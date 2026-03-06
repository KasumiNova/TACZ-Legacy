---
name: "TACZ Migrate Render Animation"
description: "Migrate TACZ rendering, model, animation, client resource, and client mixin systems into TACZ-Legacy with parity and runtime validation."
agent: "TACZ Migration"
argument-hint: "填写要迁移的渲染/动画功能、上游文件或验收约束"
---
迁移 `TACZ` 的**渲染 / 模型 / 动画 / 客户端资源 / 客户端 Mixin 系统**到 `TACZ-Legacy`。

默认关注范围：

- `client/resource/**`
- `client/model/**`
- `client/renderer/**`
- `client/animation/**`
- `api/client/**`
- `mixin/client/**`
- 与这些链路直接相关的 Legacy 渲染管线与客户端表现接线

执行要求：

- 保持上游的数学逻辑、动画采样、插值、姿态合成、资源解析与渲染时序语义一致
- 不允许只迁资源解析或只迁渲染壳子；必须保证整条表现链路真实可达
- 对插值器、姿态采样、可见性过滤、资源解析、时序计算等逻辑密集部分补测试
- 至少完成一轮编译/测试验证和一轮真实运行链路验证（如 `runClient` smoke、focused render path、调试日志/覆盖确认）

当前阶段对接重点：

- 优先消费已经落地的数据与战斗基座：`TACZGunPackRuntimeRegistry.getSnapshot()` / `TACZRuntimeSnapshot`、`LegacyItems` 的 item type 注册、`IGun.getGunId()`、`TACZNetworkHandler` 与现有 shooter/network 状态
- 不要重做 display / gun id / item type 的平行缓存；优先把渲染、模型、动画和客户端资源链路接到现有 runtime snapshot 与枪械 item 消费路径上
- 若涉及射击 / 换弹 / 近战的客户端表现，优先消费现有 `common/network/message/event/**` 事件消息并与渲染/音效系统接线

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
- 运行链路验证结果（不是 compile-only）
- 任何必须保留的非 1:1 结构差异及原因
