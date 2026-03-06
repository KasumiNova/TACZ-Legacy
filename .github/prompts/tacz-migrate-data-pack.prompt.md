---
name: "TACZ Migrate Data Pack"
description: "Migrate TACZ gun-pack, resource loading, JSON/index/modifier, and compatibility data pipeline into TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写要迁移的数据/枪包/资源系统、上游文件或兼容约束"
---
迁移 `TACZ` 的**数据 / 枪包 / 资源兼容链路**到 `TACZ-Legacy`。

当前阶段说明：

- 这条线的**本阶段迭代已完成**，当前 `TACZ-Legacy` 中已有 `ResourceManager.kt`、`JsonResourceLoader.kt`、`ModifierApi.kt`、`TACZGunPackRuntime.kt`、`TACZPackConvertor.kt`、`GunDataAccessor.kt` 与相关测试。
- runtime 数据也已经开始被真实消费到 item / tooltip / workbench 摘要 / recipe filter / attachment tag 等路径，不再只是“能扫到包”。
- 现在使用这个 Prompt 时，默认应视为**回归修复 / 缺口补齐 / 新消费点接入**，而不是继续把数据主链当成本阶段主攻方向。
- `Client UX` 的本阶段迭代也已完成；若任务重点是 tooltip / HUD / 输入 / `gun_smith_table` GUI-container-craft 流 / 客户端展示消费，优先改用 `client ux` 或 `render animation` Prompt；仅在确实需要改 runtime snapshot / parser / modifier / pack convertor / resource API 本身时再使用本 Prompt。

默认关注范围：

- `resource/**`
- `api/resource/**`
- `api/modifier/**`
- 枪包发现、Pack 元数据、JSON 反序列化、索引、modifier、兼容读取与版本检查
- 与这些数据链路直接相连的 Legacy 运行时注册/缓存层

执行要求：

- 保持枪包格式、键名、兼容读取行为与上游一致，除非用户明确要求改动
- 若必须调整格式，采用“兼容读取 + 新格式写入”的过渡策略
- 对 JSON 解析、modifier 应用、索引构建、兼容映射补测试
- 验证迁移后的数据路径不是死代码：至少证明资源能被真实加载、索引或运行时快照能被游戏链路消费
- 若 `TACZGunPackRuntimeRegistry` / `TACZRuntimeSnapshot` 已能提供所需数据，优先复用并扩展现有运行时快照与 API，不要再引入并行缓存、重复扫描器或第二套解析入口

当前阶段优先任务：

- 为新的消费场景补 runtime 字段、索引、展示数据或兼容映射时，优先扩展现有 `TACZRuntimeSnapshot` / `TACZGunPackPresentation` / `LegacyRuntimeTooltipSupport` 所依赖的数据来源
- 仅在发现明确 parity 回归、旧包兼容缺口或玩法接线缺失时继续修改数据主链
- 优先复用 `DefaultGunPackExporter.kt`、`LegacyItems.kt` 和现有 runtime 消费点，避免新增孤立的中间层或第二套 presentation/runtime 缓存

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
- 运行链路验证结果（资源加载 / 枪包扫描 / 索引消费）
- 说明这次是“补齐 / 对接 / 消费”还是“补主链缺口”，并明确复用了哪些现有 runtime/API
- 任何不可避免的格式差异及原因
