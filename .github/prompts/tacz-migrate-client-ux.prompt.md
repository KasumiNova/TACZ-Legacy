---
name: "TACZ Migrate Client UX"
description: "Migrate TACZ client input, local gameplay bridge, HUD, screens, overlays, and tooltip systems into TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写要迁移的客户端交互/UI 功能、上游文件或验收约束"
---
迁移 `TACZ` 的**客户端交互 / 本地行为桥接 / HUD / GUI / Tooltip 系统**到 `TACZ-Legacy`。

当前阶段说明：

- 这条线的**本阶段迭代已完成**。
- 当前 `TACZ-Legacy` 已有可继续复用的客户端桥接与消费层，例如 `GunEvents.kt`、`LegacyClientInputEventHandler.kt`、`LegacyClientOverlayEventHandler.kt`、`LegacyClientPlayerGunBridge.kt`、`LegacyKeyBindings.kt`、`LegacyRuntimeTooltipSupport.kt`、`TACZGunPackPresentation.kt`，以及本轮新落地的 `GunSmithTableScreen.kt`、`LegacyGuiHandler.kt`、`LegacyGuiIds.kt`、`GunSmithTableContainer.kt`、`ClientMessageGunSmithCraft.kt`、`common/application/gunsmith/LegacyGunSmithingRuntime.kt`。
- 这意味着 tooltip / HUD / overlay / 输入桥接 / `gun_smith_table` 基础 GUI-container-craft 流已经不再是空白区；现在使用这个 Prompt 时，默认应视为**回归修复 / 剩余 parity 补齐 / 新客户端消费点接入 / 被 backend 阻塞的后续屏幕收尾**。
- 当前已知仍未 truthfully 完成的重点不在 `gun_smith_table` 基础链路，而在 `GunRefitScreen` 等依赖更完整 refit backend 与附件槽位状态接口的后续能力。

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
- 当前阶段不要重新发明 gun display cache、gun id 解析、本地 shooter 状态缓存、runtime 翻译解析或 presentation bridge；优先复用 `TACZGunPackPresentation`、`LegacyRuntimeTooltipSupport`、`GunEvents`、`LegacyClientPlayerGunBridge`
- 若涉及网络事件表现，优先消费现有 `common/network/message/event/**` 与 `api/event/GunEvents.kt`，不要平行新增第二套 UI 专用消息协议
- `gun_smith_table` 的基础 GUI / container / craft 流已经落地；后续若继续修改，默认应以回归修复、parity 补齐、筛选/预览细节增强或结果展示校正为目标
- 若任务转向 `GunRefitScreen`、附件槽位操作、laser color / scope zoom / refit transform 等能力，请明确同步检查 `IGun` / `IAttachment` / attachment type / item NBT / server refit backend 是否已具备足够真值接口；若没有，应先把任务定性为 blocked backend 收尾，而不是硬拼假 UI
- 运行链路验证若被已知的 Forge 1.12 + 多版本 Kotlin jar 的 ASM 扫描问题挡在模组初始化前，可接受用“编译 + 定向测试 + 说明阻塞点”的方式收口，但必须明确这不是本次改动引入的新问题

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
- 明确说明这次接入或复用了哪些现成 client UX bridge / presentation / event 入口
- 若运行时 smoke 仍被外部 ASM / 依赖问题阻断，必须明确记录阻塞点、已完成的替代验证，以及哪些链路仍待真实客户端手验
- 任何不可避免的行为差异
