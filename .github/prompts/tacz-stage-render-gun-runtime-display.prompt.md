---
name: "TACZ Stage Render Gun Runtime Display"
description: "Port remaining gun-specific runtime display layers such as digits, readouts, emissive/material switching, and display-node consumption to TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写哪把枪缺数字/读数/emissive/runtime display、上游文件或验收截图要求"
---
迁移并修复 `TACZ` 的 **gun-specific runtime display / material layer parity** 到 `TACZ-Legacy`，重点处理枪身数字、读数、能量面板、剩余 display 节点消费，以及非纯文字的 runtime 表现。

## 当前已知基线
- `TextShow` / `PapiManager` / `TextShowRender` 主链已经落地；不要把本轮任务退化成重新移植一遍文字显示。
- 当前仍然缺的是：
  - 非纯文字的 runtime material / emissive / 帧切换表现
  - 某些 gun-specific display node 没被 runtime 真正消费
  - attachment/ammo display 与枪本体 runtime 之间仍有剩余细节缺口

## 上游真值源
- `TACZ/src/main/java/com/tacz/guns/client/resource/GunDisplayInstance.java`
- `TACZ/src/main/java/com/tacz/guns/client/model/BedrockGunModel.java`
- `TACZ/src/main/java/com/tacz/guns/client/model/functional/TextShowRender.java`
- `TACZ/src/main/java/com/tacz/guns/client/model/papi/PapiManager.java`
- `TACZ/src/main/java/com/tacz/guns/client/resource/pojo/display/ammo/**`
- `TACZ/src/main/java/com/tacz/guns/client/resource/pojo/display/attachment/**`
- 任何上游用来消费 gun-pack display 节点、动态贴图或 emissive 语义的文件

## 默认关注范围
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockGunModel.java`
- `src/main/java/com/tacz/legacy/client/model/functional/**`
- `src/main/java/com/tacz/legacy/client/model/papi/PapiManager.java`
- `src/main/kotlin/com/tacz/legacy/client/resource/TACZClientAssetManager.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/TACZGuiModelPreviewRenderer.kt`
- 相关 item / preview renderer

## 任务目标
1. 补齐剩余 gun-specific runtime display 节点消费，不再出现“上游有数字/读数，Legacy 只有空壳”。
2. 对齐非文字 runtime material 表现：如 emissive、贴图帧切换、能量读数、模型内仪表等。
3. 保持 first-person、GUI preview、物品渲染之间对同一 display/runtime 语义的解释一致。
4. 若某一类表现仍受平台限制，也要先保证节点不再完全失效，再真实记录残余限制。

## 执行要求
- 不要回头重做 HUD；HUD 不属于本 Prompt。
- 不要把 scope optics / shell ejection / laser beam 混进本 Prompt；那些有独立 Prompt。
- 如果问题其实只是 `TextShow` 没接对，先修接线；但若 `TextShow` 已正常，就继续追 runtime material / display node 本体。
- 尽量复用现有 gun-pack display 字段，不要造第二套 legacy-only 资源约定。

## 必验场景
- 至少一把带数字/读数/仪表的枪：运行时表现不再缺失。
- 至少一个 GUI preview 或 item renderer 场景：同一 display 语义在非第一人称下也成立。
- 若涉及 emissive 或贴图切换，必须给出实际运行证据，而不是只给解析测试。

## 输出必须包含
- 上游真值文件
- 当前问题属于哪类 runtime display / material gap
- Legacy 最终如何消费对应节点/材质语义
- 实测结果与剩余 delta
