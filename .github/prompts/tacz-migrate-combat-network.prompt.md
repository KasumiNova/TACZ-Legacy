---
name: "TACZ Migrate Combat Network"
description: "Migrate TACZ combat, shooter state, item protocol, and network synchronization systems into TACZ-Legacy with parity-first validation."
agent: "TACZ Migration"
argument-hint: "填写要迁移的战斗/实体/网络功能、上游文件或验收约束"
---
迁移 `TACZ` 的**战斗 / 实体 / 物品协议 / 网络同步系统**到 `TACZ-Legacy`。

当前阶段说明：

- 这条线已经补过一轮 `baseTimestamp` 同步协议，但**当前仍然只能算半成品**：用户反馈依然存在“有时能出子弹、有时吞子弹、弹药照常消耗、没有报错、伤害还不对”的严重回归。
- 新一轮实机反馈还新增了一条明确症状：**爆发模式开火没有冷却，直接打成了加特林射速。** 这条线默认归 combat/network Prompt 负责，因为它是 fire-mode 真值与 shoot cadence 门禁问题，不是渲染错觉。
- 因此本 Prompt 现在默认应视为**根因级回归修复 / parity 真值补齐**，而不是“主链已经完成，只修一点皮毛”。
- `Client UX` / Render 线负责表现消费，但**projectile 真值、伤害结算、爆炸行为、服务端裁决**仍然是本 Prompt 的直接职责，不要把这些问题甩给渲染 Agent。

默认关注范围：

- `entity/**`
- `network/**`
- `item/**`
- `inventory/**`
- `crafting/**`
- `api/item/**`
- 与这些链路直接相关的 Legacy 服务端/客户端接线

执行要求：

- 保持上游的开火、换弹、拉栓、切枪、近战、冲刺/瞄准/开火门禁、时间戳校验、服务端裁决与同步语义
- 不允许只迁客户端表现，不接服务端真值链路
- 对行为密集逻辑补测试；必要时先在上游 `TACZ` 写临时特征测试锁定行为
- 至少完成一轮有说服力的运行链路验证：证明消息、状态同步、射击/换弹等主链路真实可达
- 若现有 `IGunOperator`、`ShooterDataHolder`、`TACZNetworkHandler` 或 `common/network/message/**` 已覆盖主干，请优先补齐剩余行为与客户端消费，不要并行引入第二套 operator/state holder/channel

当前阶段优先任务：

- 仅在发现明确 parity 回归、序列化缺口、服务端门禁问题或消息协议遗漏时继续修改 combat/network 主链
- 客户端表现、HUD、音效、动画、overlay 对现有 `ServerMessage*` / `GunEvents` 的消费应优先交给 `client ux` 或 `render animation` 线处理
- 但**弹道真值本身**（速度、下坠、发射条件、弹药消耗、能否真实生成子弹）仍属于本 Prompt 的直接职责；不要把这些问题甩给渲染 Agent
- tracer 的视觉表现可以由 render 侧消费，但前提是 combat 侧先正确产出 projectile / ammo display / tracer 相关真值；如果目前根本没产出，那优先修 combat
- HeatSystem、crawl 等更大块能力仍可视为后续需求；但子弹速度、重力、发射成功率、inventory ammo 消耗不是“以后再说”的小问题

## 本轮必须优先盯死的断点

1. **间歇性吞子弹仍然没修干净**。
	- `LegacyClientPlayerGunBridge.attemptShoot()` 当前仍是**客户端本地先 `operator.shoot(...)`，成功后再发 `ClientMessagePlayerShoot`**。
	- 服务端再次执行 `LivingEntityShoot.shoot()`，这意味着任何时间戳、draw 状态、线程调度、当前枪状态不一致，都可能造成“客户端吃弹成功、服务端无实体”的假成功。
	- 本轮必须把这件事查到根因：是 `baseTimestamp` 仍会漂、是重同步窗口仍会吞首包、是 `currentGunItem/draw` 未就绪、还是 `spawnEntity` 根本没到正确侧/正确线程。
	- **不允许继续接受“偶尔成功、偶尔吞掉、没有报错”的状态。**
2. **伤害结算明显不对，Legacy 当前 `EntityKineticBullet` 仍远弱于上游。**
	- 现在的 Legacy 子弹大多还是：固定 `damage` + `DamageSource.causeThrownDamage(...)` + 简单 knockback。
	- 上游 `EntityKineticBullet` 真实行为包括：
	  - `getDamage(startPos -> hitVec)` 的距离伤害曲线
	  - armor ignore / dual damage sources
	  - headshot multiplier
	  - hurt/kill event 与客户端同步消息
	  - 更完整的 knockback / ignite / entity-type 特判
	- **任务**：至少把会直接影响“伤害明显不对”的核心路径补齐到上游语义，不要只修 projectile 出生。
3. **榴弹 / RPG / 爆炸物现在根本不算完成。**
	- 真实枪包数据（例如 `rpg7_data.json`、`m320_data.json`）已经声明了 `bullet.explosion`。
	- Legacy 当前要重点核对两件事：
	  - `GunDataAccessor.kt` / `BulletCombatData` 是否真的把 `bullet.explosion` 带到了 runtime
	  - `EntityKineticBullet` 是否对齐了上游爆炸语义：**命中实体/方块立即爆，delay 只负责飞行途中延时自爆**，而不是“delay > 0 时命中直接死掉且不爆”
	- Legacy 当前若仍只是 `world.createExplosion(...)`，也要继续核对上游 `ExplodeUtil` / `ProjectileExplosion` 的必要行为（damage / knockback / destroyBlock 语义），不要满足于“总算炸了一下”。
4. **运行验证必须覆盖普通枪与爆炸枪两类。**
	- 至少验证一把普通枪：服务端稳定生成 projectile，连续开火不再随机吞弹，伤害不离谱。
	- 至少验证一把 RPG / 榴弹枪：命中实体或方块会爆，未命中时 delay/fuse 语义也合理。
5. **爆发 / 半自动 fire mode cadence 必须重新贴齐上游。**
	- 本轮必须核对 burst count、`shootInterval`、client-side trigger 节流、server-side accept gate、burst queue/reset 语义。
	- 不允许继续出现“burst mode 一按住就像 full-auto 加特林”这种明显违背上游手感的行为。

## 推荐优先核对的 Legacy 文件

- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientPlayerGunBridge.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/LivingEntityShoot.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/LegacyEntities.kt`
- `src/main/kotlin/com/tacz/legacy/common/resource/GunDataAccessor.kt`
- `src/main/kotlin/com/tacz/legacy/common/network/message/client/ClientMessagePlayerShoot.kt`
- `src/main/kotlin/com/tacz/legacy/common/network/message/event/ServerMessageSyncBaseTimestamp.kt`
- `src/main/kotlin/com/tacz/legacy/common/network/message/client/ClientMessageSyncBaseTimestamp.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/ShooterTickHandler.kt`

通用补充要求：

- 可参考工作区中的 `PrototypeMachinery` 等现成 `1.12.2` 项目作为落地示例，但行为真值仍以上游 `TACZ` 为准
- 原版源码可优先参考：`TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**`（1.12.2）与 `TACZ/build/tmp/expandedArchives/forge-1.20.1-47.3.19_mapped_parchment_2023.08.20-1.20.1-sources.jar_*/net/minecraft/**`（1.20.1；若哈希后缀变化，按 `*sources.jar_*` 搜索）
- 在 Multi-Agent 环境下按文件或子系统切分所有权，避免多个 Agent 修改同一组文件
- 若 Gradle 构建问题明显不是本次改动引起，先记录并跳过，不要清理共享 Gradle 缓存或全局构建数据
- 可自由选择 `Java` 或 `Kotlin`，前提是更便于接线、贴近周边模块且不会增加维护负担

输出必须包含：

- 上游真值源文件
- Legacy 落点文件
- 补充的测试与其结果
- 运行链路验证结果（尤其是 client → server → state sync 路径）
- 说明这次是“补齐 parity / 客户端消费 / 下游对接”还是“补主链缺口”，并明确复用了哪些现有 shooter/network 基座
- 任何因平台差异导致的必要偏差
- 必须明确回答：
	- 为什么会出现“客户端吃弹成功，但服务端没生成子弹实体”
	- 为什么伤害会明显不对
	- 为什么榴弹 / RPG 之前不爆炸
	- 为什么 burst fire 会失去冷却、打成错误射速
	- 本轮分别是如何修掉这些根因的
