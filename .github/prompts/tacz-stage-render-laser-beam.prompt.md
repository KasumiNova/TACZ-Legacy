---
name: "TACZ Stage Render Laser Beam"
description: "Port gun and attachment laser beam runtime rendering to TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写激光颜色/束体不显示的枪或配件、上游文件或验收截图要求"
---
迁移并修复 `TACZ` 的 **laser beam 功能渲染链** 到 `TACZ-Legacy`。

## 当前已知缺口
- Legacy 已有：
  - 激光配置与语言项
  - `GunRefitScreen` 中的激光颜色预览/编辑
  - gun-pack 资源中的 `laser_beam` 节点
- 但当前源码里缺少上游那种真正的 `BeamRenderer` / `laserBeamPaths` / `renderLaserBeam()` 接线。
- 不能再把“颜色能改”误判成“laser beam 已经渲染”。

## 上游真值源
- `TACZ/src/main/java/com/tacz/guns/client/model/functional/BeamRenderer.java`
- `TACZ/src/main/java/com/tacz/guns/client/model/BedrockAttachmentModel.java`
- `TACZ/src/main/java/com/tacz/guns/client/model/BedrockGunModel.java`
- 如涉及性能分支，可参考上游加速/兼容分支，但 1.12.2 不必强行 1:1 套现代加速框架

## 默认关注范围
- `src/main/java/com/tacz/legacy/client/model/**`
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`
- `src/main/kotlin/com/tacz/legacy/common/resource/TACZGunPackPresentation.kt`
- `src/main/kotlin/com/tacz/legacy/common/config/LegacyConfigManager.kt`
- 必要时新增 `src/main/java/com/tacz/legacy/client/model/functional/BeamRenderer.java`

## 任务目标
1. 让 gun / attachment 模型中的 `laser_beam` 节点真正产生 beam 渲染。
2. 让 GUI/道具/NBT 中的激光颜色配置真实影响 beam 颜色，而不是只改 preview 数据。
3. 保留 `EnableLaserFadeOut` 这类已存在配置的语义；如果当前实现不了完全一致的 fade，也要至少保证配置不会变成死开关。
4. 同时覆盖：
   - 枪本体自带激光节点
   - 配件模型上的激光节点

## 执行要求
- 优先移植上游 beam renderer 思路，而不是用临时粒子线替代。
- 不要把本 Prompt 扩大成“顺便修所有 attachment rendering”。
- 如需先做 MVP，也必须先把：
  - 节点路径解析
  - 光束绘制
  - 颜色来源
  - 配置开关
  接通。
- 若 1.12.2 与现代 render state 有差异，必须真实记录差异点，不允许伪报“已完全一致”。

## 必验场景
- 至少一个枪本体激光场景
- 至少一个激光配件场景
- 至少一次颜色切换/预览后，实际运行 beam 颜色发生变化
- 若能抓图，优先给截图；若单帧难抓，也必须有 focused smoke marker + beam 参数日志证明束体实际绘制过

## 输出必须包含
- 上游真值文件
- Legacy beam 链路最终从哪条模型 path 触发
- 颜色与配置如何流到 renderer
- 实机验证结果与剩余 delta
