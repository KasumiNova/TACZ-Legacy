# TACZ-Legacy 瞄准镜 (Scope) 渲染迁移方案

## 0. 2026-03-13 落地状态

本轮已把上游 `TACZ` 的 optic 主运行时正式接回 `TACZ-Legacy`，范围不再只是 `scope_view` 定位矩阵，而是包含：

- `BedrockAttachmentModel` 恢复 `scope_body` / `ocular_ring` / `ocular*` / `division` 节点消费，并在第一人称下按 `scope` / `sight` / `both` 三条路径执行 stencil-style optic 渲染。
- 新增 `client/util/RenderHelper.java`，在 1.12.2 主 framebuffer 上启用/恢复 stencil 测试，避免把模板状态散落在业务逻辑里。
- `BeamRenderer` 暴露当前 render context，optic runtime 只在真正的第一人称链路启用 stencil 分支。
- `FirstPersonFovHooks` + `EntityRendererMixin` 区分 world FOV 与 item-model FOV：
    - world FOV 使用上游同类的倍率换算（`MathUtil.magnificationToFov(...)`）
    - hand/item FOV 走 attachment `viewsFov` 与 gun `zoomModelFov`
- `FirstPersonRenderGunEvent` 补齐 `scope_view_N` 切换平滑，使用 `SecondOrderDynamics` 与旧/新 aiming matrix 插值，避免多倍率 view 直接跳变。

### 本轮验证

- 单测：
    - `FirstPersonFovHooksTest`（5 项，通过）
    - `FirstPersonRenderGunEventTransformTest`（5 项，通过）
- focused smoke：
    - 旧基线（reachability）：`build/smoke-tests/runclient-focused-smoke-20260313-091941.log`
        - `OPTIC_STENCIL_RENDERED attachment=tacz:scope_aug_default mode=scope oculars=1 divisions=1`
        - `ANIMATION_OBSERVED ... attachments=builtin-SCOPE=tacz:scope_aug_default ... aimingPath=root>gun_and_righthand>gun>positioning2>scope_pos>scope_view`
        - `PASS mode=animation_only ... regularGun=tacz:aug`
        - 截图：
            - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-091941/01-optic.png`
            - `build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-091941/02-animation.png`
    - auto ADS / sight 验证：`build/smoke-tests/runclient-focused-smoke-20260313-095621.log`
        - `OPTIC_STENCIL_RENDERED attachment=tacz:sight_p90 mode=sight oculars=1 divisions=1`
        - `ADS_READY gun=tacz:p90 aimingProgress=1.000 ... attachments=builtin-SCOPE=tacz:sight_p90 ...`
        - `PASS mode=ads_only ... regularGun=tacz:p90`
        - 截图：`build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-095621/01-ads_ready.png`
    - auto ADS / builtin scope 验证：`build/smoke-tests/runclient-focused-smoke-20260313-095722.log`
        - `OPTIC_STENCIL_RENDERED attachment=tacz:scope_aug_default mode=scope oculars=1 divisions=1`
        - `ADS_READY gun=tacz:aug aimingProgress=1.000 ... attachments=builtin-SCOPE=tacz:scope_aug_default ...`
        - `PASS mode=ads_only ... regularGun=tacz:aug`
        - 截图：`build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-095722/01-ads_ready.png`
    - auto ADS / refit 倍镜验证：`build/smoke-tests/runclient-focused-smoke-20260313-100009.log`
        - `REFIT_ATTACHMENT_APPLIED gun=tacz:scar_h attachment=tacz:scope_standard_8x`
        - `OPTIC_STENCIL_RENDERED attachment=tacz:scope_standard_8x mode=scope oculars=1 divisions=1`
        - `ADS_READY gun=tacz:scar_h aimingProgress=0.952 ... attachments=SCOPE=tacz:scope_standard_8x(display=tacz:scope_scout_display;...;views=2|2) aimingPath=root>gun_and_rh>scar_h>positioning2>scope_pos>views>scope_view`
        - `PASS mode=ads_only ... regularGun=tacz:scar_h`
        - 截图：`build/smoke-tests/focused-smoke-screenshots/runclient-focused-smoke-20260313-100009/01-ads_ready.png`

### 自动 ADS 补证结论

- focused smoke 现已补齐：
    - `FOCUSED_SMOKE_AUTO_ADS=true`
    - `FOCUSED_SMOKE_PASS_AFTER_ADS=true`
    - `FOCUSED_SMOKE_REFIT_ATTACHMENT=<attachment-id>`
- 这意味着 scope parity 不再停留在“镜体链路可达”的层面，而是已经能在真实客户端路径里自动：
    1. 进入第一人称
    2. 装配指定 optic（如 `tacz:scope_standard_8x`）
    3. 推动 `aimProgress` 到 ADS 阈值
    4. 命中 `ADS_READY` / `OPTIC_STENCIL_RENDERED` / `PASS`
    5. 输出归档截图供人工复核
- 截图质量说明：
    - `p90` 样本能证明第一人称 ADS 态与 sight 分支真实进入运行时，但镜体本身较不显眼；
    - `aug` 与 `scar_h + scope_standard_8x` 样本则能清楚看到镜体/镜筒，适合作为 tube scope 视觉主证据。
- 若后续 reopen 仍集中在“fully-open ocular aperture 是否完全居中”，建议在当前 auto ADS 基础上进一步调整 screenshot trigger/delay 做逐附件复检；这已经属于个别瞄具视觉 polish，而不是 optic 主运行时缺失。

## 1. 原版 (TACZ) 渲染机制分析

在 TACZ 中，处于 Aim (ADS) 状态的瞄准镜渲染并不是将模型简单叠加在枪支之上，而是通过**OpenGL 模板测试 (Stencil Buffer)** 构建的一个多阶段渲染管线。主要流程如下：

### A. 骨骼节点严格分层
`BedrockAttachmentModel` 在加载时会提取针对瞄准镜特化的骨骼节点：
- `scope_view` / `scope_body` / `ocular_ring`
- `ocular`（镜孔/镜片所在面）
- `division`（准星分划板/红点）

### B. 渲染时序倒置与模板写入 (Stencil Masking)
在绘制主枪体之前，优先渲染 Scope 的 `ocular` (镜片) 模型：
1. 关闭颜色和深度写入：`RenderSystem.colorMask(false, false, false, false)`
2. 开启模板写入，为不同的镜孔依次写入增量模板值（如 `1`, `2` 等）：`RenderSystem.stencilFunc(GL11.GL_GREATER, i + 1, 0xFF)`

### C. 枪体遮罩裁切 (Body Culling)
主枪体渲染时：
- 设置模板测试：`RenderSystem.stencilFunc(GL11.GL_EQUAL, 0, 0xFF)`
- **效果**：枪体渲染到与镜片重叠的屏幕区域时，像素会被硬件丢弃。完美防止枪管或机匣内部在开镜时穿模挡住视野。

### D. 视野张开与准星动态渲染 (Dynamic Reticle)
使用 `Tesselator` 绘制动态分划板：
1. 根据玩家的开镜进度 (Aiming Progress, 0.0~1.0)，计算出动态的圆形半径。
2. 使用 `GL_INVERT` 模板操作，在 `ocular` 范围内通过 `Triangle Fan` 画圆，限制后续渲染只在张开的视野圈内生效。
3. 最后，重新启用颜色写入，在此遮罩下渲染 `division` 节点（准星）。

---

## 2. TACZL (1.12.2) 迁移方案与重构难点

以下条目是实施前的主要迁移痛点；截至 2026-03-13，这些问题已经在 Legacy 中有了对应落地，只保留为设计说明与后续 reopen 参考。

在 1.12.2 Forge 环境下的 `LegacyGunItemStackRenderer` 中实现该机制，需要解决以下根本痛点：

### 痛点 (1)：Framebuffer 开启 Stencil 附件
Minecraft 1.12.2 默认的主 Framebuffer Object (FBO) 仅分配了 24-bit 的 Depth 格式（没有 8-bit Stencil）。直接调用 `GL11.glEnable(...)` 无效。
**解决方案**：
在客户端初始化或由于窗口变动重建时，强制激活主 FBO 的模板缓冲：
```java
Minecraft.getMinecraft().getFramebuffer().enableStencil();
```

### 痛点 (2)：GeckoLib/LegacyGeoModel 节点解析
现在的渲染是基于根模型将所有骨架递归渲染完毕（“一锅炖”）。我们需要提取外部模型内部的特定层次。
**解决方案**：
修改或包装现在的模型解析结果：
遍历外部配件 GeoModel 定义的 `IBone`，抽取 `ocularNodes`、`divisionNodes`、`scopeBodyNode`，在 `ExternalAttachmentRenderContext` 中单独分类并提供独立的 `renderOcular()` 等方法。

### 痛点 (3)：重排 `LegacyGunItemStackRenderer` 渲染管线
不能在挂点处立即渲染附属物品。必须将整个物品渲染流切分为多个 Pass。
**重构的管线顺序（伪代码）：**
```kotlin
fun renderItem(...) {
    // 1. 预先解析所有挂载件并得到绝对变换矩阵 (如 scope_pos)
    val attachmentContexts = resolveAllAttachments()
    val scopeContext = attachmentContexts.findByType(SCOPE)

    // 2. Pass 1: Stencil Mask 构建
    if (scopeContext != null && scopeAiming) {
        GL11.glEnable(GL11.GL_STENCIL_TEST)
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)
        
        setupStencilWriteState() // 关闭 Color/Depth 写入
        scopeContext.renderOcularStencils() // 仅渲染镜孔部位写入 Stencil ID
        restoreColorDepthState()
    }

    // 3. Pass 2: 主枪体渲染
    if (scopeContext != null) {
        GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF) // 枪身不能覆盖镜孔
    }
    renderGunBody(..., excludedNodes = listOf(scopeContext))

    // 4. Pass 3: 镜筒与准星渲染
    if (scopeContext != null) {
        GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF)
        scopeContext.renderScopeBody() // 渲染瞄具外壳
        
        applyDynamicReticleMask() // 计算开镜进度并反转 Stencil 绘制圆
        scopeContext.renderDivisions() // 渲染准星

        GL11.glDisable(GL11.GL_STENCIL_TEST)
    }
}
```

### 痛点 (4)：状态隔离与兼容性
1.12.2 的 `GlStateManager` 没有暴露完整的 Stencil 功能，必须混用 `GL11.*` 方法。
考虑到光影（Optifine/Oculus）也深度利用了 Stencil/Depth 缓冲区，我们在渲染开始和结束时必须**保存且恢复当前的 Stencil State（或者进行合理的位掩码区隔）**。

## 3. 2026-03-13 后续工作建议
1. 若后续出现个别枪包 reopen，先检查该 attachment 的 `views` / `viewsFov` / `scope_body` / `ocular*` / `division` 数据是否完整，再看运行时。
2. 若需要复验指定瞄具，优先复用现有 `FOCUSED_SMOKE_AUTO_ADS + FOCUSED_SMOKE_REFIT_ATTACHMENT + ADS_READY` 组合，而不是重新写一套手动截图流程。
3. 若未来还要继续做 preview parity，应复用现有 `RenderHelper` / render context 分层，而不是在 GUI / TEISR 分支重新发明一套 stencil 逻辑。
