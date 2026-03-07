---
name: "TACZ Stage Render Material Parity"
description: "Fix TACZ-Legacy gun-mounted attachment rendering, optics, and gun model material/runtime parity."
agent: "TACZ Migration"
argument-hint: "填写枪械贴图、ammo/attachment 物品贴图、特殊方块模型等问题、上游文件或验收标准"
---
迁移并修复 `TACZ` 的**挂枪配件渲染 / 瞄具光学效果 / 枪模运行时模型类型与材质表现链**到 `TACZ-Legacy`。

## 新一轮对比测试反馈（必须覆盖）

1. **某些枪模在上游带有内建数字/字模/能量读数等模型文字显示，但 Legacy 目前没有。**
   - 这不是单纯 HUD 文案问题，而是 gun runtime / material / model text layer 没有被真实消费。
   - 本轮必须检查：这些数字/字模是来自模型骨骼上的独立 mesh、贴图帧、emissive 层，还是运行时文本渲染节点；然后按上游语义恢复，而不是拿 GUI 叠字糊上去。
2. **如果整体枪模尺寸差异的根因来自 gun-specific runtime / display node / view node 没有被正确消费，而不是第一人称 pose 插值问题，这条 Prompt 也要接住。**
   - 若确认问题属于 base viewmodel pose / ADS 插值 / recoil，则移交给 render-animation-first-person Prompt；不要两个 Prompt 抢同一种偏差。

## 当前危机与必须修复的严重 BUG （本轮重点）
1. **HUD 长枪图这条链已经修过了，本轮不要再回头重做 HUD。** 现在真正炸的是：**枪上的配件根本没正确挂到枪模上**，用户在第一人称里几乎看不到 scope / muzzle / grip / stock 到底渲染在哪。
2. Legacy 当前 active path 很可能压根没有“挂枪配件渲染链”，而只有：
   - 枪本体渲染：`FirstPersonRenderGunEvent.kt` / `TACZGunItemRenderer`
   - 配件物品自己的渲染：`TACZAttachmentItemRenderer.kt`
   - GUI 单独预览配件：`TACZGuiModelPreviewRenderer.kt` / `GunRefitScreen.kt`
   - `GunDisplayInstance.checkTextureAndModel()` 目前创建的是**通用** `BedrockAnimatedModel`，只注册了左右手 functional renderer，并没有上游 `BedrockGunModel` 那套 scope/muzzle/grip/stock/runtime attachment wiring。
3. 这条线要对齐的不是“再多写几个 if”，而是上游整套**枪专用模型运行时**：
   - `GunDisplayInstance.checkTextureAndModel()` 使用 gun-specific model runtime
   - `BedrockGunModel`
   - `BedrockAttachmentModel`
   - `AttachmentRender`
   - `ClientAttachmentIndex`
   - `scope_pos` / `muzzle_pos` / `grip_pos` / `stock_pos`
   - `attachment_adapter` / `*_default`
   - `iron_view` / `scope_view` / stencil/optic 相关渲染语义
4. 枪包资源本身已经带着这些节点与字段：`scope_pos`、`muzzle_pos`、`attachment_adapter`、`scope_view`、`adapterNodeName`、`showMuzzle`、`scope/sight/zoom` 等。**本轮任务是让运行时真正消费这些数据并把配件挂到枪上**，而不是继续只渲“配件物品自身”。

## 默认关注范围
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/client/model/**`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/attachment/**`
- `src/main/kotlin/com/tacz/legacy/client/renderer/item/TACZGunItemRenderer.kt`
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/TACZGuiModelPreviewRenderer.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`

## 执行要求
- **优先移植上游 gun-specific runtime 设计**，不要把配件渲染硬塞成 `FirstPersonRenderGunEvent` 里的临时补丁。
- 复用现有枪包/资源字段，不要新造一套 attachment slot 命名或第二套缓存。
- 至少解决以下配件挂载可见性：`scope`、`muzzle`、`grip`、`stock`；并正确处理 `attachment_adapter` / `*_default` 的显隐语义。
- 若枪模内建数字/字模/能量面板依赖 gun-specific material/runtime layer，也由本 Prompt 负责补齐；不要让它继续落成“上游枪上有读数，Legacy 只有一块空壳”。
- 如果本轮无法一次性完备实现长筒镜 stencil 细节，也必须先把**挂点、朝向、缩放、可见性**修正确；不要交“完全看不见配件但代码结构更漂亮”的答案。
- 不要回滚或破坏已经修好的 HUD 图标链；HUD 不是本轮主目标。
- 不要顺手把第三人称 primitive/gecko 风味模型大修掉，除非那是挂枪配件正确显示的必要前置。

## 必验场景
- 至少一把装了瞄具的枪：scope 真实挂到枪上，位置不漂、不埋模，并且 `iron_view/scope_view` 相关定位不再明显错乱。
- 至少一把装了 `muzzle` 或 `grip` 或 `stock` 的枪：配件真实可见，`*_default` 逻辑没有反着显示。
- 至少验证一个 `attachment_adapter` 场景，证明需要转接口时不会整段消失。

## 输出必须包含
- 上游真值源文件（特别是 `GunDisplayInstance`、`BedrockGunModel`、`BedrockAttachmentModel`、`AttachmentRender`、`ClientAttachmentIndex` 相关实现）。
- Legacy 落点文件。
- 明确说明“挂枪配件渲染链”最终是怎么接通的：从枪当前 attachment item / display 数据，到模型骨骼/functional renderer，再到实际绘制。
- 若本轮修到了模型字模 / 数字显示，必须明确说明它最终走的是哪条 runtime/material/path，以及为什么之前 Legacy 完全不显示。
- 运行验证：证明配件不再“完全没看到渲染在哪”，并说明 scope / muzzle / grip / stock 中至少哪些已被验证。
