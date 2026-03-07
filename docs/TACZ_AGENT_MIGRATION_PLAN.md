# TACZ 大系统与 Agent 迁移规划

## 结论先行

当前阶段我**不建议**为每个子系统再复制一份几乎相同的迁移 Agent。更稳妥的组织方式是：

1. **协调 Agent（主聊天 / 默认 Agent）**
   - 负责拆任务、排依赖、安排并行度、处理跨系统冲突、做最后集成验收。
2. **侦察 Agent（当前推荐 `Ask`）**
   - 只读侦察：找上游真值源、梳理依赖边界、确认 `TACZ-Legacy` 落点与风险。
3. **`TACZ Migration` Agent**
   - 真正执行迁移：严格保证逻辑不变、补测试、做运行链路验证、一次性收口。

也就是说：**Agent 角色保持少而稳定，系统差异交给 Prompt 来表达**。

这样做有三个好处：

- **迁移纪律集中**：严格 parity / 单测 / runtime validation 的硬约束只维护一份，不会在多个 Agent 文件里漂移。
- **系统拆分灵活**：真正变化的是“迁移哪条系统”，不是“迁移纪律本身”。
- **后续维护成本低**：将来你只需要调整 Prompt 范围，不用同步修改一堆近似 Agent 文件。

## 统计口径

- 上游基线：`TACZ/src/main/java/com/tacz/guns/**`
- 仅统计主源码 `.java`
- 统计值：**源码文件数 + 物理源码行数**
- 以下分组是为了指导迁移分工，不是上游原始包结构的唯一真理；个别跨系统文件由协调 Agent 归口裁定

## 通用迁移参考与协作约束

- **行为真值源**始终以上游 `TACZ` 为准；但在具体落地到 `1.12.2 Forge` 时，可以参考工作区中的现成 `1.12.2` 项目（例如 `PrototypeMachinery`）来借鉴 API 用法、分层方式、Gradle/Forge 接线与 Java/Kotlin 混合实现范式。
- 当前工作区内的 **Minecraft 1.12.2 原版源码** 可优先从 `TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**` 阅读。
- 当前工作区内与上游 `TACZ` 对应的 **Minecraft 1.20.1 映射源码** 可优先从 `TACZ/build/tmp/expandedArchives/forge-1.20.1-47.3.19_mapped_parchment_2023.08.20-1.20.1-sources.jar_*/net/minecraft/**` 阅读；若哈希后缀变化，可按 `TACZ/build/tmp/expandedArchives/*sources.jar_*/net/minecraft/**` 搜索。
- 在 **Multi-Agent** 环境下，优先按“子系统 / 文件集合”拆分所有权，避免多个 agent 同时编辑同一批文件导致补丁冲突。
- 若 Gradle 构建或测试失败且明显不是当前任务引入的问题，应先记录并跳过，不要为了“自证清白”去清理共享的 Gradle 缓存、wrapper 或全局构建数据。
- 迁移实现可自由选择 **Java** 或 **Kotlin**，但前提是贴近周边模块、减少胶水代码、不会显著增加未来维护复杂度。

## TACZ 大系统分组

| 迁移轨道 | 上游范围（主） | 文件数 | 物理行数 | 是否建议独立推进 | 推荐执行方式 |
|---|---|---:|---:|---|---|
| 基础启动与注册 | `GunMod.java`、`config/**`、`init/**`、`event/**`、`block/**`、`command/**`、`particles/**`、`sound/**` | 68 | 3,319 | 是，最先做 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 数据/枪包兼容 | `resource/**`、`api/resource/**`、`api/modifier/**` | 90 | 7,298 | 是，核心前置 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 战斗/实体/网络 | `entity/**`、`network/**`、`item/**`、`inventory/**`、`crafting/**`、`api/item/**` | 102 | 10,039 | 是，核心玩法闭环 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 客户端交互/UI | `client/gameplay/**`、`client/input/**`、`client/gui/**`、`client/tooltip/**`、`client/event/**` | 64 | 6,896 | 是，可与战斗线并行 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 渲染/动画/客户端资源 | `client/resource/**`、`client/model/**`、`client/renderer/**`、`client/animation/**`、`api/client/**`、`mixin/client/**` | 191 | 19,995 | **强烈建议独立** | `Ask` 侦察 + `TACZ Migration` 执行 |
| 第三方兼容 | `compat/**` | 55 | 3,857 | 是，但排后 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 交叉支撑层 | `util/**`、`mixin/common/**`、`api/event/**`、`api/entity/**` 等 | 59 | 4,619 | 不建议独立成一整条线 | 跟随所属主系统迁移 |

## 为什么这样拆更合适

### 1. 基础启动与注册

这条线负责把 `TACZ-Legacy` 的“壳”先撑起来，包括：

- 模组入口与生命周期接线
- 配置加载
- 注册表与内容初始化
- 方块/方块实体/容器等底座
- 启动期事件与通用命令/音效/粒子注册

没有这条线，后面的资源、玩法、渲染都容易变成“代码有了但链路没接上”。

### 2. 数据/枪包兼容

这是迁移里的“输入面”：

- 枪包发现/加载
- JSON/POJO/索引/序列化
- modifier 与兼容读取
- pack 转换与版本检查

这条线必须尽早稳定，因为玩法、渲染、UI 最后都要消费它产出的资源索引和运行时快照。

### 3. 战斗/实体/网络

这是核心玩法闭环：

- 开火/换弹/拉栓/切枪/近战
- 服务端裁决与实体同步
- 子弹、命中、状态、时间戳与网络容错
- 枪械/附件/工作台/物品协议

这条线最适合用“严格迁移 Agent”推进，因为行为性最强、最容易引入回归。

### 4. 客户端交互/UI

这条线不是“纯表现层”，它直接连接玩家输入与本地状态机：

- 按键绑定
- 本地玩家行为入口
- HUD / Overlay / Screen / Tooltip
- 客户端事件桥接

建议与战斗/网络线保持高频对齐，避免出现“UI 显示可用，实际服务端逻辑没通”的假完成。

### 5. 渲染/动画/客户端资源

这是当前体量最大、重写风险最高的一条线，也是最不适合 1:1 机械搬运的区域：

- 动画 API 与插值/姿态采样
- 模型、渲染器、第一/第三人称表现
- 客户端资产索引、显示定义、音频资产
- 客户端 Mixin 与渲染时序接线

**这条线应该被视作独立工程流，而不是附着在 UI 或玩法上的“顺手迁一下”。**

### 6. 第三方兼容

如 `KubeJS`、`JEI`、`Cloth`、控制器、光影/动画兼容等，适合排在核心链路稳定之后单独做：

- 依赖可选
- 风险分散
- 对主玩法并非强前置

## 推荐迁移顺序

1. **基础启动与注册**
2. **数据/枪包兼容**
3. **战斗/实体/网络**
4. **客户端交互/UI**（可与 3 部分并行，但要同步验收）
5. **渲染/动画/客户端资源**
6. **第三方兼容**

## 当前阶段进展（基于当前 `TACZ-Legacy` 工作区状态）

- **Foundation**：已完成第一波落地，基础启动、注册、烟测脚本与基础测试已进仓。
- **数据/枪包兼容**：核心扫描 / 解析 / 索引 / modifier / 兼容读取主链已落地，并已开始被真实消费到 item、tooltip、workbench 摘要、recipe filter、attachment tag 等路径；当前优先级转为**回归修复、缺口补齐与新增消费点接入**。
- **战斗/实体/网络**：服务端 shooter 状态机、网络通道与主消息骨架已落地，并已补齐 ammo 搜索 parity、缺失的 S2C 消息类型与基础客户端事件投递链路；当前优先级转为**回归修复、剩余 parity 收尾与表现层继续消费**。
- **音频系统 / smoke 守门**：专用音频 runtime 的阶段 A/B 已落地：`GunSoundPlayManager -> TACZAudioRuntime` 统一 facade 已建立，reload 阶段已有 manifest/probe/preflight，`diagnostic/null` backend 可用于 smoke，`GunPackSoundResourcePack` 被降级为 `legacy-minecraft` fallback，focused smoke 默认不再依赖原版资源刷新路径。最新两轮 focused smoke 已把根因进一步钉死：`diagnostic` 模式证明 animation / `ServerMessageSound` 请求都能进入 runtime；而默认 `legacy-minecraft` 路径（`build/smoke-tests/runclient-focused-smoke-20260308-032140.log`）证明客户端默认 backend 仍是旧 `SoundHandler` 链，`tacz:hk_mp5a5/hk_mp5a5_shoot` 可提交到 backend，但 `minecraft:rpg7_reload_lower` 这类缺失动画音效会在 legacy 播放链触发 `CodecJOrbis` / `Unable to acquire inputstream` 并拖垮 `runClient`。针对这一点，`TACZAudioRuntime` 现已新增 legacy fallback 的提交前防护：`MISSING/UNTRACKED` 只有在 1.12 资源管理器能真实解析 `sounds/<path>.ogg` 时才允许继续提交，`INVALID_* / IO_ERROR` 会直接 `DROPPED`，从而避免坏引用再次把旧声音栈拖死。当前剩余重点为：dedicated playback backend、decode/normalize/cache 与不兼容资源处置策略。补充说明：本轮重新执行定向 Gradle 测试与 legacy smoke 时，又分别被工作区内无关的 `compileJava` 错误（`MuzzleFlashRender.java` / `BedrockGunModel.java`）以及 Forge 1.12 对多版本 Kotlin jar 的 ASM 扫描问题挡住，因此新的防卡死逻辑已完成静态校验和代码收口，但未能在当前环境噪声下再次跑到 in-world marker。
- **客户端交互 / UI**：本阶段迭代已完成，已把 runtime 下游消费层推进到真实可用程度，并补上 `gun_smith_table` 的基础 `GUI / container / craft` 闭环。当前已落地内容包括：
  - runtime 翻译 / display / recipe filter / workbench 摘要 / attachment tag 的真实客户端消费入口；
  - `GunEvents`、输入桥、overlay、tooltip bridge、`TACZGunPackPresentation` 等客户端桥接层；
  - `GunSmithTableScreen`、`LegacyGuiHandler`、`GunSmithTableContainer`、`ClientMessageGunSmithCraft`、`LegacyGunSmithingRuntime` 组成的 `1.12.2` 工匠台基础 GUI-container-craft 流；
   - `IAttachment`、`AttachmentType`、扩展后的 `IGun` 与相关物品接线，足以支撑 `gun result` 预装附件与 by-hand filter 这轮行为；
   - 与 `GunRefitScreen` 真值直接相关的一轮 backend/accessor 补齐也已落地：`IGun` / `IAttachment` 已补 builtin attachment、aim zoom、zoom number、laser color 访问语义，`LegacyItems.AttachmentItem` 与 `LegacyItems.ModernKineticGunItem` 已按上游 NBT / runtime snapshot 读写这些值，`TACZGunPackPresentation` 已新增 builtin attachment / iron zoom / attachment zoom / laser config 解析 helper，并已有 `RefitAttachmentAccessorParityTest` 做定向回归。
- **渲染 / 动画 / 客户端资源**：本阶段已完成 bedrock model/render 基础设施的大块迁移，已落地内容包括：
   - `client/resource/pojo/model/**`、`client/resource/pojo/display/gun/**`、`client/resource/serialize/Vector3fSerializer.java` 等资源 POJO / 序列化层；
   - `client/model/bedrock/**` 下的 Bedrock geometry 渲染结构；
   - `client/resource/TACZClientAssetManager.kt` 与 `client/renderer/item/TACZGunItemRenderer.kt`，以及 `ClientProxy.kt` 中的 item 渲染接线；
   - `BedrockModelParsingTest`、`GunDisplayParsingTest` 等解析回归测试；
   - 一轮成功的 `runClient` smoke，已确认客户端能够从 gun pack 成功加载 `110` 个 display、`166` 个 model、`166` 个 texture。
   - 第一人称动画子轨本轮已补齐关键 parity 缺口：`GunDisplayInstance.checkAnimation()` / `TACZClientAssetManager.kt` 现已对齐上游 `default_animation` 与 `use_default_animation(rifle/pistol)` 的 controller prototype 回退语义；`FirstPersonRenderGunEvent.kt` 新增了等价于上游 `TickAnimationEvent(RenderTickEvent)` 的 `visualUpdate()` / `updateSoundOnly()` 驱动；`LegacyClientPlayerGunBridge.kt` + `LegacyClientGunAnimationDriver.kt` 现已把近战输入真正路由到 `INPUT_BAYONET_MUZZLE / STOCK / PUSH`，并补上 `put_away` 的 exiting 生命周期；
   - 这也解释了为什么此前只有 `special_melee_task_manager` 一类少数资源看起来还能动，而标准枪几乎静止：前者自带完整动画原型，后者大量依赖 `rifle_default / pistol_default` 回退；Legacy 之前没有把这些默认 prototype 装入 controller，状态机即使收到了 trigger，也只能驱动一个缺胳膊少腿的动画集合；
   - 最新 focused smoke（`build/smoke-tests/runclient-focused-smoke-20260308-020616.log`）已打到 `ANIMATION_OBSERVED` 与 `PASS`，并留下截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-020616/01-animation_observed.png`；日志中可见标准枪 `tacz:hk_mp5a5` 运行时 `defaultType=rifle`、`smInitialized=true`，证明默认回退与第一人称动画链已经真正走到运行时，而不只是单测层面的静态修复。
   - **TextShow / 模型文字显示**：已完成上游 `TextShow` + `PapiManager` + `TextShowRender` 的完整移植。具体落地文件：`Align.java`（枚举）、`TextShow.java`（POJO）、`PapiManager.java`（Placeholder API 管理器，内含 `%ammo_count%` / `%player_name%` 两组占位符实现）、`TextShowRender.java`（GL immediate mode 下的延迟文字渲染器，利用 `delegateRender` 在主渲染完成后绘制文字）、`ColorHex.java`（颜色解析工具）。`BedrockModel` 基类新增 `delegateRenderers` 列表，`BedrockGunModel.setTextShowList()` 负责按 bone 名称注册渲染器，`GunDisplayInstance.checkTextShow()` 在 display 创建时解析颜色并接线。已有 `ColorHexTest`（3 项）+ `TextShowDeserializationTest`（5 项）回归覆盖，全量 191 tests 通过，focused smoke PASS（commit `5aecc61`）。
   - **第一人称程序化动画与 fire feedback**（2026-03-08 本轮新增）：已完成上游 `FirstPersonRenderGunEvent` 全套程序化动画系统的移植，包括：
     - `SecondOrderDynamics.java`：二阶动力学平滑器（渲染线程版，上游为后台线程），用于 ADS 瞄准过渡与跳跃摆动平滑；
     - `PerlinNoise.java`：平滑随机噪声，用于射击水平偏移和偏航旋转；
     - `Easing.java`：`easeOutCubic` 缓动函数；
     - `MathUtil.java` 新增 `getEulerAngles(Matrix4f)` + `applyMatrixLerp(Matrix4f, Matrix4f, Matrix4f, float)` 用于 idle/aiming positioning 矩阵混合（对齐上游 lerp 语义）；
     - `FirstPersonRenderGunEvent.kt` 全面重写：新增 `applyShootSwayAndRotation`（射击后坐力/偏移）、`applyJumpingSway`（跳跃/着陆摆动）、`applyAnimationConstraintTransform`（约束骨骼逆变换，使瞄准时枪械稳定）、view bob 补偿（使用 1.12 `renderArmPitch/renderArmYaw` 替代上游 `xBob/yBob`）、`applyFirstPersonPositioningTransform` 改用 `MathUtil.applyMatrixLerp` 矩阵混合；
     - `MuzzleFlashRender.java`：枪口火焰渲染器（`IFunctionalRenderer` 实现），50ms 显示窗口、随机旋转、缩放动画、半透明 + 辉光双层渲染；`BedrockGunModel` 新增 `muzzle_flash` 节点路径解析与 `MuzzleFlashRender` 注册；
     - `LegacyClientShootCoordinator.kt` 新增 `onShoot()` 调用链，驱动后坐力时间戳 + 枪口火焰时间戳；
     - 验证：192 tests 全通过、focused smoke PASS、截图确认第一人称渲染链路完整到运行时。
- **验证状态**：编译与定向测试已覆盖 Client UX / gunsmith / refit backend / render parsing 等阶段性变更；渲染基础设施已有一轮成功的 `runClient` smoke。后续若某些 client UX / refit 手工路径再次被 Forge 1.12 对多版本 Kotlin jar 的 ASM 扫描问题挡在模组初始化前，仍应按“环境阻塞而非本轮回归”记录，不要把阶段性验证结论混成一团。

因此，下一阶段最值得投入的主线通常不是“继续重迁数据/战斗/Client UX/Render 基础设施主链”，而是：
    1. **Render 剩余子轨**：~~animation state machine~~（已落地）、~~关键帧插值~~（已落地）、bone animation application、ammo/attachment display renderer、~~muzzle flash~~（本轮已落地） / shell ejection、第一人称 hand/scope 渲染链路、~~程序化后坐力 / 射击摆动 / 跳跃摆动~~（本轮已落地）、~~ADS 二阶动力学平滑~~（本轮已落地）、~~约束骨骼逆变换~~（本轮已落地）
      2. **Combat 剩余 parity 子轨**：普通枪稳定出弹、伤害真值、爆炸物命中/延时爆炸语义与服务端裁决一致性
      3. **剩余 blocked 的 Client UX / Refit 能力**：例如 `GunRefitScreen` 本体、安装/卸下/laser 提交消息、screen refresh 回包、附件属性刷新与副作用链
      4. **第三方兼容与剩余玩法收尾**：在核心主链已成形的前提下，继续补 JEI / KubeJS / 高级玩法与表现边角

## 最新对比测试回归分诊（2026-03-08）

本轮对比截图与实机反馈说明：**当前剩余问题已经不适合让一个 Agent 全包。** 建议继续复用同一个 `TACZ Migration` Agent，但按下面的 Prompt/轨道拆分迭代。

| 用户可见症状 | 推荐 Prompt | 使用 Agent | 归属说明 |
|---|---|---|---|
| 枪模整体偏小、基础持枪/瞄准位置不对、ADS 卡顿像 tick 驱动、开火时枪械抽搐、枪焰缺失、镜头抖动/后坐力没做、非 idle 动画大量缺失 | `.github/prompts/tacz-stage-render-animation-first-person.prompt.md` | `TACZ Migration` | 这是第一人称 pose / animation runtime / render-frame 插值 / fire feedback 的直接职责，优先交给渲染动画 Agent |
| 某些枪模型本应显示的数字/字模/能量读数缺失，或 gun-specific runtime/material 节点没被消费 | `.github/prompts/tacz-stage-render-material-parity.prompt.md` | `TACZ Migration` | 这是 gun-specific model runtime / material / model text layer parity，不应塞给 GUI 或 combat |
| 武器完全没音效，需要回答“没对接”还是“实现有问题” | `.github/prompts/tacz-stage-audio-engine-compat.prompt.md` | `TACZ Migration` | 音频 Agent 应负责 runtime/backend/真实播放验证；不能再用 diagnostic smoke 代替可听结果 |
| 沉浸式改装 GUI 与上游完全不是一个东西，枪模没有从手持状态平滑过渡到屏幕内预览 | `.github/prompts/tacz-stage-client-ux-gui-i18n.prompt.md` | `TACZ Migration` | 这属于 `GunRefitScreen` / screen composition / preview transition / 交互体验 parity，主责不在 render fire feedback |
| 爆发模式没有冷却、打成错误射速 | `.github/prompts/tacz-migrate-combat-network.prompt.md` | `TACZ Migration` | 这是 fire-mode / cadence / server accept gate 真值问题，主责在 combat/network |
| 若以上任一 Agent 被共享 hook / smoke / 注册问题挡住 | `.github/prompts/tacz-stage-foundation-client-hooks.prompt.md` | `TACZ Migration` | Foundation 只负责共享接线与验证守门，不接管 feature 本体 |

### 本轮建议迭代顺序

1. **Render Animation / First-Person Agent**
   - 先处理“看起来就不对”的第一人称核心问题：尺寸/位置、ADS 插值、非 idle 动画、fire 抖动、枪焰、镜头抖动、后坐力。
2. **Audio Agent**
   - 明确回答“无音效”到底是没接上还是 backend 没真正播放，并给出真实客户端证据。
3. **Render Material Agent**
   - 继续补 gun-specific runtime/material parity，特别是模型字模 / 数字显示 / 剩余 attachment-display 细节。
4. **Client UX Agent**
   - 收 `GunRefitScreen` 的沉浸式视觉与交互一致性，重点是 world-to-screen 枪模过渡与 screen 组织，而不是只修 backend。
5. **Combat Agent**
   - 收 burst cadence、剩余 fire-mode / projectile / damage / explosion 真值问题。
6. **Foundation Agent（按需介入）**
   - 只在上面几条被共享 hook / smoke / 注册阻塞时介入。

## Agent 交付与交接机制

- 从本阶段开始，所有**可写 Agent** 完成任务时都必须：
   1. 更新至少一份 Git 受控文档
   2. 在 `.agent-workspace/stage-reports/` 写入本地阶段报告
- 本地阶段报告目录**不提交 Git**，只作为主 Agent 的快速确认与 handoff 缓冲区。
- 报告文件命名、模板与使用规则见 `docs/TACZ_AGENT_WORKFLOW.md`
- 主 Agent 收口时，优先以：
   - `docs/TACZ_AGENT_MIGRATION_PLAN.md` 作为长期正式状态
   - `.agent-workspace/stage-reports/*.md` 作为短周期阶段结果来源
- 这样后续各条线（audio / render / combat / foundation / client ux）完成阶段任务后，不需要再依赖用户截图转述结果。

补充规则：

- `util/**`、`api/event/**`、`api/entity/**`、`mixin/common/**` 这类交叉支撑代码，**不要单独立项**；直接跟随其主要调用链归入某条主轨道。
- 若某个需求同时跨越“玩法 + 渲染”，默认先迁**玩法真值链路**，再迁表现层；不要先做 UI/渲染壳子。

## 建议的 Prompt / Slash Command 组合

| 目标 | Prompt 文件 | 使用 Agent | 用途 |
|---|---|---|---|
| 迁移前侦察 | `.github/prompts/tacz-scan-system.prompt.md` | `Ask` | 先摸清上游真值源、边界、依赖、Legacy 落点 |
| 基础启动与注册 | `.github/prompts/tacz-migrate-foundation.prompt.md` | `TACZ Migration` | 迁移入口、配置、注册、底座 |
| 数据/枪包兼容 | `.github/prompts/tacz-migrate-data-pack.prompt.md` | `TACZ Migration` | 在已落地 runtime/parser 基础上继续做资源消费、兼容补齐与玩法接线 |
| 战斗/实体/网络 | `.github/prompts/tacz-migrate-combat-network.prompt.md` | `TACZ Migration` | 在已落地 shooter/network 主链基础上继续做 parity 补齐、客户端消费与表现接线 |
| 客户端交互/UI | `.github/prompts/tacz-stage-client-ux-gui-i18n.prompt.md` | `TACZ Migration` | 在已落地输入/HUD/tooltip/`gun_smith_table` 与 refit accessor/backend 真值基础上继续做回归修复、剩余 parity 与沉浸式 GUI 收尾 |
| 渲染/动画/第一人称 | `.github/prompts/tacz-stage-render-animation-first-person.prompt.md` | `TACZ Migration` | 在已落地 bedrock model/display/asset manager/gun item TEISR 基础上继续补第一人称动画、ADS、镜头/后坐力与 fire feedback |
| 第三方兼容 | `.github/prompts/tacz-migrate-compat.prompt.md` | `TACZ Migration` | 迁移 JEI/KubeJS/Cloth/动画器/光影等兼容 |

## 本阶段细化 Prompt（直接投喂各 Agent）

在当前阶段，用户已经反馈的主要问题不再是“主链是否存在”，而是**现有基座上的用户可见缺口**。因此建议继续保留上面的通用 Prompt，同时额外使用以下**阶段细分 Prompt** 来分派任务：

| 症状 / 目标 | Prompt 文件 | 使用 Agent | 适用说明 |
|---|---|---|---|
| 枪械贴图不对、`ammo/attachment` 物品像图集、GUI/掉落物错误画 3D 模型、特殊方块模型缺失或回退 | `.github/prompts/tacz-stage-render-material-parity.prompt.md` | `TACZ Migration` | 给渲染 Agent 使用，重点修 asset manager / renderer / item-block display 消费链，并在必要时补 1.12.2 的物品渲染上下文桥接，不重造第二套资源缓存 |
| 动画不播放、手模不渲染、第一人称/scope 表现缺失、ADS/开火反馈错误 | `.github/prompts/tacz-stage-render-animation-first-person.prompt.md` | `TACZ Migration` | 给渲染 Agent 使用，重点修动画 runtime、第一人称 hook / client mixin / hand-scope 渲染链、ADS 插值、fire feedback 与 faithful 的 hand renderer 语义 |
| 武器完全没音效、枪包音频格式不兼容、`refreshResources()` 触发卡顿/卡死、focused smoke 被音频链路阻塞、需要专用 backend / preflight / 静默后端 | `.github/prompts/tacz-stage-audio-engine-compat.prompt.md` | `TACZ Migration` | 给音频 Agent 使用，重点把 TACZ gun-pack 音频从 1.12 原版声音栈中解耦，统一动画音效与网络音效入口，并明确回答“没对接还是实现有 bug” |
| GUI 样式半成品、沉浸式改装界面与上游差距大、枪模过渡突兀、按钮贴图仍是原版、中文环境大量英文、创造模式分类不够细 | `.github/prompts/tacz-stage-client-ux-gui-i18n.prompt.md` | `TACZ Migration` | 给 Client UX Agent 使用，重点做 `GunRefitScreen` / `GunSmithTableScreen` 视觉复刻、world-to-screen 枪模过渡、I18n 语言源修正、tooltip/HUD 文案清理，以及创造模式分类按上游真值细分 |
| 爆发模式射速不对、普通枪伤害/子弹真值异常、RPG/榴弹爆炸行为不对 | `.github/prompts/tacz-migrate-combat-network.prompt.md` | `TACZ Migration` | 给 combat Agent 使用，重点修 fire mode cadence、服务端裁决、projectile / damage / explosion 真值，不把手感问题误甩给渲染 |
| client mixin 未激活、renderer / GUI 注册缺失、smoke 被共享基础问题阻断 | `.github/prompts/tacz-stage-foundation-client-hooks.prompt.md` | `TACZ Migration` | 给基建 Agent 使用，只做共享 hook / 注册 / smoke 守门修复，不越界接管业务 feature |

### 本阶段推荐执行顺序

1. **渲染 Agent — 材质/物品/方块表现收口**
   - 先解决“看得见但不对”的贴图 / item / block display 问题。
2. **渲染 Agent — 动画/第一人称/声音收口**
   - 再解决动画 runtime、hand/scope 链路、ADS 插值、枪焰/镜头/后坐力等第一人称反馈；声音播放 backend 本体交给音频 Agent。
3. **音频 Agent — 专用音频引擎 / smoke 兼容守门**
   - 当症状已经上升为“武器完全没音效 / 音频资源格式不兼容 / 原版声音栈卡住 focused smoke / 需要 null backend / preflight / 专用 runtime”时，单独使用音频 Prompt，不再把问题继续塞给渲染 Agent。
4. **Client UX Agent — GUI 样式与 I18n 收尾**
   - 在真实 backend 已接通的前提下补视觉与语言一致性，重点收 `GunRefitScreen` 的沉浸式预览与 world-to-screen 枪模过渡。
5. **Combat Agent — 射击 cadence / 伤害 / 爆炸真值收口**
   - 重点处理 burst 射速错误、普通枪伤害不对与 RPG/榴弹的剩余真值偏差。
6. **基建 Agent — 共享接线与 smoke 守门**
   - 只在前面几条被共享基础问题挡住时介入。

### 本阶段并行协作建议

- **渲染 Agent**：优先持有 `client/resource/**`、`client/model/**`、`client/renderer/**`、必要的 `api/client/**` 与 `mixins.tacz.json` 改动所有权。
- **音频 Agent**：优先持有 `client/sound/**`、新增的 `client/audio/**`、`TACZClientAssetManager.kt` 中的音频 manifest / probe / backend 接线，以及 focused smoke 的音频参数透传与诊断输出。
- **Client UX Agent**：优先持有 `client/gui/**`、`client/event/**`、`common/item/LegacyRuntimeTooltipSupport.kt`、`common/resource/TACZGunPackPresentation.kt`、`assets/tacz/lang/**`。
- **基建 Agent**：优先持有 `ClientProxy.kt`、`CommonProxy.kt`、`mixins.tacz.json`、smoke / diagnostic 脚本与注册文件。
- 若多个 Agent 同时需要修改 `ClientProxy.kt`、`mixins.tacz.json`、`TACZClientAssetManager.kt`，应先由协调 Agent 明确文件所有权，避免代码打架；其中 `TACZClientAssetManager.kt` 的音频 manifest / probe / sound resource 接线优先交给音频 Agent。

## 实际使用建议

### 标准工作流

1. 先运行“系统侦察” Prompt，确认：
   - 上游文件边界
   - Legacy 落点
   - 风险点
   - 建议先后顺序
2. 再运行对应的迁移 Prompt，让 `TACZ Migration` 执行完整迁移。
3. 若需求跨两个系统，**优先拆成两个 Prompt**，由协调 Agent 控制先后顺序。
4. 若多人或多 agent 并行迁移，先划清文件边界，再执行构建与验证；不要让多个 agent 争抢同一文件或共用“全量清理”操作。

### 什么时候才值得再拆更多 Agent 文件

只有在以下情况同时成立时，才值得再单独新增 custom agent：

- 某条系统长期高频使用；
- 它需要一套明显不同的工具限制或输出格式；
- 这些差异无法仅靠 Prompt 表达。

在当前阶段，真正最可能值得以后单独拆 Agent 的只有两条：

- **渲染/动画/客户端资源**
- **数据/枪包兼容**

因为它们既体量大，又有独特的验证方式。
