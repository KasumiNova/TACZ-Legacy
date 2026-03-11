---
name: "TACZ Stage Client UX Refit Immersive Polish"
description: "Polish TACZ-Legacy GunRefitScreen toward upstream immersive world-to-screen behavior without regressing the stable baseline."
agent: "TACZ Migration"
argument-hint: "填写改装界面缺少沉浸式过渡、遮罩或构图 polish 的症状与验收要求"
---
在 **不破坏当前稳定布局基线** 的前提下，把 `TACZ-Legacy` 的 `GunRefitScreen` 继续推近上游 `TACZ` 的沉浸式 world-to-screen 开场、遮罩、构图与交互体验。

## 当前已知基线（不要误判）
- 当前 Legacy 并不是“改装界面完全不可用”，而是：
  - 旧版稳定布局可用
  - `refit_view` / `refit_<type>_view` 节点驱动的小视口预览已接上
  - 但离上游沉浸式 world-to-screen 开场、遮罩、焦点流转与视觉 polish 仍有明显差距
- 本轮目标不是再来一次“大重写 UI”，而是基于当前可用基线，逐步补体验差距。

## 上游真值源
- `TACZ/src/main/java/com/tacz/guns/client/gui/GunRefitScreen.java`
- `TACZ/src/main/java/com/tacz/guns/client/animation/screen/RefitTransform.java`
- `TACZ/src/main/java/com/tacz/guns/client/gui/components/refit/**`
- Legacy 历史稳定参考：`89fc159` 与 `.agent-workspace/old-gui-reference/`

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`
- `src/main/kotlin/com/tacz/legacy/client/animation/screen/RefitTransform.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/TACZGuiModelPreviewRenderer.kt`
- `src/main/java/com/tacz/legacy/client/model/BedrockGunModel.java`
- 若 opening/preview 需要与第一人称过渡共享数据，可只做最小必要接线，不要顺手重写 first-person 主链

## 任务目标
1. 让改装界面从“稳定的小视口预览”继续走向“沉浸式 world-to-screen 过渡”。
2. 补 opening / mask / composition / focus transition / hover polish，而不是只修 backend。
3. 保持现有：
   - 候选附件列表
   - 创造模式候选
   - 激光颜色编辑
   - property diagram
   的可用性，不要为了动画效果把主功能打坏。
4. 若某一部分暂时做不到上游完整观感，也要先保证画面构图、聚焦、slot 切换与预览枪模不会突兀或错位。

## 执行要求
- 以“当前稳定基线 + 上游沉浸式体验”做增量推进，不要从零重构排版系统。
- 不要把本 Prompt 偷换成“修所有 GUI 文案 / I18n”；语言清理仅限本轮体验所需。
- 不要顺手改 combat / network / gun data backend；那不是本 Prompt 的主责。
- 若需要复用旧参考布局，请说明哪些部分是回退基线，哪些部分是新增沉浸式 polish。

## 必验场景
- 打开 refit screen：枪模过渡到预览区域的观感比当前更自然，不再像突然切换小窗。
- 切换 attachment type：焦点与预览响应自然，不出现明显抖动/错位。
- 至少一次 slot focus / 候选 hover / 候选应用后的截图对比，证明不是“界面还能开但视觉没改”。

## 输出必须包含
- 上游真值文件
- 当前 Legacy 采用的稳定基线说明
- 本轮补到的 opening / mask / composition / focus polish 项目
- 运行验证与仍待后续 polish 的剩余差距
