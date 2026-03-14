# TACZ-Legacy 工作区整理与迭代检查点（2026-03-14）

## 目的

本文件用于在本地提交前，整理当前 `TACZ-Legacy` 工作区的**近期迭代记录**与**待入库变动范围**，避免一次 checkpoint commit 混入来源不明的修改。

## 当前工作区变动规模

- 已修改的 tracked 文件：`39`
- 新增的 untracked 文件：`14`
- 本地协作目录 `.agent-workspace/`：**不纳入 Git 提交**

## 本次待提交变动分组

### 1. Scope / Optic 主运行时与自动 ADS 补证

涉及文件（代表）：
- `.github/prompts/tacz-stage-render-scope-optic-parity.prompt.md`
- `docs/SCOPE_RENDER_MIGRATION_PLAN.md`
- `docs/AGENT_SCREENSHOT_WORKFLOW.md`
- `scripts/runclient_focused_smoke.sh`
- `src/main/java/com/tacz/legacy/client/model/BedrockAttachmentModel.java`
- `src/main/java/com/tacz/legacy/client/model/functional/AttachmentRender.java`
- `src/main/java/com/tacz/legacy/mixin/minecraft/client/EntityRendererMixin.java`
- `src/main/java/com/tacz/legacy/client/event/FirstPersonFovHooks.java`（新增）
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/foundation/FocusedSmokeClientHooks.kt`
- `src/main/kotlin/com/tacz/legacy/common/foundation/FocusedSmokeRuntime.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientPlayerGunBridge.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`
- `src/test/kotlin/com/tacz/legacy/client/event/FirstPersonFovHooksTest.kt`（新增）

主要内容：
- 把 optic 主运行时从“只认 `scope_view` 定位”扩展为真正消费 `scope_body` / `ocular*` / `division` / stencil-style 渲染。
- focused smoke 新增真实自动 ADS 验证链，支持 `FOCUSED_SMOKE_AUTO_ADS` / `FOCUSED_SMOKE_PASS_AFTER_ADS` / `FOCUSED_SMOKE_REFIT_ATTACHMENT`。
- 重新补齐 builtin sight、builtin scope、refit 倍镜三类样本的 `ADS_READY` + `OPTIC_STENCIL_RENDERED` + 截图证据。

### 2. Laser beam runtime parity 与改色闭环

涉及文件（代表）：
- `src/main/java/com/tacz/legacy/client/model/functional/BeamRenderer.java`（新增）
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/LaserConfig.java`（新增）
- `src/main/java/com/tacz/legacy/util/LaserColorUtil.java`（新增）
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/client/resource/index/ClientAttachmentIndex.java`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/gun/GunDisplay.java`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/attachment/AttachmentDisplay.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockGunModel.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockAttachmentModel.java`
- `src/main/kotlin/com/tacz/legacy/client/renderer/item/TACZGunItemRenderer.kt`
- `src/main/kotlin/com/tacz/legacy/client/resource/TACZClientAssetManager.kt`
- `src/main/kotlin/com/tacz/legacy/common/config/LegacyConfigManager.kt`
- `src/test/kotlin/com/tacz/legacy/util/LaserColorUtilTest.kt`（新增）

主要内容：
- 接通 gun / attachment `laser` 配置与 NBT 颜色流。
- 恢复 `laser_beam` 真实束体渲染，并限制在真正的手持上下文执行。
- 补齐 refit 改色 → 第一人称 beam 颜色同步变化的 smoke 验证。
- 进一步做 1.12 固定管线下的 beam 可见性兼容，避免“marker 命中但截图只剩亮点”。

### 3. Shell ejection 运行时收口

涉及文件（代表）：
- `src/main/java/com/tacz/legacy/client/model/functional/ShellRender.java`（新增）
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/ammo/ShellDisplay.java`（新增）
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/ammo/AmmoDisplay.java`
- `src/main/java/com/tacz/legacy/client/animation/statemachine/GunAnimationStateContext.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockGunModel.java`
- `src/test/kotlin/com/tacz/legacy/client/animation/statemachine/GunAnimationStateContextLuaExposureTest.kt`

主要内容：
- 让 `shell_ejection` 不再停留在“JSON 可解析，运行时不抛壳”的阶段。
- `popShellFrom(int)` 真实入队 shell runtime，而非空操作。
- focused smoke 已可对 `fn_evolys` 等样本打出 `SHELL_POP` / `SHELL_VISIBLE` 证据。

### 4. Gun runtime display / text_show / preview bloom

涉及文件（代表）：
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/client/resource/index/ClientAttachmentIndex.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockAttachmentModel.java`
- `src/main/java/com/tacz/legacy/client/model/papi/PapiManager.java`
- `src/main/kotlin/com/tacz/legacy/client/gui/TACZGuiModelPreviewRenderer.kt`
- `src/test/java/com/tacz/legacy/client/model/papi/PapiManagerTest.java`（新增）
- `src/test/kotlin/com/tacz/legacy/client/resource/GunDisplayInstanceTextShowTest.kt`（新增）
- `src/test/kotlin/com/tacz/legacy/client/resource/index/ClientAttachmentIndexTextShowTest.kt`（新增）

主要内容：
- 恢复 gun / attachment `text_show` 从 display → index → runtime model 的完整注入链。
- 修正 `TextShow.colorInt` 颜色解析与 `PapiManager` placeholder 处理。
- GUI preview 渲染后补抓 bloom，不再继续走旧 fallback。
- `trisdyna:iras` 的运行时读数、文字与 emissive 已重新具备 smoke 证据。

### 5. GUI / Refit 细节与 smoke 工具补强

涉及文件（代表）：
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/TACZGuiTextureUv.kt`（新增）
- `src/test/kotlin/com/tacz/legacy/client/gui/TACZGuiTextureUvTest.kt`（新增）
- `src/test/kotlin/com/tacz/legacy/common/item/RefitAttachmentAccessorParityTest.kt`
- `scripts/runclient_focused_smoke.sh`

主要内容：
- 修复 `GunRefitScreen` 配件按钮 UV 裁切错误、半透明槽位发白 / 白块 / 缺纹理问题。
- focused smoke 可按指定 attachment id 稳定安装目标 optic / laser，避免 GUI 当前页候选顺序干扰验证。
- 进一步巩固 refit preview、laser 改色与 auto ADS 的取证链。

### 6. glTF 动画语义修正

涉及文件：
- `src/main/java/com/tacz/legacy/api/client/animation/Animations.java`
- `src/test/kotlin/com/tacz/legacy/api/client/animation/GltfAnimationControllerTest.kt`

主要内容：
- 修正 `createControllerFromGltf(...)` 中 translation / quaternion / SLERP 的运行时转换语义。
- 让 `trisdyna:fl3e` 这类 glTF 动画样本回到更接近上游的骨骼变换语义。
- 当前日志已证明运行链真实进入修复后的 glTF controller；视觉截图仍需在可见窗口环境做最终补证。

### 7. 文档同步

涉及文件：
- `docs/migration_plan/2026-03-10-FOUNDATION-TO-CLIENT-UX.md`
- `docs/migration_plan/2026-03-10-RENDER-VALIDATION-AND-PROMPTS.md`
- `docs/SCOPE_RENDER_MIGRATION_PLAN.md`
- `docs/AGENT_SCREENSHOT_WORKFLOW.md`

主要内容：
- 同步记录 3/12–3/13 的 render runtime display、laser beam、shell ejection、scope/optic auto ADS 补证结论。
- 让后续 Agent 能直接从正式文档中读到当前 render 轨道的真实落地状态与 reopen 重点。

## 近期迭代记录（本地未入库主线）

这批待提交修改主要对应以下阶段记录：

1. `2026-03-12T20-08-22--client-ux-refit-buttons--tacz-migration--completed.md`
   - 修复 `GunRefitScreen` 配件按钮 UV 与半透明 slot 贴图问题。
2. `2026-03-12T20-09-19--render-runtime-display--tacz-migration--completed.md`
   - 接回 gun / attachment `text_show`、preview bloom 与 `trisdyna:iras` runtime display 验证。
3. `2026-03-12T22-16-00--render-validation--tacz-migration--completed.md`
   - 完成 laser beam runtime parity 接线与改色闭环。
4. `2026-03-12T23-02-00--render-validation--tacz-migration--completed.md`
   - 收口 1.12 下 laser beam 可见性不足的剩余问题。
5. `2026-03-12T22-20-00--render-animation--tacz-migration--partial.md`
   - 修正 `fl3e` glTF 动画变换语义，日志链已验证，视觉证据待可见窗口环境补充。
6. `2026-03-13T10-03-17--render-scope-optic--tacz-migration--completed.md`
   - scope / sight / refit 倍镜的 auto ADS focused smoke 补证完成。

补充基线：
- 最近已提交基线为 `fba0975 docs: split migration plan and add focused prompts`
- 本批工作区改动建立在该基线之上，聚焦 3/12–3/13 的 render / GUI / smoke / docs 收口

## 提交边界

建议本次提交纳入：
- `.github/prompts/**`
- `docs/**`
- `scripts/**`
- `src/main/**`
- `src/test/**`

明确不纳入：
- `.agent-workspace/**`
- `build/**`
- `logs/**`
- `run/**`
- focused smoke 截图与其他本地产物

## 本次校验结论

已执行：

- `./gradlew --no-daemon test --console=plain`

结果：

- `BUILD SUCCESSFUL`

说明：
- 当前合并后的工作树已能通过完整 `test` 任务，可作为本次本地 checkpoint commit 的提交前验证。

## 建议提交说明

这批改动横跨 render runtime、scope/laser/shell、GUI preview 与验证文档，建议使用 checkpoint 风格但保留功能指向，例如：

- `feat: checkpoint recent render parity worktree`
- 或 `feat: checkpoint scope laser shell render worktree`
