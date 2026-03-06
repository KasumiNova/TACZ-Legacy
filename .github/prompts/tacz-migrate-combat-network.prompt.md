---
name: "TACZ Migrate Combat Network"
description: "Migrate TACZ combat, shooter state, item protocol, and network synchronization systems into TACZ-Legacy with parity-first validation."
agent: "TACZ Migration"
argument-hint: "填写要迁移的战斗/实体/网络功能、上游文件或验收约束"
---
迁移 `TACZ` 的**战斗 / 实体 / 物品协议 / 网络同步系统**到 `TACZ-Legacy`。

当前阶段说明：

- 这条线的**服务端主链与协议骨架已落地**，当前 `TACZ-Legacy` 中已有 `IGunOperator.kt`、`ShootResult.kt`、`ReloadState.kt`、`KnockBackModifier.kt`、`FireMode.kt`、`ShooterTickHandler.kt`、`common/entity/shooter/**`、`TACZNetworkHandler.kt`、`common/network/message/**`、`EntityKineticBullet.kt` 与 `LivingEntityMixin.java`。
- 现在使用这个 Prompt 时，优先做**剩余 parity 缺口、客户端消费链路和下游对接**，而不是重写已经存在的 shooter 状态机 / network channel 主干。

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

- 补齐 `LegacyItems.ModernKineticGunItem.hasInventoryAmmo` 的背包弹药 / ammo box 搜索 parity
- 把现有 S2C 事件消息继续对接到客户端表现层（音效、动画、HUD、特效），而不是只在网络层到达
- 在依赖的数据 / 渲染路径准备好后，对接 HeatSystem、crawl、tracer 等尚未落地的剩余行为

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
