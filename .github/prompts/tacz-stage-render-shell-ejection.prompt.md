---
name: "TACZ Stage Render Shell Ejection"
description: "Port shell ejection runtime and shell-window visual parity to TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写缺少抛壳的枪、上游真值文件、验收截图或日志要求"
---
迁移并修复 `TACZ` 的 **shell ejection / 抛壳窗 runtime 与视觉链** 到 `TACZ-Legacy`。

## 当前已知缺口（本轮必须直面）
- `GunDisplay` / `shell_ejection` JSON 已能解析，但 Legacy 运行时没有真正消费。
- `src/main/java/com/tacz/legacy/client/animation/statemachine/GunAnimationStateContext.java`
  - `popShellFrom(int index)` 目前是空实现，只保留脚本兼容外壳。
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
  - 当前没有像上游那样持有 shell ejection runtime 状态。
- Legacy 当前树中没有上游对应的 `ShellRender` 落点；不要把“有 JSON 字段”误判成“功能已经接上”。

## 上游真值源
- `TACZ/src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationStateContext.java`
- `TACZ/src/main/java/com/tacz/guns/client/model/functional/ShellRender.java`
- `TACZ/src/main/java/com/tacz/guns/client/resource/GunDisplayInstance.java`
- 以及上游 `BedrockGunModel` / LOD 模型对 shell render 的挂接方式

## 默认关注范围
- `src/main/java/com/tacz/legacy/client/animation/statemachine/GunAnimationStateContext.java`
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/client/model/**`
- 必要时新增 `src/main/java/com/tacz/legacy/client/model/functional/ShellRender.java`
- 必要的 focused smoke / 诊断 hook

## 任务目标
1. 让 state machine / Lua `popShellFrom(index)` 真正驱动某个抛壳窗弹出壳体。
2. 正确消费 `GunDisplay.shellEjection` 的速度、加速度、角速度、存活时间与随机项。
3. 如果枪有 LOD/runtime 多模型路径，保持与上游一致的 shell 生命周期语义，不要只在一个模型上“假抛壳”。
4. 抛壳位置必须锚定到具体抛壳窗/骨骼节点，而不是屏幕中心粒子特效。

## 执行要求
- 优先对齐上游 `ShellRender` 设计；不要用一个与模型无关的临时粒子系统冒充完成。
- 不要把 tracer、muzzle flash、hit feedback 混进本 Prompt。
- 若上游是“多抛壳窗 index -> render slot”的语义，Legacy 也必须保留 index。
- 如果本轮只能做第一版，也必须先保证：
  - 抛壳从正确的模型位置出现
  - 生命周期和速度参数受 display 数据控制
  - `popShellFrom()` 不再是空操作

## 必验场景
- 至少一把有明确抛壳动作的枪，触发 `shoot` / `reload` / `bolt` 中的抛壳回调。
- 日志或 focused smoke marker 能证明：
  - shell ejection 被触发
  - index 正确
  - active shell 数量确实变化
- 如果截图难抓，也要提供一份包含触发 marker + 壳体生命周期参数的运行证据，而不是只给单元测试。

## 输出必须包含
- 上游真值文件
- Legacy 新增/修改的 runtime 链说明
- `popShellFrom()` 最终落到了哪里
- 实际运行验证结果与未收口小 delta
