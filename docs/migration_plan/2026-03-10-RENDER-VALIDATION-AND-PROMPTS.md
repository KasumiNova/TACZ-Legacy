# 2026-03-10 / RENDER-VALIDATION-AND-PROMPTS 分类分册

> 本分册承接原大文档中 **Render / Animation / Client Resource / 视觉 reopen / Prompt 细分与并行协作建议** 的详细内容。  
> **文件名中的日期表示本分类文档的建档/结构整理日期，不代表正文中的所有开发都发生在 2026-03-10。**  
> 当一份分类分册汇总多天开发内容时，真实开发日期必须继续在正文小节中明确标注；全局入口仍以 `docs/migration_plan/MAIN.md` 为准。

## 适用范围

本分册集中记录：

- 渲染 / 动画 / 客户端资源阶段状态
- 第一人称、tracer、bloom 等高风险视觉 reopen 的结论
- 视觉 focused smoke 的验收原则
- Prompt / Slash Command 路由
- 2026-03-10 非兼容专项 Prompt 拆分
- Render / Audio / Client UX / Combat 的并行协作建议

## Render 轨道当前总览（截至 2026-03-10）

### 已落地基础设施

- `client/resource/pojo/model/**`
- `client/resource/pojo/display/gun/**`
- `client/resource/serialize/Vector3fSerializer.java`
- `client/model/bedrock/**`
- `client/resource/TACZClientAssetManager.kt`
- `client/renderer/item/TACZGunItemRenderer.kt`
- `ClientProxy.kt` 中的 item renderer 接线
- `BedrockModelParsingTest`、`GunDisplayParsingTest` 等解析回归

历史 smoke 已验证客户端能从 gun pack 加载：

- `110` 个 display
- `166` 个 model
- `166` 个 texture

### 第一人称动画链

已落地的关键 parity：

- `default_animation` 与 `use_default_animation(rifle/pistol)` controller prototype 回退
- 等价于上游 `TickAnimationEvent(RenderTickEvent)` 的 `visualUpdate()` / `updateSoundOnly()` 驱动
- 近战输入真正路由到 `INPUT_BAYONET_MUZZLE / STOCK / PUSH`
- `put_away` exiting 生命周期接回生产链

focused smoke 已给出：

- `ANIMATION_OBSERVED`
- `PASS`

标准枪 `tacz:hk_mp5a5` 已证明默认回退与第一人称动画链真实跑到运行时。

### TextShow / 模型文字显示

已完成上游 `TextShow + PapiManager + TextShowRender` 完整移植，关键结论：

- 模型字模 / placeholder / bone-aligned 文本绘制主链已落地
- `ColorHexTest` 与 `TextShowDeserializationTest` 已覆盖
- focused smoke PASS
- 该项已不再是当前 Render 主缺口

### 第一人称程序化动画与 fire feedback

已完成的上游语义包括：

- `SecondOrderDynamics`
- `PerlinNoise`
- `Easing.easeOutCubic`
- `MathUtil.getEulerAngles / applyMatrixLerp`
- `applyShootSwayAndRotation`
- `applyJumpingSway`
- `applyAnimationConstraintTransform`
- view bob 补偿
- `MuzzleFlashRender`
- `LegacyClientShootCoordinator.onShoot()` 驱动 recoil / muzzle flash 时间戳

### 左键抑制与第一人称接管

- `TACZClientAssetManager.loadScriptFromSource(...)` 现已把 `LuaAnimationConstant` / `LuaGunAnimationConstant` 正确安装到 `Globals`
- `PreventGunLeftClickHandler.kt` + `MinecraftMixin.java` 双层抑制已阻断原版挥手 / 挖方块泄漏
- focused smoke 已打到：
  - `LEFT_CLICK_SUPPRESSED`
  - `INSPECT_TRIGGERED`
  - `REGULAR_SHOT_SENT`
  - `PASS`

### 第一人称构图收口

- `FirstPersonRenderGunEvent.kt` 已移除把枪模长期推向右下 / 拉远镜头的常驻 vanilla baseline
- 第一人称 framing 已重新以 `idle_view` / `iron_view` positioning 为主
- `timeless50` 已不再停留在原版右下角小体积构图

结论：

- “第一人称仍在原版主手位置”应视为**已收口问题**
- 后续更多是逐枪 polish，而不是回到整条矩阵链重写

## 2026-03-08 / 2026-03-09 Render reopen 摘要

### GLTF 动画 / 扩容弹匣 / 准星系统

已收口：

- `.gltf` 动画消费链最小可用版本
- `BedrockGunModel` 扩容弹匣条件渲染
- 自定义准星拦截与绘制
- `trisdyna:rc` 的动画不再停在纯静止 pose
- `ak47` 的扩容弹匣不再把多级 ext mag 同时渲染出来

### TRIS-dyna follow-up：检视飞天 / 枪焰污染 / 曳光缺失

已收口结论：

- `trisdyna:rc` 与 `trisdyna:fpc211` 的 inspect 已不再“飞到天上”
- `trisdyna:omerta` / `trisdyna:cms92` 的枪焰贴图污染已修复
- tracer 缺失问题最终定位并修复为：
  1. bullet renderer 注册时机错误（需在 `preInit()`）
  2. `EntityThrowable` 客户端 owner 丢失，需要同步 `shooterEntityId`

### tracer 长链 reopen 的最终结论

tracer 曾经历多轮 reopen，当前应记住的是**最终公共结论**，而不是每一轮的临时猜测：

1. ammo 主 display 被错误拿来当 projectile entity 的问题已修复
2. muzzle offset 的 FOV 空间换算已修复
3. tracer 几何已从“简陋交叉 quad”回到更接近上游的体积表现
4. 客户端 bullet 朝向同步、帧间旋转插值、速度同步与 spawn data 已收口
5. 第一人称 muzzle offset 的缓存时机已改到 `model.render(stack)` 之后
6. 第一人称 camera yaw 语义已归一化，不再在斜视角下落到错误象限
7. hand bobbing 已对齐上游语义，不再让 vanilla bob 额外扰动枪口诊断
8. 目前 tracer 的几何起点已基本与 hand-render 枪口对齐；后续若仍主观感觉异常，更应先检查视觉显著性、pack 自身表现或截图时机，而不是重新大改枪口矩阵链

### 第一人称 Bloom 时序

结论已经收口为：

- 世界 / 第三人称 Bloom 继续沿用 GT callback
- 第一人称 Bloom 已从 callback 中拆出，改为在 hand 阶段内联执行
- base gun 与 bloom 现在共享同一份 hand render 上下文
- AA12 当前如果“看起来不够亮”，更应归因于资产与阈值，而不是链路失效

## 当前 Render 剩余缺口（2026-03-10 审计）

## 2026-03-12 / Shell ejection parity 已收口

本轮已完成 `shell_ejection` 运行时链路迁移，Legacy 不再停留在“JSON 可解析、运行时不抛壳”的状态。

### 已落地内容

- ammo display 恢复 `shell` 资产解析：`AmmoDisplay.shellDisplay` + `ShellDisplay`
- `TACZClientAssetManager` 现会把 ammo shell model / texture 纳入 load plan，并缓存 `ShellRenderAsset`
- `GunDisplayInstance` 现会把 `ShellEjection` 与 LOD shell 信息绑定到 `BedrockGunModel`
- `BedrockGunModel` 现会缓存 `shell_*` origin bone，并为每个窗口创建 `ShellRender`
- `GunAnimationStateContext.popShellFrom(int)` 已真实入队 shell，而非空实现
- 第一人称渲染阶段已切换 `ShellRender.isSelf`，shell runtime 会实际消费速度 / 加速度 / 角速度 / 生命周期配置

### 2026-03-12 验证结论

单测：

- `GunAnimationStateContextLuaExposureTest`
  - `default state machine shoot transition invokes popShellFrom`
  - `fn evolys shoot transition invokes popShellFrom`

focused smoke：

- 目标枪：`tacz:fn_evolys`
- 关键日志：
  - `SHELL_POP`
  - `SHOOT_ANIMATION_TRIGGER ... triggered=true ... smInitialized=true`
  - `SHELL_VISIBLE`
  - `PASS animation=true projectile=true explosion=true regularGun=tacz:fn_evolys explosiveGun=skipped`
- 归档截图：
  - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-193820/01-shell_visible.png`
  - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-193820/02-shell_visible_settled.png`

截图说明：

- `01-shell_visible.png`：可见第一人称持枪与运行时场景，截图上半部有黑边，但游戏窗口内容有效
- `02-shell_visible_settled.png`：可见 ejection port 右侧抛出的黄铜弹壳，足以作为视觉验证主证据

### focused smoke 额外说明

默认脚本的 `inspect` 状态在收到 `INPUT_SHOOT` 时会先打断检视并返回 `idle`，**不会在同一次输入里调用 `popShellFrom()`**。

因此，若目标是专门验证“第一枪射击抛壳”，focused smoke 需要允许跳过 inspect。当前仓库已补充 `FOCUSED_SMOKE_SKIP_INSPECT=true` 开关作为视觉验收辅助；这属于**验证路径修正**，不是 gameplay 行为改动。

## 2026-03-12 / Laser beam parity 已收口

本轮已把上游 `laser_beam` 运行时链路迁回 Legacy，状态不再是“颜色可编辑但束体不真正渲染”。

### 已落地内容

- 新增结构化 `LaserConfig`，并把 gun / attachment display 的 `laser` 字段接到 Legacy runtime：
   - `GunDisplay` / `AttachmentDisplay`
   - `GunDisplayInstance` / `ClientAttachmentIndex`
- 新增 `LaserColorUtil`，恢复上游优先级：
   - item NBT 自定义颜色
   - display `laser.default_color`
   - 红色 fallback
- 新增 `BeamRenderer`，以 1.12.2 `Tessellator + POSITION_TEX_COLOR + additive/fullbright` 路径真正绘制 `textures/entity/beam.png`
- `BedrockGunModel` 已接 `laser_beam`
- `BedrockAttachmentModel` 已接 `laser_beam(_n)`
- beam 现在只在真实手持上下文渲染；`GUI / GROUND / FIXED / HEAD` 这类物品展示上下文会显式抑制
- focused smoke / refit 辅助已补：
   - `GunRefitScreen.triggerFocusedSmokeAdjustLaserPreview()`
   - `FocusedSmokeClientHooks` 自动改色 marker：`LASER_COLOR_PREVIEW`
   - laser 槽优先选择“可编辑且 beam 更长”的候选附件，避免 smoke 随机装到不可编辑或过短 beam 的第三方附件

### 2026-03-12 单测 / 编译结论

- 定向 Gradle 测试通过：
   - `GunDisplayParsingTest`
   - `AttachmentDisplayParsingTest`
   - `RefitAttachmentAccessorParityTest`
   - `LaserColorUtilTest`
- `compileJava compileKotlin` 已在补 smoke helper 后再次通过

### 2026-03-12 focused smoke 结论

#### 枪体 laser（`tacz:minigun`）

- focused smoke 日志：
   - `build/smoke-tests/runclient-focused-smoke-20260312-220419.log`
   - `build/smoke-tests/runclient-focused-smoke-20260312-220630.log`
- 关键 marker：
   - `LASER_BEAM_RENDERED item=tacz:minigun context=first_person path=laser_beam color=0x00FF00 length=10.000 width=0.0080 fadeOut=true`
   - `ANIMATION_OBSERVED ... gun=tacz:minigun ...`
   - `PASS ... regularGun=tacz:minigun ...`
- 归档截图：
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-220419/01-minigun_beam.png`
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-220630/01-minigun_ground_beam_early.png`
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-220630/02-minigun_ground_beam_settled.png`
- 截图说明：
   - 这些帧能确认第一人称 `minigun` 真实进入世界并走到 gun-body laser runtime；但由于该 gun-pack 自身配置为 `length=10`、`width=0.008`，束体在夜间平地场景里极细，截图中**不够清晰到可作为纯视觉主证据**。
   - 因此，对 gun-body laser 的结论应表述为：**真实 runtime marker 已命中，截图只证明场景与持枪上下文，束体本身在该样本上视觉可分辨度不足。**

#### 附件 laser + 改色闭环（`tacz:m4a1` + `tacz:laser_lopro`）

- focused smoke 日志：
   - `build/smoke-tests/runclient-focused-smoke-20260312-221341.log`
   - `build/smoke-tests/runclient-focused-smoke-20260312-221509.log`
- 关键 marker：
   - `REFIT_ATTACHMENT_APPLIED gun=tacz:m4a1 attachment=tacz:laser_lopro`
   - `LASER_COLOR_PREVIEW gun=tacz:m4a1 attachment=tacz:laser_lopro color=0x00FFFF`
   - `LASER_BEAM_RENDERED item=tacz:laser_lopro ... color=0xFF0000 length=75.000 ...`
   - `LASER_BEAM_RENDERED item=tacz:laser_lopro ... color=0x00FFFF length=75.000 ...`
   - `REGULAR_SHOT_SENT gun=tacz:m4a1`
   - `PASS ... regularGun=tacz:m4a1 ...`
- 归档截图：
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-221341/01-laser_preview.png`
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-221341/02-laser_beam_runtime.png`
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-221341/03-laser_postshot.png`
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-221509/01-laser_preview_gui.png`
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-221509/02-laser_preview_gui_settled.png`
- 截图说明：
   - `01-laser_preview_gui.png`：可见 `GunRefitScreen` 已打开并停留在 laser 槽位的 refit 预览场景；这是 GUI preview 链路的截图证据。
   - `02-laser_preview_gui_settled.png`：截图落在 pause 菜单，不应作为 preview 主证据。
   - `02-laser_beam_runtime.png` / `03-laser_postshot.png`：第一人称 `m4a1 + laser_lopro` 场景可见枪口前方的青色小段 / 青色亮点，与 marker 中 `color=0x00FFFF` 对应；虽仍受 1.12 截图压缩与束体细度影响，但**已比 gun-body `minigun` 样本更能肉眼分辨 beam 的存在与颜色变化。**

#### 2026-03-12 23:00 / 1.12 可见性兼容收口

在前述 parity 接线完成后，Legacy 仍暴露出一个 **1.12 固定管线特有的视觉问题**：marker 已确认 beam 真正进入第一人称渲染，但超细束体在截图中往往只剩枪口亮点或远端落点，不足以作为清晰视觉证据。

本轮最终收口的不是数据链或 attachment pose，而是 `BeamRenderer` 的手持视角可见性兼容层：

- 手持视角最小渲染宽度提升到 `0.03`
- 保留原始 textured beam quad 的同时，叠加更明显的 additive core line
- core line 调整为 `width=3.5`，且在 `fadeOut=true` 时不再把末端 alpha 直接打成 `0`

最终 focused smoke：

- 日志：`build/smoke-tests/runclient-focused-smoke-20260312-225938.log`
- 关键 marker：
   - `LASER_BEAM_RENDERED item=tacz:laser_lopro context=first_person path=laser_beam color=0x00FFFF length=75.000 width=0.0300 fadeOut=true`
   - `LASER_BEAM_SCREEN item=tacz:laser_lopro ... start=(888.2,535.3,0.8803) end=(1727.4,720.2,1.0022) startPxWidth=25.697 endPxWidth=0.471`
   - `REFIT_ATTACHMENT_APPLIED gun=tacz:m4a1 attachment=tacz:laser_lopro`
   - `LASER_COLOR_PREVIEW gun=tacz:m4a1 attachment=tacz:laser_lopro color=0x00FFFF`
   - `PASS mode=refit_preview ... regularGun=tacz:m4a1 ...`
- 主证据截图：
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-225938/01-laser_runtime.png`
   - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260312-225938/02-laser_runtime_settled.png`

最终截图结论：

- 两帧都能在 `m4a1` 枪口前方稳定看到**清晰的青色 laser segment**，不再只是“存在 marker 但肉眼难辨”的状态。
- 这说明 Legacy 当前已经从“逻辑上渲染 beam”收口到“第一人称视觉上也能可靠看见 beam”。

结论：

- Legacy 现在不只是“UI 能改激光颜色”，而是 **refit preview → NBT/color 应用 → 第一人称真实 beam render** 全链路已打通。
- 1.12 可见性补偿收口后，当前更大的视觉剩余问题重新回到 optic / stencil / gun-specific runtime display，而不是 laser beam 本体。

## 2026-03-12 / `trisdyna:fl3e` glTF 骨骼混乱已按上游语义修正

本轮处理的不是“模型资源坏掉”，而是 **glTF 动画数据进入 Legacy runtime 时的坐标 / 四元数转换语义偏离上游**。

### 目标资源与真值来源

- 用户指定枪包：`run/tacz/[Tacz1.1.5+]TRIS-dyna GunsPack ver1.1.5.zip.zip`
- 目标 display：`assets/trisdyna/display/guns/fl3e_display.json`
   - 其中明确声明：`"animation": "trisdyna:fl3e"`
- 目标 glTF 动画：`assets/trisdyna/animations/fl3e.gltf`
- 上游行为真值：`TACZ/src/main/java/com/tacz/guns/api/client/animation/Animations.java`

结论：`fl3e` 的枪模仍是 bedrock gun model，但动画控制器走的是 `.gltf` 动画消费链，因此这次问题应归属 **`createControllerFromGltf(...)` 语义偏差**，不是 `GltfAnimationParser` 本体或 bedrock 模型渲染器本体。

### 本轮收口内容

- `Animations.createControllerFromGltf(...)` 已恢复上游同等语义：
   - rotation `LINEAR` 通道改用 `SLERP`
   - listener 初始旋转值先转四元数并取逆，再参与 glTF keyframe 合成
   - glTF 原始 quaternion 会先经过 Legacy 轴向 / 符号重映射，再乘上初始逆值
   - translation 不再按“减 glTF node default”处理，而是按 listener 初始值空间转换
- 新增 / 更新 `GltfAnimationControllerTest`：
   - 覆盖 translation → Legacy listener space 的结果
   - 覆盖 rotation → quaternion remap + `SLERP` 的结果

### 2026-03-12 验证结论

静态 / 编译：

- `Animations.java`、`GltfAnimationControllerTest.kt` 的 editor diagnostics 均为 `No errors found`
- 因当前工作区存在其他未收口改动，定向 `gradlew test --tests "*GltfAnimationControllerTest"` 仍会被**无关的 `compileJava` 错误**挡住；这些错误来自其他 WIP 文件，不属于本轮 glTF 修复本体
- 为避免被无关编译噪声阻断，本轮已用映射后的 1.12.2 `renameOutput.jar` 对 `Animations.java` 做手工 `javac` 验证，结果成功

真实运行链：

- 目标枪：`trisdyna:fl3e`
- focused smoke 旁路日志：
   - `build/smoke-tests/manual-fl3e-gltf-smoke-20260312-220726.log`
   - `build/smoke-tests/manual-fl3e-gltf-smoke-20260312-221003-shot.log`
- 关键 marker：
   - `REGULAR_OVERRIDE gun=trisdyna:fl3e`
   - `SERVER_GEAR_READY regularGun=trisdyna:fl3e explosiveGun=none attachment=none`
   - `ANIMATION_OBSERVED gun=trisdyna:fl3e display=trisdyna:fl3e_display ... smInitialized=true ...`
   - `PASS mode=animation_only ... regularGun=trisdyna:fl3e ...`
   - 后续同轮也持续命中 `SHOOT_ANIMATION_TRIGGER ... triggered=true ... smInitialized=true`

视觉取证：

- 已尝试抓取：
   - `build/smoke-tests/display1-probe.png`
   - `build/smoke-tests/fl3e-capture-01.png`
   - `build/smoke-tests/fl3e-capture-02.png`
   - `build/smoke-tests/fl3e-capture-03.png`
- 但当前终端启动环境下，这些帧全部呈现为**黑底 / 仅鼠标指针可见**，不构成有效视觉主证据
- 因此，本轮可以确认：**`fl3e` 已真实走到修复后的 glTF 动画运行链**；但若要补齐“肉眼可见的最终截图证据”，仍需在可见窗口环境中重跑 smoke

这部分是当前真正需要继续投递 Agent 的内容：

1. **scope / optic parity（2026-03-13 主链已收口）**
    - `BedrockAttachmentModel` 已恢复 `scope_body` / `ocular_ring` / `ocular*` / `division` runtime，并在第一人称按 `scope` / `sight` / `both` 三条路径执行 stencil-style optic 渲染。
    - 已新增 `client/util/RenderHelper.java` 处理 1.12.2 framebuffer stencil enable / state restore，`BeamRenderer` render context 也已接入 optic runtime，避免第三人称/GUI 误走 stencil 分支。
    - `FirstPersonFovHooks` + `EntityRendererMixin` 已区分 world FOV 与 item-model FOV；`MathUtil.magnificationToFov(...)`、attachment `viewsFov` 与 gun `zoomModelFov` 都已接入真实运行链。
    - `FirstPersonRenderGunEvent` 已补齐 `scope_view_N` 切换平滑、旧/新 aiming matrix 插值与 tracer FOV 缓存读取。
    - focused smoke 现已补齐 `FOCUSED_SMOKE_AUTO_ADS`、`FOCUSED_SMOKE_PASS_AFTER_ADS` 与 `FOCUSED_SMOKE_REFIT_ATTACHMENT`，并完成三组 real-path 验证：
       - `tacz:p90`：`OPTIC_STENCIL_RENDERED attachment=tacz:sight_p90 mode=sight` + `ADS_READY ... aimingProgress=1.000` + `PASS mode=ads_only`
       - `tacz:aug`：`OPTIC_STENCIL_RENDERED attachment=tacz:scope_aug_default mode=scope` + `ADS_READY ... aimingProgress=1.000` + `PASS mode=ads_only`
       - `tacz:scar_h + tacz:scope_standard_8x`：`REFIT_ATTACHMENT_APPLIED ... scope_standard_8x` + `OPTIC_STENCIL_RENDERED ... mode=scope` + `ADS_READY ... aimingPath=...>views>scope_view` + `PASS mode=ads_only`
    - 截图归档：
       - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-095621/01-ads_ready.png`
       - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-095722/01-ads_ready.png`
       - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-100009/01-ads_ready.png`
    - 结论：scope / sight / refit 倍镜的自动 ADS 补证已经完成；后续若仍有 reopen，重点应放在个别枪包的视觉居中/ocular aperture polish，而不是 optic 主运行时缺失。
2. **gun-specific runtime display / material layer**
   - 2026-03-12：已补齐 `GunDisplayInstance` → `BedrockGunModel` 的 `text_show` runtime 接线与 `TextShow.colorInt` 解析；`AttachmentDisplay` / `ClientAttachmentIndex` / `BedrockAttachmentModel` 也已恢复 attachment `text_show` 注入链路
   - 2026-03-12：`TACZGuiModelPreviewRenderer` 已补 `renderBloom()` 捕获，GUI preview 不再绕开 bloom bridge 走旧 fallback
   - 2026-03-12：`PapiManager` 已改为上游同等的“原始翻译文本 / 无翻译则原样返回”语义，不再对 `%ammo_count%`、`%player_name%` 这类 placeholder 先做 `I18n.format(...)`，`trisdyna:iras` 枪身小屏此前出现的 `Format error` 已收口
   - 以 `trisdyna:iras` 为例，focused smoke 已命中 `FIRST_PERSON_BLOOM_RENDERED`、`ANIMATION_OBSERVED`、`REFIT_SCREEN_OPEN`、`REFIT_ATTACHMENT_APPLIED`；截图可见枪身读数/文字与蓝绿色 emissive 亮部
   - 仍未完成：更复杂的非文字 material/runtime 节点、optic/stencil 级 preview 语义，以及 `refit focus positioning lerp produced non-finite output` 这类预览定位问题
3. **验证补证而非功能缺失**
   - `HUD-under-GUI`
   - `camera recoil`
   - `tracer default-speed`
   这些更偏补充证据，不再单拆新 Prompt

## 最新对比测试回归分诊

当前剩余问题已经不适合让一个 Agent 全包，建议继续使用同一个 `TACZ Migration` Agent，但**按 Prompt 拆任务**。

| 用户可见症状 | 推荐 Prompt | 归属说明 |
|---|---|---|
| 个别枪仍有基础持枪/瞄准构图偏差、ADS/后坐力/镜头反馈细节不一致 | `.github/prompts/tacz-stage-render-animation-first-person.prompt.md` | 第一人称 pose / animation runtime / render-frame 插值 / fire feedback |
| 某些枪模型本应显示的数字/字模/能量读数缺失，或 gun-specific runtime/material 节点没被消费 | `.github/prompts/tacz-stage-render-material-parity.prompt.md` | gun-specific model runtime / material / model text layer parity |
| 武器完全没音效，需要回答“没对接”还是“实现有问题” | `.github/prompts/tacz-stage-audio-engine-compat.prompt.md` | 由音频 Agent 负责 runtime/backend/真实播放验证 |
| `GunRefitScreen` 的沉浸式 world-to-screen 过渡、screen composition 与交互体验仍与上游有明显差距 | `.github/prompts/tacz-stage-client-ux-gui-i18n.prompt.md` | GUI / preview transition / 交互体验 parity |
| 爆发模式没有冷却、打成错误射速 | `.github/prompts/tacz-migrate-combat-network.prompt.md` | fire-mode / cadence / server accept gate 真值 |
| 任一 Agent 被共享 hook / smoke / 注册问题挡住 | `.github/prompts/tacz-stage-foundation-client-hooks.prompt.md` | Foundation 只负责共享接线与验证守门 |

## 2026-03-10 非兼容专项 Prompt

为了避免继续把 shell / optic / laser / heat cadence / refit polish / runtime display 炖成一锅，本轮补充了 6 个更窄的专项 Prompt：

| 症状 / 目标 | Prompt 文件 | 适用说明 |
|---|---|---|
| 抛壳 JSON 能解析，但运行时完全不抛壳 | `.github/prompts/tacz-stage-render-shell-ejection.prompt.md` | 专门处理 `shell_ejection` runtime、`ShellRender`、抛壳窗 index 与模型锚点 |
| 长筒镜 / 红点 optics reopen / ADS 补证 | `.github/prompts/tacz-stage-render-scope-optic-parity.prompt.md` | 主链与 auto ADS 验证已收口；后续主要处理个别 gun-pack reopen、倍率镜视觉居中或残余 ocular/division polish |
| 激光束体 / 颜色链路 parity（2026-03-12 已收口，现主要保留作历史专项 Prompt） | `.github/prompts/tacz-stage-render-laser-beam.prompt.md` | 本轮已完成 gun / attachment beam renderer、颜色流与 focused smoke 验证；默认不再作为当前主缺口 |
| heat 对散布已生效，但 `min_rpm_mod / max_rpm_mod` 仍未接入真实射击 cadence | `.github/prompts/tacz-stage-combat-heat-rpm-cadence.prompt.md` | 专门把 heat→RPM modifier 接入 shoot interval / cadence |
| `GunRefitScreen` 已有稳定布局，但缺少上游沉浸式开场、遮罩、焦点过渡与视觉 polish | `.github/prompts/tacz-stage-client-ux-refit-immersive-polish.prompt.md` | 在当前稳定基线之上继续补 opening / mask / composition / focus polish |
| 枪身数字 / 读数 / emissive / 非文字 runtime display 仍有缺口 | `.github/prompts/tacz-stage-render-gun-runtime-display.prompt.md` | 专门处理 gun-specific runtime display / material layer parity |

补充：JEI / KubeJS 继续留在 compat 轨道，不纳入本轮专项 Prompt 拆分。

## 推荐迭代顺序

1. **Render Material / Runtime Display**
   - 先解决“看得见但不对”的贴图 / item / block / runtime display 问题
2. **Render Animation / First-Person**
   - 再收动画 runtime、hand/scope 链路、ADS 插值、枪焰/镜头/后坐力
3. **Audio Agent**
   - 当问题已经上升到 runtime/backend 层时，单独切给音频 Prompt
4. **Client UX Agent**
   - 在 backend 已接通的前提下继续补 `GunRefitScreen` 沉浸式体验
5. **Combat Agent**
   - 收 cadence / damage / explosion / heat cadence 等玩法真值
6. **Foundation Agent**
   - 只在共享基础问题挡住前面几条线时介入

## 并行协作建议

- **渲染 Agent**：优先持有 `client/resource/**`、`client/model/**`、`client/renderer/**`、必要的 `api/client/**` 与 `mixins.tacz.json`
- **音频 Agent**：优先持有 `client/sound/**`、新增 `client/audio/**`、`TACZClientAssetManager.kt` 的音频 manifest/probe/backend 接线
- **Client UX Agent**：优先持有 `client/gui/**`、`client/event/**`、`LegacyRuntimeTooltipSupport.kt`、`TACZGunPackPresentation.kt`、`assets/tacz/lang/**`
- **Foundation Agent**：优先持有 `ClientProxy.kt`、`CommonProxy.kt`、`mixins.tacz.json`、smoke / diagnostic 脚本与注册文件
- 多 Agent 若都想改 `ClientProxy.kt`、`mixins.tacz.json`、`TACZClientAssetManager.kt`，必须先由协调 Agent 明确文件所有权

## 实际使用建议

### 标准工作流

1. 先运行系统侦察 Prompt，确认上游真值源、Legacy 落点、风险点与先后顺序
2. 再运行对应迁移 Prompt，让 `TACZ Migration` 执行完整迁移
3. 若需求跨两个系统，优先拆成两个 Prompt，由协调 Agent 控制先后顺序
4. 若多人或多 Agent 并行，先划清文件边界，再执行构建与验证

### 什么时候值得继续拆更多 Agent 文件

只有在以下条件同时成立时才值得：

- 某条系统长期高频使用
- 它需要明显不同的工具限制或输出格式
- 这些差异无法仅靠 Prompt 表达

当前最可能值得以后单独拆 custom agent 的仍然只有两条：

- 渲染 / 动画 / 客户端资源
- 数据 / 枪包兼容

## 2026-03-08 紧急补充：欺骗性交付防护

在首轮拆分任务后，曾出现 Agent 报告“已完成”，但实机结果仍然明显错误的情况。当前仓库已形成更严格的 reject 规则：

1. focused smoke 现在会主动尝试 inspect，并在关键时刻抓图
2. 若截图中的枪模仍停在原版位置，或者日志没有真实音效 / 粒子 / 动画调用证据，则不能以“PASS”自证完成
3. Render / Audio 相关任务必须优先以截图与运行 marker 为准，不能只拿编译成功或单测成功充当结论

## 交接建议

- Render 轨道后续交接时，优先引用本分册 + 对应专项 Prompt
- 交付规范、阶段报告模板与 handoff 规则请统一查看 `docs/TACZ_AGENT_WORKFLOW.md`
- 若后续 render reopen 再次变长，请新增新的分类分册，而不是继续把本文件膨胀成第二本巨著
