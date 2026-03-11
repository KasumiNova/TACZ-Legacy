# TACZ 迁移计划总入口（分册版）

> 本目录是 `TACZ-Legacy` 的 **正式迁移计划入口**。  
> 从 2026-03-10 开始，原先单文件的 `docs/TACZ_AGENT_MIGRATION_PLAN.md` 已拆成 **短入口 + 多分册** 结构，避免后续 Agent 因单文件过大而难以维护。

## 先看这里

建议按下面顺序阅读：

1. `docs/migration_plan/MAIN.md`
   - 读全局结构、轨道边界、更新规则、Prompt 路由。
2. `docs/migration_plan/2026-03-10-FOUNDATION-TO-CLIENT-UX.md`
   - 读 Foundation / Data / Combat / Audio / Client UX 的详细阶段状态。
3. `docs/migration_plan/2026-03-10-RENDER-VALIDATION-AND-PROMPTS.md`
   - 读 Render 轨道、focused smoke 结论、Prompt 细分与并行协作建议。
4. `docs/TACZ_AGENT_WORKFLOW.md`
   - 读 Agent 交付、阶段报告、文档更新与 handoff 规范。

兼容入口仍保留：

- `docs/TACZ_AGENT_MIGRATION_PLAN.md`
  - 仅作为跳板与旧路径兼容页；**不要再把大段阶段状态直接堆回这个文件**。

## 文档地图

| 文档 | 角色 | 什么时候更新 |
|---|---|---|
| `docs/migration_plan/MAIN.md` | 稳定入口、轨道边界、更新规则、Prompt 总路由 | 当“分工、顺序、边界、维护规则”变化时 |
| `docs/migration_plan/2026-03-10-FOUNDATION-TO-CLIENT-UX.md` | Foundation / Data / Combat / Audio / Client UX 的分类分册 | 当这些轨道出现新的阶段性落地、回归、阻塞或验收结论时 |
| `docs/migration_plan/2026-03-10-RENDER-VALIDATION-AND-PROMPTS.md` | Render 轨道、视觉验收、Prompt 细分、并行建议 | 当 Render/Prompt 路由/阶段验收发生变化时 |
| `docs/TACZ_AGENT_WORKFLOW.md` | Agent 交付与阶段报告规范 | 当协作规则、报告模板、文档更新流程变化时 |
| `docs/TACZ_AGENT_MIGRATION_PLAN.md` | 兼容跳板 | 仅在入口或分册导航变化时 |

## 核心结论

当前阶段**不建议**为每个子系统继续复制一份几乎相同的迁移 Agent。更稳妥的组织方式仍然是：

1. **协调 Agent（主聊天 / 默认 Agent）**
   - 负责拆任务、排依赖、安排并行度、处理跨系统冲突、做最后集成验收。
2. **侦察 Agent（推荐 `Ask` / `Explore`）**
   - 只读侦察：找上游真值源、梳理依赖边界、确认 `TACZ-Legacy` 落点与风险。
3. **`TACZ Migration` Agent**
   - 真正执行迁移：严格保证逻辑不变、补测试、做运行链路验证、一次性收口。

也就是说：**Agent 角色保持少而稳定，系统差异交给 Prompt 来表达**。

## 统计口径

- 上游基线：`TACZ/src/main/java/com/tacz/guns/**`
- 默认只统计主源码 `.java`
- 统计值：**源码文件数 + 物理源码行数**
- 分组的目的首先是指导迁移分工，而不是完全复刻上游包结构

## 通用迁移参考与协作约束

- 行为真值源始终以上游 `TACZ` 为准。
- 落地到 `1.12.2 Forge` 时，可参考工作区中的 `PrototypeMachinery` 借鉴 API、分层、Gradle/Forge 接线与 Java/Kotlin 混合实现方式。
- `1.12.2` 原版源码优先从 `TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**` 阅读。
- 对应 `1.20.1` 映射源码可优先从 `TACZ/build/tmp/expandedArchives/*sources.jar_*/net/minecraft/**` 阅读。
- Multi-Agent 环境下优先按**子系统 / 文件集合**划分所有权，避免多个 Agent 同时编辑同一批文件。
- 如果 Gradle 构建失败且明显不是本轮任务引入的问题，应记录并跳过，不要顺手清理共享缓存来“自证清白”。
- 迁移实现可自由选 `Java` 或 `Kotlin`，前提是贴近周边模块、减少胶水代码、避免明显增加未来维护复杂度。

## TACZ 大系统分组

| 迁移轨道 | 上游范围（主） | 文件数 | 物理行数 | 是否建议独立推进 | 推荐执行方式 |
|---|---|---:|---:|---|---|
| 基础启动与注册 | `GunMod.java`、`config/**`、`init/**`、`event/**`、`block/**`、`command/**`、`particles/**`、`sound/**` | 68 | 3,319 | 是，最先做 | `Ask/Explore` 侦察 + `TACZ Migration` 执行 |
| 数据/枪包兼容 | `resource/**`、`api/resource/**`、`api/modifier/**` | 90 | 7,298 | 是，核心前置 | `Ask/Explore` 侦察 + `TACZ Migration` 执行 |
| 战斗/实体/网络 | `entity/**`、`network/**`、`item/**`、`inventory/**`、`crafting/**`、`api/item/**` | 102 | 10,039 | 是，核心玩法闭环 | `Ask/Explore` 侦察 + `TACZ Migration` 执行 |
| 客户端交互/UI | `client/gameplay/**`、`client/input/**`、`client/gui/**`、`client/tooltip/**`、`client/event/**` | 64 | 6,896 | 是，可与战斗线并行 | `Ask/Explore` 侦察 + `TACZ Migration` 执行 |
| 渲染/动画/客户端资源 | `client/resource/**`、`client/model/**`、`client/renderer/**`、`client/animation/**`、`api/client/**`、`mixin/client/**` | 191 | 19,995 | **强烈建议独立** | `Ask/Explore` 侦察 + `TACZ Migration` 执行 |
| 第三方兼容 | `compat/**` | 55 | 3,857 | 是，但排后 | `Ask/Explore` 侦察 + `TACZ Migration` 执行 |
| 交叉支撑层 | `util/**`、`mixin/common/**`、`api/event/**`、`api/entity/**` 等 | 59 | 4,619 | 不建议独立成整条线 | 跟随所属主系统迁移 |

## 推荐迁移顺序

1. **基础启动与注册**
2. **数据/枪包兼容**
3. **战斗/实体/网络**
4. **客户端交互/UI**
5. **渲染/动画/客户端资源**
6. **第三方兼容**

## 当前推荐主攻方向（2026-03-10）

详细证据见两个分类分册；这里只保留协调入口层结论：

1. **Render 剩余子轨优先**
   - shell ejection
   - scope / optic parity
   - laser beam runtime
   - gun-specific runtime display / material layer
2. **Combat 剩余 cadence/parity**
   - heat → RPM / cadence 真实接线
   - 少量边界 fire-mode / script / gate 收尾
3. **Client UX 收口**
   - `GunRefitScreen` 的沉浸式 opening / mask / composition / focus polish
4. **第三方兼容排后**
   - JEI / KubeJS 仍不纳入当前主攻批次

## Prompt 路由总览

### 通用 Prompt

| 目标 | Prompt 文件 | 用途 |
|---|---|---|
| 迁移前侦察 | `.github/prompts/tacz-scan-system.prompt.md` | 先摸清上游真值源、边界、依赖、Legacy 落点 |
| 基础启动与注册 | `.github/prompts/tacz-migrate-foundation.prompt.md` | 迁移入口、配置、注册、底座 |
| 数据/枪包兼容 | `.github/prompts/tacz-migrate-data-pack.prompt.md` | 资源消费、兼容补齐与玩法接线 |
| 战斗/实体/网络 | `.github/prompts/tacz-migrate-combat-network.prompt.md` | shooter/network 主链与 parity 补齐 |
| 客户端交互/UI | `.github/prompts/tacz-stage-client-ux-gui-i18n.prompt.md` | 输入/HUD/tooltip/GUI/refit 收尾 |
| 渲染/动画/第一人称 | `.github/prompts/tacz-stage-render-animation-first-person.prompt.md` | 第一人称动画、ADS、镜头/后坐力与 fire feedback |
| 第三方兼容 | `.github/prompts/tacz-migrate-compat.prompt.md` | JEI/KubeJS/Cloth/光影等兼容 |

### 2026-03-10 后续专项 Prompt（不含 JEI / KubeJS）

| 症状 / 目标 | Prompt 文件 |
|---|---|
| 抛壳 JSON 能解析，但运行时完全不抛壳 | `.github/prompts/tacz-stage-render-shell-ejection.prompt.md` |
| 长筒镜 / 红点 optics 仍是最小接线版 | `.github/prompts/tacz-stage-render-scope-optic-parity.prompt.md` |
| 激光颜色可编辑，但 `laser_beam` 束体未真实渲染 | `.github/prompts/tacz-stage-render-laser-beam.prompt.md` |
| heat 对散布已生效，但 `min_rpm_mod / max_rpm_mod` 未接入 cadence | `.github/prompts/tacz-stage-combat-heat-rpm-cadence.prompt.md` |
| `GunRefitScreen` 缺少沉浸式 world-to-screen / focus polish | `.github/prompts/tacz-stage-client-ux-refit-immersive-polish.prompt.md` |
| 枪身数字 / 读数 / emissive / 非文字 runtime display 仍有缺口 | `.github/prompts/tacz-stage-render-gun-runtime-display.prompt.md` |

补充：

- `HUD-under-GUI`、`camera recoil`、`tracer default-speed` 当前更偏验证补证或既有 Prompt 的收尾子项，不再单独拆新 Prompt。
- JEI / KubeJS 跨模组联动继续保留在 `.github/prompts/tacz-migrate-compat.prompt.md`。

## 什么时候更新哪份文档

### 更新 `MAIN.md`

当你修改的是这些内容：

- 轨道边界
- 推荐顺序
- Prompt 总路由
- 文档维护规则
- 目录结构与入口说明

### 更新分类分册

当你修改的是这些内容：

- 某条轨道的详细阶段状态
- focused smoke / 单测 / 编译结论
- 某轮 reopen 的原因、修复和残余风险
- 需要保留的阶段证据与文件落点

### 新增新的分类分册

当出现以下情况时，不要继续膨胀现有分册，而是新增文件：

- 某个主题已经明显超出当前分册边界
- 某一大分类继续膨胀，已经不适合在当前分类分册内维护
- 需要为新的**大分类**单独建立长期维护入口

命名规则：

- `YYYY-MM-DD-CATEGORY.md`

其中：

- `YYYY-MM-DD` 表示**该分类文档的建档/结构整理日期**，不是正文里所有开发记录的唯一发生日期。
- `CATEGORY` 表示**文档的大分类**，例如 `FOUNDATION-TO-CLIENT-UX`、`RENDER-VALIDATION-AND-PROMPTS`、`COMPAT-AND-INTEGRATION`。
- 正文若汇总多天开发内容，必须在各小节中继续标明真实开发日期，避免“文件名日期 = 全部内容日期”的误读。

示例：

- `2026-03-10-FOUNDATION-TO-CLIENT-UX.md`
- `2026-03-10-RENDER-VALIDATION-AND-PROMPTS.md`
- `2026-03-11-COMPAT-AND-INTEGRATION.md`
- `2026-03-11-SMOKE-VALIDATION.md`

## 维护约束

- **不要**再把长篇阶段日志直接堆回 `docs/TACZ_AGENT_MIGRATION_PLAN.md`。
- `MAIN.md` 保持短、稳、可导航，尽量不要重新长成一本新巨著。
- 阶段细节进入分类分册；任务交接细节进入 `.agent-workspace/stage-reports/`。
- 交付规范、阶段报告模板与 handoff 规则统一维护在 `docs/TACZ_AGENT_WORKFLOW.md`。

## 备注

如果后续继续细分 custom agent，优先考虑：

- **渲染/动画/客户端资源**
- **数据/枪包兼容**

前提仍然是：它们真的需要单独的工具限制或输出形式，且这些差异无法仅靠 Prompt 表达。
