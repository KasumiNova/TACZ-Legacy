---
name: "TACZ Stage Client UX GUI & I18n"
description: "Build the complete interactive Gun Refit Screen and Polish Client GUI/I18n."
agent: "TACZ Migration"
argument-hint: "填写具体缺少的 GUI 面板（如改装台）、本地化遗漏、creative tab 表现等等。"
---
迁移并修复 `TACZ` 的**客户端交互层、GunSmithTable 屏幕、沉浸式枪械改装界面 (GunRefitScreen) 及本地化表现**。

## 当前危机与必须修复的严重 BUG （本轮重点）
1. **枪械的沉浸式改装界面依然是严重的半成品**：
   - 现有的 `GunSmithTableScreen` 虽然实现了基本的容器界面（可能还非常丑陋或缺失了交互材质）。
   - 或者更严重的：真正的配件改装面板 `GunRefitScreen` 根本就不存在，或者只有布局外壳，不能真实装备、卸下配件，也不能直观地预览 3D 枪模！
   **任务**：你需要参考上游 `TACZ` 的沉浸式配件装卸逻辑，在改装台内构建完整的附件管理 GUI，支持点击槽位，发包到服务器真正应用/卸下附件！
2. **用户现在明确反馈：Legacy 的沉浸式改装 GUI 和上游“完全是两个东西”。**
   - 现状不只是“按钮少几个”，而是屏幕结构、预览方式、氛围层、信息层次、手中枪模过渡到屏幕内预览的方式都和上游不一致。
   - 本轮必须把“手上的枪模平滑过渡到屏幕上的沉浸式预览”视作核心验收之一；如果当前只是切到另一个完全独立的平面预览器，那还不算过关。
3. **不要把 `GunRefitScreen` 只做成一个功能后台。**
   - backend / 发包当然要通，但用户这轮反馈首先是在说“视觉交互体验根本不是同一个系统”。
   - 因此本 Prompt 需要同时对齐：screen 布局、预览 camera / model transition、信息区层级、交互反馈，而不仅仅是“能装配件”。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/gui/**` （重点排查或补齐 `GunRefitScreen` / `GunSmithTableScreen`）
- `src/main/kotlin/com/tacz/legacy/common/network/**` （增加配件安装卸载的发包逻辑）
- `src/main/kotlin/com/tacz/legacy/common/registry/LegacyCreativeTabs.kt` （完善标签分类）

## 执行要求
- 确保 `GunRefitScreen` 是可以直接使用枪包内 display 模型进行预览的。复用现有的渲染基座 `TACZClientAssetManager`，可以在 GUI 内起一个迷你 Viewport 渲染当前枪支和已经装上的配件。
- 确保有对应的选配件逻辑并发包给服务端。服务端负责把配件数据写入物品的 NBT 中。必须确保配件一旦装上，数据真实落地。
- 不要只是写一个摆设 UI！它的所有按钮（如果有准镜、枪口、消音器选择）必须具有真实副作用。
- 必须对齐上游沉浸式改装界面的**视觉组织与过渡语义**：枪模如何进入 screen 预览、预览视角如何稳定、按钮/列表/说明面板如何与模型预览形成一个整体，而不是几块松散控件。
- 如果涉及创造模式物品栏，确保其更精细，而不是只有一个大杂烩标签。

## 避坑指南
- 不要修改渲染库的核心加载逻辑。你的任务是“画出用户操作面板并且发包”。
- 如果没有发包机制，装配将仅局限于客户端，这会在进入世界/退出界面后立刻重置丢失！千万不要只写纯前端逻辑！

## 输出必须包含
- 具体的面板（Screen）代码落点。
- 配件装备的网络同步机制（C2S Packet）落地情况。
- 若本轮对齐了沉浸式预览 / 枪模过渡，必须明确说明：Legacy 之前为什么看起来像“另一个完全不同的 GUI”，这次又是如何缩小视觉差距的。
