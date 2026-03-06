---
name: "TACZ Migrate Client UX"
description: "Migrate TACZ client input, local gameplay bridge, HUD, screens, overlays, and tooltip systems into TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写要迁移的客户端交互/UI 功能、上游文件或验收约束"
---
迁移 `TACZ` 的**客户端交互 / 本地行为桥接 / HUD / GUI / Tooltip 系统**到 `TACZ-Legacy`。

默认关注范围：

- `client/input/**`
- `client/gameplay/**`
- `client/gui/**`
- `client/tooltip/**`
- `client/event/**`
- 与这些路径直接相关的 Legacy 输入桥、屏幕、HUD、提示文本与本地状态接线

执行要求：

- 保持上游输入门禁、客户端行为桥接、Screen/HUD/Tooltip 的可观察行为一致
- 不允许只把 UI 画出来但不接真实游戏状态
- 对逻辑密集的本地状态机、提示解析、HUD 文本生成等部分补测试
- 至少完成一轮运行链路验证，证明输入和 UI 是被真实状态驱动的，而不是静态假数据

当前阶段对接重点：

- 优先消费已经落地的数据与战斗基座：`TACZGunPackRuntimeRegistry.getSnapshot()` / `TACZRuntimeSnapshot`、`LegacyItems`、`IGun.getGunId()`、`IGunOperator`、`ShooterDataHolder`、`TACZNetworkHandler`
- 当前阶段不要重新发明 gun display cache、gun id 解析或本地 shooter 状态缓存；优先把 tooltip / HUD / Screen / 本地输入桥接到现有 runtime 与同步状态
- 若涉及网络事件表现，优先消费现有 `common/network/message/event/**`，不要平行新增第二套 UI 专用消息协议

通用补充要求：

- 可参考工作区中的 `PrototypeMachinery` 等现成 `1.12.2` 项目作为落地示例，但行为真值仍以上游 `TACZ` 为准
- 原版源码可优先参考：`TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**`（1.12.2）与 `TACZ/build/tmp/expandedArchives/forge-1.20.1-47.3.19_mapped_parchment_2023.08.20-1.20.1-sources.jar_*/net/minecraft/**`（1.20.1；若哈希后缀变化，按 `*sources.jar_*` 搜索）
- 在 Multi-Agent 环境下按文件或子系统切分所有权，避免多个 Agent 修改同一组文件
- 若 Gradle 构建问题明显不是本次改动引起，先记录并跳过，不要清理共享 Gradle 缓存或全局构建数据
- 可自由选择 `Java` 或 `Kotlin`，前提是更便于接线、贴近周边模块且不会增加维护负担

输出必须包含：

- 上游真值源文件
- Legacy 落点文件
- 测试结果
- 运行链路验证结果（输入触发、UI 更新、Tooltip/HUD 实时性）
- 任何不可避免的行为差异
