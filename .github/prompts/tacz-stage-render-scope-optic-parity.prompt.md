---
name: "TACZ Stage Render Scope Optic Parity"
description: "Port scope-body, ocular, division, and stencil-style optic rendering parity to TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写哪类瞄具缺镜片/分划/遮罩、上游文件或验收截图要求"
---
迁移并修复 `TACZ` 的 **scope / sight optic runtime** 到 `TACZ-Legacy`，重点是镜片、分划、ocular/body 节点、scope view 与 stencil/遮罩语义。

## 当前阶段状态（2026-03-13）
- optic 主运行时已落地：`BedrockAttachmentModel` 现在会消费 `scope_body` / `ocular_ring` / `ocular*` / `division`，并在第一人称下执行 `scope` / `sight` / `both` 三类 stencil-style 渲染分支。
- `FirstPersonFovHooks` + `EntityRendererMixin` 已区分 world FOV 与 item-model FOV；`FirstPersonRenderGunEvent` 也已补 `scope_view_N` 切换平滑。
- focused smoke 已补自动 ADS / 指定 refit 附件能力，并完成三组 runtime 验证：
   - `tacz:p90` → `OPTIC_STENCIL_RENDERED attachment=tacz:sight_p90 mode=sight` + `ADS_READY ... aimingProgress=1.000` + `PASS mode=ads_only`
   - `tacz:aug` → `OPTIC_STENCIL_RENDERED attachment=tacz:scope_aug_default mode=scope` + `ADS_READY ... aimingProgress=1.000` + `PASS mode=ads_only`
   - `tacz:scar_h + tacz:scope_standard_8x` → `REFIT_ATTACHMENT_APPLIED ... scope_standard_8x` + `OPTIC_STENCIL_RENDERED ... mode=scope` + `ADS_READY ... aimingPath=...>views>scope_view` + `PASS mode=ads_only`

## 当前仍值得处理的 reopen
- 若某些 gun-pack 仍表现异常，本 Prompt 现在更适合处理：
   - 个别 attachment 的 `ocular_scope` / `ocular_sight` / `division` 可见性 reopen
   - 多倍率 `scope_view_N` 或 `viewsFov` 个案回归
   - 个别倍率镜 fully-aimed 视觉居中 / aperture 张开程度的逐镜 polish

## 上游真值源
- `TACZ/src/main/java/com/tacz/guns/client/model/BedrockAttachmentModel.java`
- `TACZ/src/main/java/com/tacz/guns/client/model/functional/AttachmentRender.java`
- `TACZ/src/main/java/com/tacz/guns/client/resource/index/ClientAttachmentIndex.java`
- 任何与 `scope_body` / `ocular_ring` / `division` / `scope_view` / 多 zoom 切换相关的上游文件

## 默认关注范围
- `src/main/java/com/tacz/legacy/client/model/BedrockAttachmentModel.java`
- `src/main/java/com/tacz/legacy/client/model/functional/AttachmentRender.java`
- `src/main/java/com/tacz/legacy/client/resource/index/ClientAttachmentIndex.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockGunModel.java`
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- 必要的瞄具 display / preview / smoke 诊断

## 任务目标
1. 恢复 `scope_body` / `ocular_ring` / `division` / `ocular*` 的运行时消费链，而不是继续全隐藏。
2. 对齐上游 scope / sight / both 三种路径：长筒镜、红点/全息、二者共存时的表现都要有清晰规则。
3. 保持 `scope_view` / zoom 切换与当前第一人称定位矩阵链兼容，不要顺手重写整条 first-person pose。
4. 若 1.12.2 平台限制导致无法一次性 100% 复刻现代渲染状态，也必须先把：
   - 镜片/分划可见性
   - ocular/body 层级
   - scope view 选路
   - 多倍率 view switch
   做正确，再真实记录残余限制。

## 执行要求
- 先读上游 `BedrockAttachmentModel` 的节点/状态机/渲染次序真值，再动 Legacy。
- 不要把这项任务偷换成“调一调 first-person positioning 常量”。
- 不要把 scope optics 和 HUD、muzzle flash、shell ejection 混在一锅里做。
- 如需加 1.12.2 特有的 GL/stencil 兼容桥，必须保持节点命名与上游 gun-pack 数据兼容。

## 必验场景
- 至少一把长筒镜：镜片主体、分划/视圈不是整段缺失。
- 至少一把 sight：`ocular_sight` / `ocular_scope` 的可见规则正确。
- 至少一个多倍率/多 `scope_view_N` 场景：zoom 切换不会回退到错误 view。
- 必须提供截图或 focused smoke marker，证明现在不是“节点继续隐藏，只是代码更复杂了”。

## 输出必须包含
- 上游 optic 相关真值文件
- Legacy 现在如何从 attachment display/index 走到 `BedrockAttachmentModel`
- 哪些节点恢复了消费，哪些因平台限制仍有残差
- 实际运行验证与剩余 delta
