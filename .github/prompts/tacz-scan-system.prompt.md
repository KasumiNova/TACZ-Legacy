---
name: "TACZ Scan System"
description: "Read-only reconnaissance for a TACZ subsystem before migration: find upstream source-of-truth files, dependencies, Legacy target layers, and validation risks."
agent: "Ask"
argument-hint: "填写要分析的系统，例如 scope 渲染、GunPack 资源链路、射击网络同步"
---
在迁移某个 `TACZ` 子系统之前，先对该系统做一次只读侦察。

请围绕**用户指定的系统/功能**输出：

- 上游 `TACZ` 的真值源文件与主要包边界
- 该系统的职责分层（数据、逻辑、网络、渲染、UI 等）
- 关键依赖与耦合点
- 在 `TACZ-Legacy` 中更合适的落点（`api/common/client/integration/mixin`，必要时补充 `domain/application/infrastructure`）
- 哪些部分适合先迁，哪些必须后迁
- 哪些逻辑必须补测试
- 哪些部分必须做运行链路验证
- 建议拆成哪几个可独立提交的小任务

要求：

- 只做研究与规划，不改文件
- 尽量给出具体文件路径，而不是只给目录名
- 明确指出高风险区域（特别是渲染、网络、输入、资源兼容、Mixin）
- 若用户问到数据迁移或战斗/网络，请先核对 `TACZ-Legacy` 里是否已经有可复用基座（如 `TACZGunPackRuntime`、`ResourceManager`、`ModifierApi`、`IGunOperator`、`TACZNetworkHandler`、`ShooterTickHandler`）；若已有，优先把剩余工作归类为“对接 / 补齐 / 消费链路”，而不是建议重复迁移主干
- 可参考工作区中的 `PrototypeMachinery` 等现成 `1.12.2` 项目作为 Forge-era 落地示例，但行为真值仍以上游 `TACZ` 为准
- 如需查看原版源码，可优先参考：`TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**`（1.12.2）与 `TACZ/build/tmp/expandedArchives/forge-1.20.1-47.3.19_mapped_parchment_2023.08.20-1.20.1-sources.jar_*/net/minecraft/**`（1.20.1，哈希后缀变化时按 `*sources.jar_*` 搜索）
- 若规划的是多 Agent 并行迁移，请显式建议按子系统或文件集合分割范围，避免代码打架
