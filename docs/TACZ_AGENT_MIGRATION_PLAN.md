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
- **战斗/实体/网络**：服务端 shooter 状态机、网络通道与主消息骨架已落地，并已补齐 ammo 搜索 parity、缺失的 S2C 消息类型与基础客户端事件投递链路；2026-03-08 本轮已进一步补齐 burst cadence / fire-mode 真值：`GunDataAccessor` 改为按上游读取 `burst_data`（不再把 `min_interval` 错读成 display 层 `burst`，默认值也回到上游的 `1.0s / count=3 / bpm=200 / continuous=false`），`LegacyClientPlayerGunBridge` 改回上游风格的 `auto|continuous-burst` 按住触发与 `semi|burst-semi` 按下沿触发分流，`LivingEntityShoot` 则新增服务端 `BurstFireTaskScheduler`，让一次合法 burst 扳机在服务端按 `burstShootInterval` 连续击发，而不是退化成"客户端连点/按住反复发单发包"。定向测试已覆盖 burst 解析与调度器周期语义；focused smoke 现可强制 regular gun，使用 `tacz:b93r` + `tacz:rpg7` 的实机链路已验证 `GUN_FIRE side=SERVER gun=tacz:b93r count=1/2/3`、`REGULAR_PROJECTILE_GATE_OPEN fireCount=3/3` 与最终 `PASS`。2026-03-08 后续本轮继续补齐 7 项关键 gameplay bug：(1) burst fire 客户端调度完善；(2) 缺失音效（draw/holster/reload）已补齐；(3) 弹道计算与子弹渲染修正（EntityKineticBullet 自定义 onUpdate 替代 EntityThrowable 默认路径）；(4) **数据脚本系统**——完整实现了上游 `ModernKineticGunScriptAPI` 等价的 `TACZGunScriptAPI` + `TACZDataScriptManager`，从枪包加载 Lua 数据脚本（`data/<ns>/scripts/*.lua`），script 可接管射击逻辑（shootOnce / adjustShootInterval / cacheScriptData 等全部方法），hmg22 加速射速脚本现在可正常运行；(5) 特殊弹药消耗修正（rc 无弹匣开栓 + fuel 类型兼容）；(6) 完整过热系统（heatPerShot / 冷却延迟 / 过热锁定 / 过热时间 + Mixin tick 冷却 + HUD 显示）；(7) s10dmd 等由过热系统解决。当前优先级继续转为**剩余 parity 收尾与表现层继续消费**。
- **音频系统 / smoke 守门**：专用音频 runtime 的阶段 A/B/C 仍是当前真值，但 2026-03-08 这一轮重新审计时发现工作树曾发生**部分回退**：`TACZOpenALSoundEngine` 与相关测试文件仍在树上，然而 `TACZAudioBackendMode`、`TACZAudioRuntime`、`GunSoundPlayManager` 与 `ClientProxy` 的关键接线被退回到了旧 `SoundHandler` 主链，导致 `legacy-minecraft` 再次真的走 `CodecJOrbis`。本轮已把这些接线重新对齐：`legacy-minecraft` / `legacy` / `minecraft` 属性别名重新解析到 `direct_openal`，显式旧链路退回 `vanilla-minecraft` 调试 fallback，`GunPackSoundResourcePack` 也再次只在显式 fallback 模式下保持启用。验证方面，`./gradlew --no-daemon compileKotlin compileJava` 已通过；重新执行定向测试时，**音频相关** unresolved（`DIRECT_OPENAL` / `VANILLA_MINECRAFT` / `setLegacyResourceResolverForTesting`）已全部消失，当前剩余 `compileTestKotlin` 阻塞只来自并行轨道的 `LegacyClientShootCoordinatorBurstStateTest` 与 `MathUtilFovTest`。运行时方面，本轮先用 `build/smoke-tests/runclient-focused-smoke-20260308-190244.log` 在当前坏状态下复现了旧问题：`backend=legacy_minecraft`、`CodecJOrbis`、`Unable to acquire inputstream` 与 `runClient` 退出码 `137`；随后修复后的 focused smoke `build/smoke-tests/runclient-focused-smoke-20260308-190637.log` 已再次给出当前工作树的真实证据：`OpenAL direct audio ready: loaded=1515 failed=0 totalBuffers=1515`、`Audio manifest ready: 1829 sounds (...), backend=direct_openal`、`[FocusedSmoke] AUDIO_PLAYBACK_OBSERVED location=tacz:hk_mp5a5/hk_mp5a5_shoot class=TACZOpenALSoundHandle`、多条 `backend=direct_openal result=SUBMITTED_TO_BACKEND` 请求，以及最终 `[FocusedSmoke] PASS ...`；同一份 log 关键字复核已确认不再出现 `CodecJOrbis` / `Error reading the header` / `Unable to acquire inputstream`。当前剩余重点回到：继续清理 `minecraft:*` 但资源缺失的动画音效引用，并对 direct backend 的缓存/诊断文档做后续收口。
- **客户端交互 / UI**：本阶段迭代已完成，已把 runtime 下游消费层推进到真实可用程度，并补上 `gun_smith_table` 的基础 `GUI / container / craft` 闭环。当前已落地内容包括：
  - runtime 翻译 / display / recipe filter / workbench 摘要 / attachment tag 的真实客户端消费入口；
  - `GunEvents`、输入桥、overlay、tooltip bridge、`TACZGunPackPresentation` 等客户端桥接层；
  - `GunSmithTableScreen`、`LegacyGuiHandler`、`GunSmithTableContainer`、`ClientMessageGunSmithCraft`、`LegacyGunSmithingRuntime` 组成的 `1.12.2` 工匠台基础 GUI-container-craft 流；
   - `IAttachment`、`AttachmentType`、扩展后的 `IGun` 与相关物品接线，足以支撑 `gun result` 预装附件与 by-hand filter 这轮行为；
   - 与 `GunRefitScreen` 真值直接相关的一轮 backend/accessor 补齐也已落地：`IGun` / `IAttachment` 已补 builtin attachment、aim zoom、zoom number、laser color 访问语义，`LegacyItems.AttachmentItem` 与 `LegacyItems.ModernKineticGunItem` 已按上游 NBT / runtime snapshot 读写这些值，`TACZGunPackPresentation` 已新增 builtin attachment / iron zoom / attachment zoom / laser config 解析 helper，并已有 `RefitAttachmentAccessorParityTest` 做定向回归；
   - 2026-03-08 当前工作树已按 `.agent-workspace/old-gui-reference/` 重新落地成真实可运行的 `GunRefitScreen.kt`：界面回到旧版稳定的绝对坐标布局，不再沿用上一轮未真正接线到生产路径的 `TACZRefitTransform` 方案；当前屏幕已直接接上 `LegacyGunRefitRuntime` + `ClientMessageRefitGun` / `ClientMessageRefitGunCreative` / `ClientMessageUnloadAttachment` / `ClientMessageLaserColor` / `ClientMessagePlayerFireSelect`，可用附件槽、候选附件列表、卸下、fire mode 切换、laser 颜色预览提交与属性面板都已重新进入生产路径。最新一轮还把用户点名的视觉/交互缺口一起收口：去掉了全屏 tint / 顶栏背景 / close 按钮，附件槽重新锚到右上 title 边，slot icon 改回独立 atlas 绘制，创造模式候选列表也改为按 runtime snapshot 枚举任意兼容附件，而不再只扫背包栏位；
   - refit opening / 切槽插值现已重新回到第一人称生产链：新增 `client/animation/screen/RefitTransform.kt` 对齐上游 openingProgress / transformProgress / oldType / currentType 状态机，`FirstPersonRenderGunEvent.kt` 已消费该状态并将持枪 idle/aiming/refit attachment view 三组矩阵重新做 lerp，界面开启期间也会像上游一样抑制第一人称手臂渲染；与此同时，`GunRefitScreen.kt` 现已恢复旧版“overview 打开 + 视角锁定/输入锁定 + 切槽驱动 transform 状态机”的交互语义，而不是一开屏就强制跳到第一个附件槽；
   - 本轮补齐上游 `BedrockGunModel.getRefitAttachmentViewPath(AttachmentType)` 真值：`BedrockGunModel.java` 现会缓存 `refit_view` / `refit_<type>_view` 节点路径，`TACZGuiModelPreviewRenderer.kt` 则在 `GunRefitScreen` 的 mini viewport 中直接消费这些节点逆矩阵，`selectedType` 切换后不再退回固定 identity 相机；
   - `FocusedSmokeRuntime.kt` / `FocusedSmokeClientHooks.kt` 现真实支持 `-Dtacz.focusedSmoke.refitPreview=true`：smoke 不再把 refit 分支当 stub，而是会自动打开 `GunRefitScreen`、调用 `triggerFocusedSmokeToggleProperties()` 验证属性图表按钮能在 hidden/show 间切换、调用 `triggerFocusedSmokeSelectType()` 聚焦一个可用附件槽，并进一步点选首个候选附件，打出 `REFIT_SCREEN_OPEN`、`REFIT_PROPERTIES_TOGGLE`、`REFIT_SLOT_FOCUS`、`REFIT_ATTACHMENT_CLICK`、`REFIT_ATTACHMENT_APPLIED`、`REFIT_PREVIEW_COMPLETE` marker；
   - 相关验证结论：本轮在当前并行工作树上重新执行 `./gradlew --no-daemon compileKotlin` 已通过；尝试执行 `./gradlew --no-daemon test --tests "*LegacyGunRefitRuntimeTest"` 时，`compileTestKotlin` 仍被并行轨道留下的 `LegacyClientShootCoordinatorBurstStateTest` / `MathUtilFovTest` unresolved 噪声阻塞，因此 refit runtime 新增的 `compatibleCreativeAttachments` 回归当前以主源码编译 + focused smoke 为准。运行时方面，focused smoke `runclient-focused-smoke-20260308-205235.log` 已打到 `REFIT_SCREEN_OPEN`、`REFIT_PROPERTIES_TOGGLE hidden=false`、`REFIT_PROPERTIES_TOGGLE hidden=true`、`REFIT_SLOT_FOCUS gun=tacz:hk_mp5a5 type=scope`、`REFIT_ATTACHMENT_CLICK attachment=tacz:sight_srs_02`、`REFIT_ATTACHMENT_APPLIED attachment=tacz:sight_srs_02`、`REFIT_PREVIEW_COMPLETE` 与最终 `PASS`；该次运行同时归档了 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-205235/01-refit_open.png`（打开 screen 后的沉浸式布局帧）与 `02-refit_focus.png`（scope 槽聚焦 + 创造模式候选点击后的帧），open/focus 两帧采样差异约 `84.73%`，右侧 icon 区域近白像素比为 `0.0`，说明当前截图已不再退化成“白块 icon + 同一静态帧重复抓拍”。需要继续记录的剩余小 delta 是：第一人称 refit focus 仍会对 `tacz:hk_mp5a5` 打出一次 `POSITIONING_FALLBACK`（scope refit lerp 中间矩阵非有限），但现在会保留上一帧有限矩阵而不再把客户端直接崩掉。
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
   - **2026-03-08 第一人称收口（Lua globals + 左键抑制）**：`TACZClientAssetManager.loadScriptFromSource(...)` 现已按上游把 `LuaAnimationConstant` / `LuaGunAnimationConstant` 安装到 `Globals`，而不是只落在返回表上；这直接修复了标准枪脚本对裸全局常量（如 `INPUT_DRAW`、`INPUT_RELOAD`、`INPUT_PUT_AWAY`、`PLAY_ONCE_STOP`、`PLAY_ONCE_HOLD`、`SEMI/AUTO/BURST`）的访问，`draw / inspect / shoot` 等第一人称动作不再“只有 idle / static_idle”。同时，已新增 `PreventGunLeftClickHandler.kt` + `MinecraftMixin.java` 双层抑制：前者取消 `LeftClickBlock`，后者拦截 `Minecraft.clickMouse()` / `sendClickBlockToController(...)`，从而阻断原版挥手与挖方块泄漏。最新 focused smoke `build/smoke-tests/runclient-focused-smoke-20260308-185443.log` 已同时打出 `LEFT_CLICK_SUPPRESSED target=BlockPos{x=629, y=20, z=40} ... swing=false hittingBlock=false`、`INSPECT_TRIGGERED gun=tacz:hk_mp5a5`、`REGULAR_SHOT_SENT gun=tacz:hk_mp5a5` 与最终 `PASS`；归档截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-185443/01-inspect.png`、`02-fire.png` 也确认了动作 pose 与自定义第一人称枪模仍然可见，说明“动作不播 + 原版左键泄漏”这组核心回归已被实机链路收口。
   - **2026-03-08 第一人称残余 parity 收口（`timeless50` / `fn_evolys` / reload / 快切）**：针对用户最后一轮实机反馈，本轮继续对齐上游 `FirstPersonRenderGunEvent.java`、`BedrockGunModel.java`、`KeepingItemRenderer.java`、`LocalPlayerShoot.java`、`LivingEntityReload.java` 与 `resource/pojo/data/gun/GunData.java` 的可观察行为。`BedrockGunModel` 现补缓存 `idle_view` 路径，`FirstPersonRenderGunEvent` 继续沿旧矩阵链消费 idle/aiming positioning；`GunDataAccessor` 已改为按上游读取 `reload.feed.{tactical,empty}` / `reload.cooldown.{tactical,empty}`；`LegacyClientShootCoordinator` 增加本地 shoot/silence 音效补偿；`LegacyClientPlayerGunBridge` + `ItemRendererMixin` 接回 1.12 `KeepingItemRenderer` 语义，快速切枪时会在 `put_away` 时段继续保留旧枪渲染。为避免只靠“编译成功”自证，本轮又把 focused smoke 补到了真实验收：`FocusedSmokeRuntime` 现真正消费 `tacz.focusedSmoke.regularGun`，`FocusedSmokeClientHooks` 会自动做 tactical reload 自检，`LegacyClientPlayerGunBridge.beginPutAwayAndKeep(...)` 会输出 `KEEP_ITEM_RENDER` marker。运行时验证结论为：
     - `build/smoke-tests/runclient-focused-smoke-20260308-195348.log`：`REGULAR_OVERRIDE gun=tacz:timeless50`，本地 `GENERIC sound=tacz:timeless50/timeless50_shoot`，`KEEP_ITEM_RENDER armed gun=tacz:timeless50 durationMs=280`，最终 `PASS`；截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-195348/01-first_person.png` 已人工确认不再是原版手持偏位，而是正常的第一人称枪模构图。
     - `build/smoke-tests/runclient-focused-smoke-20260308-195527.log`：`REGULAR_OVERRIDE gun=tacz:fn_evolys`，本地 `GENERIC sound=tacz:fn_evolys/evolys_shoot`，`KEEP_ITEM_RENDER armed gun=tacz:fn_evolys durationMs=670`，最终 `PASS`。
     - `build/smoke-tests/runclient-focused-smoke-20260308-200133.log`：`RELOAD_STARTED gun=tacz:timeless50 ... expectedMs=3139`、`RELOAD_COMPLETED ... actualMs=3147 deltaMs=8`、`RELOAD_TIMING_OK deltaMs=8`，同轮仍保留本地 shoot 音效与 `KEEP_ITEM_RENDER` marker，并最终 `PASS`。当前唯一未收口的是：`compileTestKotlin` 仍被并行轨道的 `LegacyClientShootCoordinatorBurstStateTest` / `MathUtilFovTest` 噪声阻塞，因此本轮 reload parse 回归仍以主源码编译 + focused smoke 为最终验收依据。
   - **2026-03-08 第一人称构图收口（尺寸过小 / 长期偏右下）**：在上面那轮 smoke 之后，用户继续指出截图里的枪仍然“像原版右下角小物件”。对 `89fc159` 旧实现、上游 `TACZ` 1.20 `FirstPersonRenderGunEvent.java` / `GunItemRendererWrapper.java` 以及 1.12 `ItemRenderer` 的重新对比表明：当前残余偏差不在 keep-item 或 reload，而在于 Legacy 还把 `applyVanillaFirstPersonTransform(...)` 这一层 1.12 主手 baseline 无条件叠加到了自定义枪模渲染前。该 baseline 会持续把模型推向右下并拉远镜头，使枪模显得过小。现已在 `FirstPersonRenderGunEvent.kt` 中移除这层常驻 baseline，让第一人称构图重新以 `idle_view` / `iron_view` positioning 节点为主，同时保留之前补上的 `EXITING_FIRST_PERSON_UPDATE` 与 positioning fail-fast/fallback 保底。验证方面，`./gradlew compileKotlin --no-daemon` 通过，focused smoke `build/smoke-tests/runclient-focused-smoke-20260308-210855.log` 打出 `ANIMATION_OBSERVED gun=tacz:timeless50`、`RELOAD_TIMING_OK deltaMs=8`、`KEEP_ITEM_RENDER armed gun=tacz:timeless50 durationMs=280`、多条 `EXITING_FIRST_PERSON_UPDATE` 与最终 `PASS`；截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-210855/01-first_person.png` 与 `02-keep_item_02.png` 也已人工复核：`timeless50` 不再停留在原版主手右下角小体积构图，而是回到屏幕中下区域的大枪模 framing，且切枪 keep window 期间旧枪仍可见。当前 render 轨道若继续迭代，应把“第一人称仍是原版手持位置”视为已收口；剩余工作更多是逐枪 polish（例如个别模型节点数据或未来的 scale-aware inverse 硬化），而不是回到整条第一人称矩阵链重写。
    - **2026-03-08 枪包 GLTF 动画 / 扩容弹匣条件渲染 / 准星系统收口**：本轮继续对齐上游 `Animations.java`、`CommonAssetsManager`、`GunDisplayInstance.java`、`BedrockGunModel.java` 与 `RenderCrosshairEvent.java` 的可观察行为。Legacy 现已补上一条最小可用的 `.gltf` 动画消费链：`TACZClientAssetManager.kt` 能加载 gun-pack `*.gltf`，`Animations.java` 会把解析结果转换为 `AnimationController`，`GunDisplayInstance.java` 也不再把 GLTF-only display 直接判死刑；这使 `TRIS-dyna` 枪包里的 `trisdyna:rc` 不再停在纯静止 pose。与此同时，`BedrockGunModel.java` 已恢复 `MAG_STANDARD` / `MAG_EXTENDED_1..3` / `additional_magazine` 的条件渲染真值，`GunAnimationStateContext.getMagExtentLevel()` 与 `GunDataAccessor` 也改为读取真实扩容弹匣等级；`LegacyClientOverlayEventHandler.kt` 则补上了自定义准星拦截与绘制链路。为了避免只靠“日志看起来像对了”，本轮 focused smoke 又新增了 `CROSSHAIR_RENDERED`、`MAG_RENDER_STATE` marker，以及 `passAfterAnimation` / `passAfterRefit`、`refitType` 等定向验收开关。运行时验证结论为：
       - `build/smoke-tests/runclient-focused-smoke-20260308-225105.log`：`REGULAR_OVERRIDE gun=trisdyna:rc`，随后打出 `CROSSHAIR_RENDERED type=dot_1 texture=tacz:textures/crosshair/normal/dot_1.png`、`ANIMATION_OBSERVED gun=trisdyna:rc display=trisdyna:rc_display runtime=BedrockGunModel`，并以 `PASS mode=animation_only` 收口；对应截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-225105/01-crosshair.png`、`02-rc_anim.png`、`03-rc_anim_settled.png` 已人工复核：准星帧可见自定义中心准星，后两帧中 `rc` 枪模 pose 明显推进，已不再是“纯静止不跑动画”。
       - `build/smoke-tests/runclient-focused-smoke-20260308-225305.log`：`REFIT_SLOT_FOCUS gun=tacz:ak47 type=extended_mag`、`REFIT_ATTACHMENT_APPLIED gun=tacz:ak47 attachment=applied_armorer:extended_mid_mag_aa_1`、`MAG_RENDER_STATE gun=tacz:ak47 level=1 standard=false ext1=true ext2=false ext3=false additional=false`，并以 `PASS mode=refit_preview` 收口；对应截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-225305/01-refit_slot.png`、`02-refit_applied.png`、`03-mag_ext.png` 已人工复核：改装界面确实聚焦扩容弹匣槽，安装后返回第一人称时枪模只保留一套扩容弹匣轮廓，不再把多级 ext mag 一起渲染出来。
   - **2026-03-09 第一人称 follow-up 收口（TRIS-dyna 检视飞天 / 枪焰贴图污染 / 曳光缺失）**：针对用户以 `run/tacz/[Tacz1.1.5+]TRIS-dyna GunsPack ver1.1.5.zip.zip` 为基准追加的 4 个可见问题，本轮继续对齐上游 `CustomInterpolator.java`、第一人称动画运行链、`MuzzleFlashRender` 与 `EntityBulletRenderer` 的可观察结果，并补齐 1.12.2 的必要接线差异。
      - **GLTF 检视“飞到天上”**：先前已补齐 GLTF 动画控制器与插值真值后，本轮继续用 focused smoke 做实机验收。`trisdyna:rc` 的检视验证见 `build/smoke-tests/runclient-focused-smoke-20260308-234358.log` 与截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260308-234358/01-inspect_0.png`、`02-inspect_1.png`、`03-inspect_2.png`；`trisdyna:fpc211` 的对应验收见 `build/smoke-tests/runclient-focused-smoke-20260308-234937.log` 与 `.../01-inspect_0.png`、`02-inspect_1.png`、`03-inspect_2.png`。两组截图均已人工复核：枪模保持在正常第一人称构图内，检视期间不再发生“模型飞到天上”的失真。
      - **Focused smoke 跳过 reload 守门**：为避免 `TRIS-dyna` 某些枪的 reload timing 噪声掩盖 shot-path 验收，本轮把 `FocusedSmokeRuntime.kt`、`FocusedSmokeClientHooks.kt` 与 `scripts/runclient_focused_smoke.sh` 扩展为支持 `FOCUSED_SMOKE_SKIP_RELOAD`。开启后 smoke 会在 inspect 后直接进入 regular shot 验收，并输出 `INSPECT_COMPLETED wait=4000ms skipReload=true`，从而把枪焰 / 曳光验证与 reload 真值解耦。
      - **枪焰贴图污染**：基于 `trisdyna:omerta` / `trisdyna:cms92` 的 shot-path focused smoke 已确认 Legacy 不再把枪焰纹理“黏”到后续枪模渲染上。对应日志包含 `MUZZLE_FLASH_VISIBLE texture=trisdyna:textures/flash/tris_muzzle_flash.png ...` marker，其中 `build/smoke-tests/runclient-focused-smoke-20260309-000517.log` 与截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-000517/01-muzzle_0.png` 已人工复核：枪焰出现后枪模仍使用正确枪体贴图，未再复现“枪模渲染用了枪焰贴图”的污染。
      - **曳光缺失的实际根因与收口**：本轮 focused smoke 先后新增 `BULLET_CLIENT_SPAWN`、`BULLET_RENDERER_ACTIVE`、`TRACER_SKIP_CLOSE_RANGE`、`TRACER_RENDERED` marker，把问题从“看起来没做 tracer”缩到两条 1.12.2 接线缺口：
        1. `RenderingRegistry.registerEntityRenderingHandler(EntityKineticBullet, ...)` 之前被放在 `ClientProxy.init()`，而 Forge 1.12 factory 版 API 必须在 **`preInit()`** 注册，否则 `RenderManager` 不会装配 bullet renderer；
        2. `EntityThrowable` 不会在实体首次 spawn 到客户端时自动恢复 owner，Legacy 因而会在 tracer 渲染时拿不到 shooter。现已在 `EntityKineticBullet` 的 spawn data / NBT 中同步 `shooterEntityId`，并通过 `getShooterForRender()` 在客户端按实体 id 回查 owner。
      - 曳光运行时验收已使用 `trisdyna:cms92` 收口：`build/smoke-tests/runclient-focused-smoke-20260309-001920.log` 中可见连续 marker：`BULLET_CLIENT_SPAWN ... tracer=true shooterResolved=true`、`BULLET_RENDERER_ACTIVE ...`、`TRACER_SKIP_CLOSE_RANGE ...`（近距离保护仍保留）以及随后多条 `TRACER_RENDERED gun=trisdyna:cms92 ammo=trisdyna:emx_b1 firstPerson=true color=#deffd7 ...`。截图清单 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-001920/01-muzzle_0.png`、`02-spawn_0.png`、`03-render_0.png`、`04-tracer_0.png` 中，`04-tracer_0.png` 已人工复核可见第一人称绿色曳光束，证明这条链路已经真正回到可见运行态，而不是只在日志里“理论成立”。
         - **手动复测 reopen：`cms92`“把弹药箱打出去” / 曳光偏轴 收口**：在上轮确认 tracer 可见后，用户继续反馈 `trisdyna:cms92` 第一人称里“像把子弹箱打出去了”以及“曳光往侧边飞”。继续对照上游 `EntityBulletRenderer.java`、`AmmoDisplay.java`、`AmmoEntityDisplay.java`、`GunItemRendererWrapper.java`，并扫描 `run/tacz/[Tacz1.1.5+]TRIS-dyna GunsPack ver1.1.5.zip.zip` 内 `assets/trisdyna/display/ammo/*.json` 后确认：`emx_b1` / `emx_b2` / `emx_b3` / `emx_b6` / `emx_s10` / `tris_core` 全都 `entity=null`，也就是说 TRIS-dyna 的飞行弹体本就**不该**复用 ammo 主展示模型。Legacy 先前把 `AmmoDisplay.model/modelTexture` 直接当 projectile entity 用，才会在旧图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-001920/04-tracer_0.png` 里出现居中的巨大“亚空间枪管”。本轮已补齐 `AmmoEntityDisplay`、`AmmoDisplay.entity`、`TACZClientAssetManager.buildAssetLoadPlan(...)` 的 ammo-entity 资源预加载，并把 `RenderKineticBullet.kt` 改为只在 `ammo.entity` 存在时渲染飞行实体模型；当 `entity=null` 时，表现重新与上游一致，只保留 tracer/弹道可见层。
         - 同一轮还对齐了上游第一人称 tracer 出射几何：`FirstPersonRenderGunEvent.kt` 现通过 `EntityRendererInvoker` 调用 1.12 `EntityRenderer#getFOVModifier(float, boolean)`，把缓存的 muzzle offset 从 item-FOV 空间换算回 world-FOV 空间，再交给 bullet/tracer 渲染；这修复了用户看到的“曳光往侧边飞”问题。为避免 focused smoke 截图继续抢在 `drawTracerBeam()` 前一帧，本轮还额外加入了 after-draw marker `TRACER_FRAME_DRAWN`。
         - 回归与运行时验证：`./gradlew --no-daemon test --tests 'com.tacz.legacy.client.resource.pojo.display.ammo.AmmoDisplayParsingTest' --tests 'com.tacz.legacy.client.resource.TACZClientAssetLoadPlanTest'` 通过，对应 XML `build/test-results/test/TEST-com.tacz.legacy.client.resource.pojo.display.ammo.AmmoDisplayParsingTest.xml`（5 tests）与 `.../TEST-com.tacz.legacy.client.resource.TACZClientAssetLoadPlanTest.xml`（2 tests）均为 0 failures；`./gradlew --no-daemon classes` 也通过。focused smoke `build/smoke-tests/runclient-focused-smoke-20260309-004904.log` 的 `01-render_0.png` 已人工复核：旧的飞行“弹药箱/竖直大实体”不再出现；随后 `build/smoke-tests/runclient-focused-smoke-20260309-005418.log` 已打出 `TRACER_FRAME_DRAWN`，对应截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-005418/02-tracer_drawn.png` 可见细线 tracer 从准星附近中心线方向出射，不再像用户复测时那样明显向侧边偏出。
         - **第二轮 reopen：所有枪 tracer 共性偏轴 / 正视难见 收口**：在用户继续反馈“所有枪 tracer 都偏、只有视角朝下才明显、总是歪在左侧飞”后，再次回看上游 `EntityBulletRenderer.java` 发现 Legacy 这条链路除了枪口 offset 之外，还保留了一个更根本的偏差：上游 tracer 实际渲染的是内置 `basic_bullet` 体积模型（`DEFAULT_BULLET_MODEL` + `DEFAULT_BULLET_TEXTURE`），而 Legacy 一直在用自绘的两片交叉 quad 代替。这种 quad 在第一人称近轴观察时很容易只剩一条侧边可见，于是用户主观上就会看到“左边一条线”“正视几乎看不到”。本轮已把 `RenderKineticBullet.kt` 的 tracer 几何切回 classpath 内置 `assets/tacz/models/bedrock/basic_bullet.json` + `textures/entity/basic_bullet.png`，并保持 additive/full-bright 渲染语义，与上游体积 tracer 的可见性更一致。
         - 同时补齐了上游 `EntityKineticBullet` 的客户端朝向同步：`LegacyEntities.kt` 现在在 spawn data 中额外同步 `rotationPitch` / `rotationYaw` / `motionX` / `motionY` / `motionZ`，客户端 `readSpawnData(...)` 立即恢复这些值；`onUpdate()` 也改为按上游 `lerpRotation` 语义平滑追随真实弹道，而不是每 tick 直接把 `prevRotation*` 覆盖成当前值。这保证 tracer 体积模型会沿实际 bullet motion 对齐，而不是在客户端用更抖或更滞后的朝向去画。
         - 新一轮 focused smoke `build/smoke-tests/runclient-focused-smoke-20260309-015139.log` 已再次用 `trisdyna:cms92` 验证：日志包含 `BULLET_RENDERER_ACTIVE`、`TRACER_RENDERED`、`TRACER_FRAME_DRAWN` 与最终 `PASS`；截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-015139/02-tracer_drawn.png` 已人工复核，可见 tracer 不再退化成屏幕左侧的一条薄边，而是回到枪口前方、围绕弹道中心的体积束。结合前面的 ammo entity / FOV offset 修正，本轮将“所有枪 tracer 共性侧偏”的根因从 pack 数据或单枪 display 差异收窄并收口到渲染几何 parity + 客户端弹体朝向同步两个公共层面。
         - **第三轮 reopen：render-data 证据链复核 + 第一人称枪口缓存时机收口**：在用户继续指出“所以还是左边一条线”“不要再以烟测截图乐观下结论，要看弹体渲染数据是否真的聚焦在屏幕中心附近”后，本轮把 tracer 验收从“看到 tracer 了”进一步升级为**中性视角 + 屏幕投影坐标**验证。为避免 smoke 在 tracer 可见前提前退出，`FocusedSmokeRuntime.kt` / `RenderKineticBullet.kt` 新增了可选 `tacz.focusedSmoke.requireTracerFrame=true` 诊断闸门，只有打到 `TRACER_FRAME_OBSERVED` 才会 PASS；`FocusedSmokeClientHooks.kt` 也补齐了真正作用到本地玩家视角的 `regularShotPitch/Yaw` override。基于这些诊断链路，先在 `build/smoke-tests/runclient-focused-smoke-20260309-023346.log` 证明了仅靠“上游式 bullet spawn reset + shootFromRotation 语义”还不够：虽然近距离可见性恢复了（先出现 `TRACER_SKIP_CLOSE_RANGE distance=0.494...`，随后 `TRACER_RENDERED distance=2.273...`），但当时 `TRACER_PROJECTION` 仍显示 `originBeforeOffset=(2411.1,1037.1,...)`、`originAfterOffset=(2949.6,692.9,...)`、`centerScreen=(-2723.2,2760.8,...)`，说明第一人称 offset 一应用就把 tracer 从屏幕右侧甩出并横切到左边，和用户描述的“左边一条线”完全一致。继续对照上游 `GunItemRendererWrapper.cacheMuzzlePosition(...)` 后确认，Legacy 的 `FirstPersonRenderGunEvent.kt` 仍在 `model.render(stack)` **之前**缓存 `muzzleFlashPosPath`，而 upstream 明确是在枪模渲染完成后缓存；现已将 `cacheMuzzleRenderOffset(...)` 挪到 `model.render(stack)` 之后，与上游时机对齐。修复后的 focused smoke `build/smoke-tests/runclient-focused-smoke-20260309-023629.log` 和带截图验收 `build/smoke-tests/runclient-focused-smoke-20260309-023805.log` 已给出新的 render-data 证据：`TRACER_PROJECTION` 分别收敛到 `originBeforeOffset=(1911.8,1038.2,...)` / `originAfterOffset=(1870.9,1003.8,...)` / `centerScreen=(1822.9,986.7,...)`，以及更稳定的一轮 `originBeforeOffset=(1909.8,1037.2,...)` / `originAfterOffset=(1899.4,1026.6,...)` / `centerScreen=(1897.7,1025.0,...)` / `headScreen=(1895.5,1023.0,...)`。对应截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-023805/01-tracer.png` 分辨率为 `3822×2074`，说明 tracer 的 tail/head/center 已重新压回屏幕中心附近，不再沿左侧错误向量飞行。至此，本轮把 tracer 剩余公共层问题进一步从“弹体出生点 / 网络同步 / tracer 几何”收窄并收口到**第一人称枪口 offset 的缓存时机**，用户要求的 render-data 证据链也已完整留档。
         - **第四轮 reopen：斜视角仍会歪 / 第一人称 yaw 语义归一化收口**：在用户继续反馈“正视没问题，但只要视角越偏 tracer 依旧会变歪，特别是东南/西北这种斜向方位，会像一条斜线先从屏幕边缘飞出来再往准星靠”后，本轮继续把诊断从“枪口缓存时机”推进到**枪口偏移与相机 yaw 的语义是否一致**。对照上游 `EntityBulletRenderer.renderTracerAmmo(...)` 与 `net.minecraft.client.Camera` 后确认：上游 `bullet.getCameraYRot()` / `Camera.getYRot()` 保存的是**原始视角 yaw**，并且 tracer 代码内部自己再做一次 `+180°` 来绕回第一人称相机空间；而 Legacy 这时缓存的 `cachedCameraYaw` 来自 1.12 `EntityViewRenderEvent.CameraSetup`，该值本身已经带着 `orientCamera()` 里的 `+180°` 视图矩阵语义。如果继续把这个值当成上游 `Camera.getYRot()` 去用，再在 tracer 里追加一次 `+180°`，就会在斜视角下把枪口偏移绕到错误象限。
         - 针对这一点，本轮给 `FirstPersonRenderGunEvent.kt` / `RenderKineticBullet.kt` 增加了同帧 `muzzle offset + camera yaw/pitch + frameId` 锁存与 `FIRST_PERSON_LATCH` 日志，并把 tracer 端的 yaw 归一化为 `cameraYawRaw - 180°` 后再走上游同样的 first-person rotate/translate/reverse-rotate 顺序。修复前的 45° 斜视角 smoke `build/smoke-tests/runclient-focused-smoke-20260309-130058.log` 中，`FIRST_PERSON_LATCH` 仍显示 `muzzleCameraYaw=225.0000 ... playerYaw=45.0000`，对应 `TRACER_PROJECTION originAfterOffset=(891.3,495.2,...) centerScreen=(991.0,1407.6,...)`，典型就是“先从屏幕边缘斜插进来”；修复后的同场景 `build/smoke-tests/runclient-focused-smoke-20260309-131023.log` 则收敛为 `muzzleCameraYawRaw=225.0000 muzzleCameraYaw=45.0000 ... playerYaw=45.0000`，并把 `TRACER_PROJECTION` 压到 `originAfterOffset=(955.5,974.0,...) centerScreen=(956.0,961.4,...) tailScreen=(955.5,974.0,...) headScreen=(956.6,942.5,...)`。这说明 tracer 现在已经不再沿错误象限“先飞到边缘再折回准星”，而是直接围绕准星附近出射。
         - 为了让这类第一人称 tracer 更容易抓图，本轮还把 focused smoke 扩展为支持世界视觉与 tracer 抓图倍率：`tacz.focusedSmoke.worldTime` / `freezeWorldTime` / `clearWeather` 可把自动世界锁到固定时间（例如午夜）并关闭天气；`tacz.focusedSmoke.bulletSpeedMultiplier` / `tracerSizeMultiplier` / `tracerLengthMultiplier` 则可单独放大抓图场景中的弹速、曳光粗细与长度，而不影响正常玩法默认值。`scripts/runclient_focused_smoke.sh` 也同步支持 `FOCUSED_SMOKE_WORLD_TIME`、`FOCUSED_SMOKE_BULLET_SPEED_MULTIPLIER`、`FOCUSED_SMOKE_TRACER_SIZE_MULTIPLIER`、`FOCUSED_SMOKE_TRACER_LENGTH_MULTIPLIER` 等环境变量。
         - 放大抓图验证见 `build/smoke-tests/runclient-focused-smoke-20260309-141333.log`：日志先打到 `[FocusedSmoke] WORLD_VISUAL_OVERRIDES dimension=0 worldTime=18000 freezeTime=true clearWeather=true` 与 `[FocusedSmoke] TRACER_CAPTURE_OVERRIDES bulletSpeedMultiplier=0.45 tracerSizeMultiplier=6.0 tracerLengthMultiplier=5.0`，随后 `FIRST_PERSON_LATCH` 继续保持 `muzzleCameraYawRaw=225.0000 muzzleCameraYaw=45.0000 playerYaw=45.0000`，`TRACER_PROJECTION` 则显示 `width=0.03000 length=9.4765 originAfterOffset=(958.1,972.8,...) centerScreen=(988.1,685.7,...) tailScreen=(958.1,972.8,...) headScreen=(941.3,1133.2,...)`，最终打出 `TRACER_FRAME_OBSERVED` 与 `PASS`。对应截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-141333/01-midnight_sky.png` 已归档；虽然由于 tracer 现在重新贴近准星近轴出射，单帧 PNG 视觉上更像一段紧凑亮束而不是夸张横线，但它已经不再体现用户最初那种“从屏幕边缘斜着飞进来”的错误向量，且与日志里的屏幕坐标结论一致。
         - **2026-03-09 第一人称 Bloom 时序收口（GT/Lumenized callback 提前于 hand 渲染）**：在把 `_illuminated` 接到 GT/Lumenized Bloom API 后，继续对照 `BloomEffectUtil.renderBloomBlockLayer(...)`、1.12 `EntityRenderer.renderWorldPass(...)` / `renderHand(...)` 与当前 `FirstPersonRenderGunEvent.kt` 发现：Legacy 之前的第一人称 Bloom 是借由 GT callback 提前重放，而 vanilla 真正的第一人称枪模发生在 `renderHand()` 末尾、且其前一行会 `GlStateManager.clear(256)` 清空 depth。结果就是：Bloom pass 比 base gun 更早，且它使用的是 hand FOV / hand 矩阵，却和 world pass 的 depth/投影处在同一时机，视觉上容易出现“枪模盖住自己的 Bloom”“深度关系诡异”甚至动画轻微拖影的风险。
         - 本轮修正后，**世界 / 第三人称** 仍沿用 `TACZBloomBridge` + `TACZBloomHooks` 的 GT callback 重放；但**第一人称** 已从 callback 中拆出，改为在 `FirstPersonRenderGunEvent.renderPreparedFirstPersonModel(...)` 的 hand 阶段内联执行：同一个 `PreparedFirstPersonRenderContext`、同一套矩阵栈、先 `model.render(stack)` 画 base gun，再通过新增的 `TACZFirstPersonBloomRenderer.kt` 把 `model.renderBloom(stack)` 画入私有 FBO，并直接调用 `BloomEffect.renderLOG/Unity/Unreal(...)`（按 Lumenized 配置选型）叠回主 framebuffer。这样第一人称 Bloom 与枪模共享同一份 partialTicks / 动画状态机 / 程序化位移状态，不再跨时机重算，也不再依赖那个早于 hand 的 GT callback。
         - 验证：`./gradlew --no-daemon classes` 通过；带 Lumenized 的 focused smoke `build/smoke-tests/runclient-focused-smoke-20260309-031457.log` 已再次打到 `[FocusedSmoke] FIRST_PERSON_BLOOM_RENDERED gun=tacz:aa12`、`ANIMATION_OBSERVED` 与最终 `PASS`。归档截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-031457/01-bloom.png` 已人工复核：AA12 第一人称枪模本体正常可见，准星位的绿色 `_illuminated` 发光也仍然存在，说明“hand 阶段内联 Bloom”链路已实机跑通，而不是只在代码层面重排顺序。
         - 补充复核：后续用户再次实机确认“测试已经正常了，只是因为该武器的发光不明显而已”。因此这轮 Bloom reopen 的最终结论是：**GT/Lumenized Bloom 接线、第一人称 hand 阶段时序与 FBO 合成链路均已正常工作**；AA12 当前观感更偏“发光不算显眼”，属于该枪 `_illuminated` 几何分布 / 贴图亮度 / 全局 bloom 阈值共同决定的视觉强度问题，而不是渲染链路失效。若后续想进一步“变亮”，应优先检查资产与配置，而不是继续改第一人称 Bloom 时序代码。
         - **2026-03-09 第一人称 tracer follow-up 复核（hand bobbing parity + 同帧屏幕坐标审计）**：在用户继续质疑“向下看时 tracer 仍像从屏幕中部冒出来再往边缘飘”后，本轮没有再直接相信单帧截图，而是回到上游 `RenderItemInHandBobEvent.BobView` / Legacy 1.12 `EntityRenderer.renderHand()` 的实际接线去做逐帧诊断。首先新增 `mixin/minecraft/client/EntityRendererMixin.java`，在第一人称持枪时跳过 vanilla `applyBobbing(float)`，把 1.12 hand bobbing 行为对齐到上游 TACZ 的“枪械 hand render 不吃原版 bob view”语义；随后在 `FirstPersonRenderGunEvent.kt` 中新增 `MUZZLE_SCREEN` 诊断，把**真正 hand render 时的枪口节点**投影到当前视口坐标，再与 `RenderKineticBullet.kt` 中 tracer 的 `tailScreen` / `originAfterOffset` 做同帧比较。
         - 两轮 focused smoke 的关键证据如下：`build/smoke-tests/runclient-focused-smoke-20260309-195739.log` 中，`muzzleFrameId=1038` 的 `MUZZLE_SCREEN=(957.8,930.7,0.9727)`，而同帧 `TRACER_PROJECTION tailScreen=(955.9,973.9,0.9832)`；更严格的极慢 tracer 复核 `build/smoke-tests/runclient-focused-smoke-20260309-200239.log` 中，`muzzleFrameId=1058` 的 `MUZZLE_SCREEN=(945.9,931.2,0.9727)`，对应 `tailScreen=(946.8,947.0,0.9766)`。也就是说，在完成此前的 FOV 空间换算、枪口缓存时机修正与 yaw 语义归一化之后，**tracer 尾部已经回到与真实枪口几乎重合的屏幕位置**；剩余差值只有十几到几十个视口像素量级，不再符合“从屏幕中心或边缘错误生成”的矩阵级偏差特征。
         - 同时，本轮也把视觉验收失败明确记录下来：归档截图 `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260309-195739/01-downward_tracer.png`、`.../runclient-focused-smoke-20260309-200043/01-downward_tracer_big.png` 与 `.../runclient-focused-smoke-20260309-200239/01-downward_tracer_slow.png` 在即使放大 tracer 长度/粗细、并把弹速降到极慢后，仍未把 tracer 本体稳定拍进 PNG；因此当前 focused smoke 的截图触发链**不适合继续用来判断单帧 tracer 原点是否正确**。这一 reopen 的正式结论应更新为：在现有工作树上，第一人称 tracer 的几何起点已经与 hand-render 枪口基本对齐；后续若用户仍主观感觉“从中间冒出来”，更应优先检查 tracer 中段/头部的视觉显著性、截图链时机或 pack 自身表现层，而不是再回到枪口偏移矩阵链上做大改。
- **验证状态**：编译与定向测试已覆盖 Client UX / gunsmith / refit backend / render parsing 等阶段性变更；渲染基础设施已有一轮成功的 `runClient` smoke。2026-03-08 最终清理轮已移除此前阻塞 `compileTestKotlin` 的 `LegacyClientShootCoordinatorBurstStateTest`（引用已废弃的 pending-burst API）与 `MathUtilFovTest`（引用已移除的 FOV 函数），以及无法在无头环境运行的 `BedrockGunModelRefitViewPathTest`（`ClassNotFoundException`），同时修复 `TACZAudioRuntimeTest.manifest rejects ogg files with incomplete vorbis headers`（改为使用 channels=0 使 probe 真正拒绝）。
   - 2026-03-09 本轮继续修复 4 项 gameplay parity 缺口并新增到 219 tests 全通过：
     1. **fuel/inventory 弹药检查（needCheckAmmo）**：`LivingEntityShoot.kt` 此前硬编码 `needCheckAmmo = true`，导致创造模式下 fuel/inventory reload 类型武器（如 `trisdyna:cms92x`、`trisdyna:ch104`、`applied_armorer:moritz_mg_hmg22`、`tacz:s10dmd`）即使正确触发了客户端射击也会在服务端被 `NO_AMMO` 拦截。现已改为从 `IGunOperator.fromLivingEntity(shooter).needCheckAmmo()` 获取，`consumeAmmoFromPlayer` 在 `!needCheckAmmo` 时直接返回所需量而不消耗背包物品。三把武器的 focused smoke 均 PASS。
     2. **AmmoCountStyle HUD 渲染**：上游 `GunDisplay` POJO 有 `ammo_count_style` 字段（`NORMAL` / `PERCENT`），Legacy 之前完全缺失，始终使用 `DecimalFormat("000")`。现已新增 `AmmoCountStyle.java` 枚举、`GunDisplay.java` + `GunDisplayInstance.java` 字段、`LegacyClientOverlayEventHandler.kt` 按样式渲染（`PERCENT` → `DecimalFormat("000%")`），4 项新测试覆盖。hmg22 的 display JSON 中 `ammo_count_style: "percent"` 现可正确消费。
     3. **Burst fire 服务端调度**：`BurstFireTaskScheduler.tick()` 此前从未在运行时被调用——仅由测试驱动。现已在 `ShooterTickHandler.onServerTick(ServerTickEvent)` 中添加调用，与上游 `CycleTaskHelper.tick()` 在 `ServerTickEvent` 中的位置对齐。B93R（burst=3, bpm=900）smoke PASS，单元测试覆盖 scheduler 周期语义。
     4. **客户端动画状态机时间戳（hmg22 连射加速）**：`GunAnimationStateContext.getLastShootTimestamp()` / `getShootTimestamp()` 此前返回服务端相对时间戳（`ShooterDataHolder.lastShootTimestamp`），而 `getCurrentTimestamp()` 返回 `System.currentTimeMillis()`，导致 delta 总是巨大、连射检测永远不成立。现已改为从 `LegacyClientShootCoordinator` 读取客户端绝对时间戳（`clientLastShootTimestampMs` / `clientShootTimestampMs`），使 Lua 脚本中的 `context:getCurrentTimestamp() - context:getLastShootTimestamp() < shootInterval + 100` 判定回到正确区间。hmg22 的客户端 state machine 脚本（`applied_armorer:moritz_mg_hmg22`）与服务端 data script（`applied_armorer:moritz_mg_hmg22_logic`）两条加速路径现均有正确的时间基准。
   - 当前 `./gradlew test` 全量 226 tests 通过（含 7 项新增过热冷却公式测试）。focused smoke 验证：`tacz:timeless50`（半自动 fire-mode fallback）PASS、`tacz:b93r`（三连发服务端调度）PASS、`tacz:rpg7`（爆炸弹道）PASS、`trisdyna:cms92x` / `trisdyna:ch104` / `tacz:s10dmd`（fuel/inventory 弹药检查）PASS。后续若某些 client UX / refit 手工路径再次被 Forge 1.12 对多版本 Kotlin jar 的 ASM 扫描问题挡在模组初始化前，仍应按"环境阻塞而非本轮回归"记录，不要把阶段性验证结论混成一团。

因此，下一阶段最值得投入的主线通常不是“继续重迁数据/战斗/Client UX/Render 基础设施主链”，而是：
    1. **Render 剩余子轨**：~~animation state machine~~（已落地）、~~关键帧插值~~（已落地）、bone animation application、ammo/attachment display renderer、~~muzzle flash~~（本轮已落地） / shell ejection、第一人称 hand/scope 渲染链路、~~程序化后坐力 / 射击摆动 / 跳跃摆动~~（本轮已落地）、~~ADS 二阶动力学平滑~~（本轮已落地）、~~约束骨骼逆变换~~（本轮已落地）
      2. **Combat 剩余 parity 子轨**：~~hurt/kill 事件与客户端同步消息~~、~~边界 fire-mode 状态切换~~、~~needCheckAmmo 真值~~（已修复）、~~burst 服务端调度~~（已补齐 `BurstFireTaskScheduler.tick()` 到 `ServerTickEvent`）、~~脚本武器时间戳基准~~（已修复 `GunAnimationStateContext` 客户端绝对时间戳）、~~AmmoCountStyle HUD~~（已迁移）、~~过热系统散热加速 + 冷却延迟对齐~~（已修复 `tacz$tickHeat` 不再重置 `heatTimestamp`，公式与上游完全一致；`handleShootHeat` 补上 `heatMax` cap；`heatTimestamp` 改为在 `shoot()` 入口统一赋值，脚本路径也覆盖）、~~过热数据客户端-服务端不同步~~（已修复 `tacz$tickHeat` 加 `isRemote` 保护，仅服务端计算冷却，客户端通过 ItemStack NBT 同步获取，与上游 `onTickServerSide` 仅服务端 tick 一致；根因是客户端 `heatTimestamp` 仅在 `draw()` 时设置而不随射击更新，导致客户端冷却量远大于服务端）以及少数未覆盖武器脚本/门禁边角的一致性收尾
      3. **Client UX / Refit 非阻塞微收尾**：例如 `GunRefitScreen` 的遮罩 / 构图微调、按钮 hover / 贴图的像素级 polish、更多附件副作用的可视反馈补足（opening / 切槽插值 / property diagram 主链已回到生产路径）
      4. **第三方兼容与剩余玩法收尾**：在核心主链已成形的前提下，继续补 JEI / KubeJS / 高级玩法与表现边角

## 最新对比测试回归分诊（2026-03-08）

本轮对比截图与实机反馈说明：**当前剩余问题已经不适合让一个 Agent 全包。** 建议继续复用同一个 `TACZ Migration` Agent，但按下面的 Prompt/轨道拆分迭代。

补充正式状态：截至 `runclient-focused-smoke-20260308-185443.log`，标准枪 `tacz:hk_mp5a5` 的第一人称 `draw / inspect / shoot` 动作已在实机链路恢复，且左键挥手 / 挖方块泄漏已被新的 common + client 双层抑制收口。当前 render 轨道若仍需继续迭代，优先级应转为“个别枪的构图/尺寸 polish 与剩余动作细节一致性”，而不是把问题继续表述为“动作完全不播 / 左键完全没抑制”。

| 用户可见症状 | 推荐 Prompt | 使用 Agent | 归属说明 |
|---|---|---|---|
| 若仍有个别枪的基础持枪/瞄准构图偏差、ADS/后坐力/镜头反馈细节不一致，或剩余动作细节与上游存在偏差 | `.github/prompts/tacz-stage-render-animation-first-person.prompt.md` | `TACZ Migration` | 这是第一人称 pose / animation runtime / render-frame 插值 / fire feedback 的直接职责；当前重点已从“动作完全不播”转为细节 polish 与逐枪 parity |
| 某些枪模型本应显示的数字/字模/能量读数缺失，或 gun-specific runtime/material 节点没被消费 | `.github/prompts/tacz-stage-render-material-parity.prompt.md` | `TACZ Migration` | 这是 gun-specific model runtime / material / model text layer parity，不应塞给 GUI 或 combat |
| 武器完全没音效，需要回答“没对接”还是“实现有问题” | `.github/prompts/tacz-stage-audio-engine-compat.prompt.md` | `TACZ Migration` | 音频 Agent 应负责 runtime/backend/真实播放验证；不能再用 diagnostic smoke 代替可听结果 |
| 沉浸式改装 GUI 与上游完全不是一个东西，枪模没有从手持状态平滑过渡到屏幕内预览 | `.github/prompts/tacz-stage-client-ux-gui-i18n.prompt.md` | `TACZ Migration` | 这属于 `GunRefitScreen` / screen composition / preview transition / 交互体验 parity，主责不在 render fire feedback |
| 爆发模式没有冷却、打成错误射速 | `.github/prompts/tacz-migrate-combat-network.prompt.md` | `TACZ Migration` | 这是 fire-mode / cadence / server accept gate 真值问题，主责在 combat/network |
| 若以上任一 Agent 被共享 hook / smoke / 注册问题挡住 | `.github/prompts/tacz-stage-foundation-client-hooks.prompt.md` | `TACZ Migration` | Foundation 只负责共享接线与验证守门，不接管 feature 本体 |

补充：此前文档曾把“`GunRefitScreen` 的沉浸式 world-to-screen 开场过渡已完整落地”写成既成事实，但当前工作树的真实基线是“旧版稳定布局 + `refit_view` / `refit_<type>_view` 节点驱动的小视口预览 + refitPreview smoke 验证入口”。后续 Client UX 轨道若继续接手 refit，应在这个基线之上继续补 opening/遮罩/构图 polish，而不要再假定 `TACZRefitTransform` 仍是现行生产实现。

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
   - 继续收 hurt/kill 同步、边界 fire-mode 切换与少数未覆盖脚本门禁；burst cadence、普通枪稳定出弹与 RPG 基础爆炸链路已完成一轮 smoke 级验证。
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

## 2026-03-08 紧急补充：关于欺骗性交付的应对措施
**注意**：在首轮拆分任务后，执行第一人称渲染和音频系统的 Agent 均报告“已完成”，但实机反馈显示：
- Audio 彻底断片（没有音效 log）。
- 渲染全部卡在 Idle（未接入 `inspect` 状态及原版 Transform 屏中心透视接管失效）。
- Muzzle Flash、Recoil 等未做。

为此，**测试底层已得到强化**：
1. 烟测脚本 `scripts/runclient_focused_smoke.sh` 现已具有 `ATTEMPT_INSPECT` 和截图劫持拦截：默认在执行完成第一渲染后强拉 Inspect，并在 0/1/2 秒抓取画面。
2. 任何 Agent 再次认领这些任务时，**都面临严密的截图核对流程与触发记录追踪**。如果在 `last-focused-screenshots.txt` 中的图片以及截图中枪模型均还在原版位置，或者在日志没有打出真实的播放音频/开火粒子调用，将被一票否决。
3. 请继续派发以下 Agent (Prompt 已经做针对性强化)：
   - `TACZ Migration` + `.github/prompts/tacz-stage-render-animation-first-person.prompt.md`
   - `TACZ Migration` + `.github/prompts/tacz-stage-audio-engine-compat.prompt.md`

## 2026-03-09 — 脚本系统完整对齐

**问题**：此前落地的脚本系统（Bug 4 修复）只实现了 `shoot` hook，且存在缺失 API、吞异常的问题，导致所有脚本枪开火后不出子弹。

**本轮变更**：

1. **TACZDataScriptManager** — 完全重写：
   - 注入全局 Lua 常量（`SEMI`, `AUTO`, `BURST`, `NOT_RELOADING`, `EMPTY_RELOAD_FEEDING`, 等 8 项）
   - 脚本加载 fail-fast，不再 catch LuaError

2. **GunDataAccessor / GunCombatData** — 新增字段：
   - `scriptParams: Map<String, Any>` — 解析 `script_param` JSON 对象
   - `boltFeedTimeS: Float` — 解析 `bolt_feed_time`，默认 -1

3. **TACZGunScriptAPI** — 完全重写（~420 行），对齐上游 `ModernKineticGunScriptAPI` 全部 API：
   - 新增方法：`getNeededAmmoAmount`, `getScriptParams`, `safeAsyncTask`, `calcHeatReduction`, `getHeatMinInaccuracy/MaxInaccuracy`, `getMagExtentLevel`, `getAttachment`, `getBolt`
   - 新增 `create()` 工厂方法
   - `performSingleFire` 支持 `handle_shoot_heat` 脚本 hook
   - `checkFunction()` 遇到非 nil 非 function 时 fail-fast 抛 LuaError

4. **BurstFireTaskScheduler** — 新增 `addCycleTask(task, delayMs, periodMs, cycles)` 延迟启动重载

5. **LivingEntityShoot** — 移除 try-catch，使用 `TACZGunScriptAPI.create()` 工厂方法

6. **LivingEntityReload** — 全面重写，添加脚本 hook：
   - `reload()` → 调用脚本 `start_reload`（返回 false 取消换弹）
   - `tickReload()` → 调用脚本 `tick_reload`（返回 `{stateType, countDown}`），否则走 `defaultTickReload`
   - `cancelReload()` → 调用脚本 `interrupt_reload`
   - `defaultTickReload` / `defaultReloadFinishing` 与上游对齐

7. **LivingEntityBolt** — 全面重写，添加脚本 hook：
   - `bolt()` → 调用脚本 `start_bolt`（返回 boolean）
   - `tickBolt()` → 调用脚本 `tick_bolt`（返回 boolean），否则走 `defaultTickBolt`
   - `defaultTickBolt` 与上游对齐（含 boltFeedTime 两阶段）

8. **LivingEntityMixin (`tacz$tickHeat`)** — 添加 `tick_heat` 脚本 hook 分发

**覆盖的枪包脚本**：
- Applied Armorer: hmg22, win_win, melee_task_manager
- TRIS-dyna: cf007, cms92x, fs2000, noammo, rc, rc_ar
- Cyber Armorer: carnage, grad, m2038, mantis_blade

**待做**：`calcSpread` hook 仅 mantis_blade 使用，当前未实现，对大多数枪包无影响。

**验证**：编译通过，210/210 测试全绿。

### 2026-03-09 Bug Fix: fuel/inventory 类型枪械创造模式无法射击

**问题**：用户报告 TRIS-dyna 枪包中 ch104（fuel）、cms92x（fuel+scripted）、s10dmd（inventory）在创造模式下"有声音有动画但没子弹"。

**根因**：`LivingEntityShoot.kt` 中 `hasInventoryAmmo` 检查硬编码传入 `true`，而上游 TACZ 传入 `gunOperator.needCheckAmmo()`。对于创造模式玩家，`needCheckAmmo()` 返回 `false`，使得 `hasInventoryAmmo(shooter, stack, false)` 直接返回 `true`（跳过真实库存检查）。Legacy 硬编码 `true` 导致服务端始终执行真实库存搜索——创造模式玩家没有实际弹药物品，所以返回 `NO_AMMO`。客户端侧使用了正确的 `needCheckAmmo()` 因此认为有弹药→播放动画和音效→发送射击包→服务端拒绝→无子弹。

**修复**：
- `LivingEntityShoot.shoot()`: `hasInventoryAmmo` 改为传入 `needCheckAmmo` 而非硬编码 `true`
- `LivingEntityShoot.reduceAmmoOnce()`: 同上
- `LivingEntityShoot.consumeAmmoFromPlayer()`: 新增 `needCheckAmmo` 参数，`false` 时直接返回 `neededAmount`（创造模式不消耗弹药——与上游一致）
- 闭膛上膛路径同步传递 `needCheckAmmo`
- `TACZGunScriptAPI.kt` 已验证无此 bug（已使用 `isReloadingNeedConsumeAmmo()`）

**验证**：212/212 单元测试通过，focused smoke ch104 PASS、cms92x SERVER_SHOOT_RESULT=SUCCESS、s10dmd PASS。

### 2026-03-09 HUD 弹药格式兼容迁移：AmmoCountStyle

**迁移内容**：上游 TACZ `GunHudOverlay` 支持 `ammo_count_style` 字段（`"normal"` / `"percent"`），`percent` 模式下 HUD 不显示弹药绝对数而是百分比（如 `050%`）。Legacy 之前始终走 `"000"` 三位数格式，缺失百分比模式。

**新增/变更文件**：
- `AmmoCountStyle.java`（新增）：与上游一致的枚举，`@SerializedName("normal") NORMAL` / `@SerializedName("percent") PERCENT`
- `GunDisplay.java`：新增 `ammo_count_style` 字段 + `getAmmoCountStyle()` getter（POJO 层，Gson 反序列化直接消费）
- `GunDisplayInstance.java`：新增 `ammoCountStyle` 字段，`create()` 时从 POJO 读取 + getter（运行时层，与上游对齐）
- `LegacyClientOverlayEventHandler.kt`：新增 `currentAmmoFormatPercent`（`"000%"`），`renderGunHud` 按 `ammoCountStyle` 选择格式化路径；同时消除了 `snapshot/displayId/gunDisplay` 的重复解析

**回归测试**（4 项新增）：
- `ammo_count_style defaults to NORMAL when absent`
- `ammo_count_style parses normal`
- `ammo_count_style parses percent`
- `percent format produces expected output`

**验证**：216/216 单元测试通过，编译成功。
