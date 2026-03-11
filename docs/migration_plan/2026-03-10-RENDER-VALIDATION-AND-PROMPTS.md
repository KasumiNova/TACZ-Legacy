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

这部分是当前真正需要继续投递 Agent 的内容：

1. **shell ejection**
   - `popShellFrom()` 仍是空实现
   - 缺 `ShellRender` 级别 runtime
2. **scope / optic parity**
   - `BedrockAttachmentModel` 仍是 minimal port
   - `division` / `ocular*` / stencil 语义未完整恢复
3. **laser beam runtime**
   - 激光颜色可改，但 `laser_beam` 束体未真正渲染
4. **gun-specific runtime display / material layer**
   - 文字链虽然落地，但数字、读数、emissive、非纯文字 runtime display 仍有缺口
5. **验证补证而非功能缺失**
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
| 长筒镜 / 红点 optics 仍是最小接线版 | `.github/prompts/tacz-stage-render-scope-optic-parity.prompt.md` | 专门处理 `BedrockAttachmentModel` 的 optic runtime |
| 激光颜色可编辑，但 `laser_beam` 束体仍未真实渲染 | `.github/prompts/tacz-stage-render-laser-beam.prompt.md` | 专门补 gun / attachment 的 beam renderer、颜色流与配置开关 |
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
