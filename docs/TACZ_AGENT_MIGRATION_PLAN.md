# TACZ 大系统与 Agent 迁移规划

## 结论先行

当前阶段我**不建议**为每个子系统再复制一份几乎相同的迁移 Agent。更稳妥的组织方式是：

1. **协调 Agent（主聊天 / 默认 Agent）**
   - 负责拆任务、排依赖、安排并行度、处理跨系统冲突、做最后集成验收。
2. **侦察 Agent（当前推荐 `Ask`）**
   - 只读侦察：找上游真值源、梳理依赖边界、确认 `TACZ-Legacy` 落点与风险。
3. **`TACZ Migration` Agent**
   - 真正执行迁移：严格保证逻辑不变、补测试、做运行链路验证、一次性收口。

也就是说：**Agent 角色保持少而稳定，系统差异交给 Prompt 来表达**。

这样做有三个好处：

- **迁移纪律集中**：严格 parity / 单测 / runtime validation 的硬约束只维护一份，不会在多个 Agent 文件里漂移。
- **系统拆分灵活**：真正变化的是“迁移哪条系统”，不是“迁移纪律本身”。
- **后续维护成本低**：将来你只需要调整 Prompt 范围，不用同步修改一堆近似 Agent 文件。

## 统计口径

- 上游基线：`TACZ/src/main/java/com/tacz/guns/**`
- 仅统计主源码 `.java`
- 统计值：**源码文件数 + 物理源码行数**
- 以下分组是为了指导迁移分工，不是上游原始包结构的唯一真理；个别跨系统文件由协调 Agent 归口裁定

## 通用迁移参考与协作约束

- **行为真值源**始终以上游 `TACZ` 为准；但在具体落地到 `1.12.2 Forge` 时，可以参考工作区中的现成 `1.12.2` 项目（例如 `PrototypeMachinery`）来借鉴 API 用法、分层方式、Gradle/Forge 接线与 Java/Kotlin 混合实现范式。
- 当前工作区内的 **Minecraft 1.12.2 原版源码** 可优先从 `TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**` 阅读。
- 当前工作区内与上游 `TACZ` 对应的 **Minecraft 1.20.1 映射源码** 可优先从 `TACZ/build/tmp/expandedArchives/forge-1.20.1-47.3.19_mapped_parchment_2023.08.20-1.20.1-sources.jar_*/net/minecraft/**` 阅读；若哈希后缀变化，可按 `TACZ/build/tmp/expandedArchives/*sources.jar_*/net/minecraft/**` 搜索。
- 在 **Multi-Agent** 环境下，优先按“子系统 / 文件集合”拆分所有权，避免多个 agent 同时编辑同一批文件导致补丁冲突。
- 若 Gradle 构建或测试失败且明显不是当前任务引入的问题，应先记录并跳过，不要为了“自证清白”去清理共享的 Gradle 缓存、wrapper 或全局构建数据。
- 迁移实现可自由选择 **Java** 或 **Kotlin**，但前提是贴近周边模块、减少胶水代码、不会显著增加未来维护复杂度。

## TACZ 大系统分组

| 迁移轨道 | 上游范围（主） | 文件数 | 物理行数 | 是否建议独立推进 | 推荐执行方式 |
|---|---|---:|---:|---|---|
| 基础启动与注册 | `GunMod.java`、`config/**`、`init/**`、`event/**`、`block/**`、`command/**`、`particles/**`、`sound/**` | 68 | 3,319 | 是，最先做 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 数据/枪包兼容 | `resource/**`、`api/resource/**`、`api/modifier/**` | 90 | 7,298 | 是，核心前置 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 战斗/实体/网络 | `entity/**`、`network/**`、`item/**`、`inventory/**`、`crafting/**`、`api/item/**` | 102 | 10,039 | 是，核心玩法闭环 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 客户端交互/UI | `client/gameplay/**`、`client/input/**`、`client/gui/**`、`client/tooltip/**`、`client/event/**` | 64 | 6,896 | 是，可与战斗线并行 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 渲染/动画/客户端资源 | `client/resource/**`、`client/model/**`、`client/renderer/**`、`client/animation/**`、`api/client/**`、`mixin/client/**` | 191 | 19,995 | **强烈建议独立** | `Ask` 侦察 + `TACZ Migration` 执行 |
| 第三方兼容 | `compat/**` | 55 | 3,857 | 是，但排后 | `Ask` 侦察 + `TACZ Migration` 执行 |
| 交叉支撑层 | `util/**`、`mixin/common/**`、`api/event/**`、`api/entity/**` 等 | 59 | 4,619 | 不建议独立成一整条线 | 跟随所属主系统迁移 |

## 为什么这样拆更合适

### 1. 基础启动与注册

这条线负责把 `TACZ-Legacy` 的“壳”先撑起来，包括：

- 模组入口与生命周期接线
- 配置加载
- 注册表与内容初始化
- 方块/方块实体/容器等底座
- 启动期事件与通用命令/音效/粒子注册

没有这条线，后面的资源、玩法、渲染都容易变成“代码有了但链路没接上”。

### 2. 数据/枪包兼容

这是迁移里的“输入面”：

- 枪包发现/加载
- JSON/POJO/索引/序列化
- modifier 与兼容读取
- pack 转换与版本检查

这条线必须尽早稳定，因为玩法、渲染、UI 最后都要消费它产出的资源索引和运行时快照。

### 3. 战斗/实体/网络

这是核心玩法闭环：

- 开火/换弹/拉栓/切枪/近战
- 服务端裁决与实体同步
- 子弹、命中、状态、时间戳与网络容错
- 枪械/附件/工作台/物品协议

这条线最适合用“严格迁移 Agent”推进，因为行为性最强、最容易引入回归。

### 4. 客户端交互/UI

这条线不是“纯表现层”，它直接连接玩家输入与本地状态机：

- 按键绑定
- 本地玩家行为入口
- HUD / Overlay / Screen / Tooltip
- 客户端事件桥接

建议与战斗/网络线保持高频对齐，避免出现“UI 显示可用，实际服务端逻辑没通”的假完成。

### 5. 渲染/动画/客户端资源

这是当前体量最大、重写风险最高的一条线，也是最不适合 1:1 机械搬运的区域：

- 动画 API 与插值/姿态采样
- 模型、渲染器、第一/第三人称表现
- 客户端资产索引、显示定义、音频资产
- 客户端 Mixin 与渲染时序接线

**这条线应该被视作独立工程流，而不是附着在 UI 或玩法上的“顺手迁一下”。**

### 6. 第三方兼容

如 `KubeJS`、`JEI`、`Cloth`、控制器、光影/动画兼容等，适合排在核心链路稳定之后单独做：

- 依赖可选
- 风险分散
- 对主玩法并非强前置

## 推荐迁移顺序

1. **基础启动与注册**
2. **数据/枪包兼容**
3. **战斗/实体/网络**
4. **客户端交互/UI**（可与 3 部分并行，但要同步验收）
5. **渲染/动画/客户端资源**
6. **第三方兼容**

## 当前阶段进展（基于当前 `TACZ-Legacy` 工作区状态）

- **Foundation**：已完成第一波落地，基础启动、注册、烟测脚本与基础测试已进仓。
- **数据/枪包兼容**：核心扫描 / 解析 / 索引 / modifier / 兼容读取主链已落地，并已开始被真实消费到 item、tooltip、workbench 摘要、recipe filter、attachment tag 等路径；当前优先级转为**回归修复、缺口补齐与新增消费点接入**。
- **战斗/实体/网络**：服务端 shooter 状态机、网络通道与主消息骨架已落地，并已补齐 ammo 搜索 parity、缺失的 S2C 消息类型与基础客户端事件投递链路；当前优先级转为**回归修复、剩余 parity 收尾与表现层继续消费**。
- **客户端交互 / UI**：本阶段迭代已完成，已把 runtime 下游消费层推进到真实可用程度，并补上 `gun_smith_table` 的基础 `GUI / container / craft` 闭环。当前已落地内容包括：
  - runtime 翻译 / display / recipe filter / workbench 摘要 / attachment tag 的真实客户端消费入口；
  - `GunEvents`、输入桥、overlay、tooltip bridge、`TACZGunPackPresentation` 等客户端桥接层；
  - `GunSmithTableScreen`、`LegacyGuiHandler`、`GunSmithTableContainer`、`ClientMessageGunSmithCraft`、`LegacyGunSmithingRuntime` 组成的 `1.12.2` 工匠台基础 GUI-container-craft 流；
  - `IAttachment`、`AttachmentType`、扩展后的 `IGun` 与相关物品接线，足以支撑 `gun result` 预装附件与 by-hand filter 这轮行为。
- **验证状态**：编译与定向测试已覆盖上述 Client UX / gunsmith runtime 变更；真实 `runClient` 手工 smoke 仍会被外部环境问题阻断——Forge 1.12 对多版本 Kotlin jar 的 ASM 扫描会在模组初始化前失败，因此当前需要把这类阻塞明确记录为**环境问题**，而不是把它误记为本轮迁移失败。

因此，下一阶段最值得投入的主线通常不是“继续重迁数据/战斗/Client UX 主链”，而是：
   1. **Render / Animation 对接**：把 runtime snapshot、gun id/item type、`ServerMessage*` 事件与客户端表现链路全面接通
   2. **剩余 blocked 的 Client UX / Refit 能力**：例如 `GunRefitScreen`、更完整的 attachment slot 操作与 refit backend 状态接口
   3. **第三方兼容与剩余玩法收尾**：在核心主链已成形的前提下，继续补 JEI / KubeJS / 高级玩法与表现边角

补充规则：

- `util/**`、`api/event/**`、`api/entity/**`、`mixin/common/**` 这类交叉支撑代码，**不要单独立项**；直接跟随其主要调用链归入某条主轨道。
- 若某个需求同时跨越“玩法 + 渲染”，默认先迁**玩法真值链路**，再迁表现层；不要先做 UI/渲染壳子。

## 建议的 Prompt / Slash Command 组合

| 目标 | Prompt 文件 | 使用 Agent | 用途 |
|---|---|---|---|
| 迁移前侦察 | `.github/prompts/tacz-scan-system.prompt.md` | `Ask` | 先摸清上游真值源、边界、依赖、Legacy 落点 |
| 基础启动与注册 | `.github/prompts/tacz-migrate-foundation.prompt.md` | `TACZ Migration` | 迁移入口、配置、注册、底座 |
| 数据/枪包兼容 | `.github/prompts/tacz-migrate-data-pack.prompt.md` | `TACZ Migration` | 在已落地 runtime/parser 基础上继续做资源消费、兼容补齐与玩法接线 |
| 战斗/实体/网络 | `.github/prompts/tacz-migrate-combat-network.prompt.md` | `TACZ Migration` | 在已落地 shooter/network 主链基础上继续做 parity 补齐、客户端消费与表现接线 |
| 客户端交互/UI | `.github/prompts/tacz-migrate-client-ux.prompt.md` | `TACZ Migration` | 在已落地输入/HUD/tooltip/`gun_smith_table` 基础 GUI-container-craft 链路上继续做回归修复、剩余 parity 与 blocked backend 收尾 |
| 渲染/动画/客户端资源 | `.github/prompts/tacz-migrate-render-animation.prompt.md` | `TACZ Migration` | 在已落地 runtime、network、Client UX 桥接基础上继续把渲染、动画、模型、scope 与客户端表现链路接通 |
| 第三方兼容 | `.github/prompts/tacz-migrate-compat.prompt.md` | `TACZ Migration` | 迁移 JEI/KubeJS/Cloth/动画器/光影等兼容 |

## 实际使用建议

### 标准工作流

1. 先运行“系统侦察” Prompt，确认：
   - 上游文件边界
   - Legacy 落点
   - 风险点
   - 建议先后顺序
2. 再运行对应的迁移 Prompt，让 `TACZ Migration` 执行完整迁移。
3. 若需求跨两个系统，**优先拆成两个 Prompt**，由协调 Agent 控制先后顺序。
4. 若多人或多 agent 并行迁移，先划清文件边界，再执行构建与验证；不要让多个 agent 争抢同一文件或共用“全量清理”操作。

### 什么时候才值得再拆更多 Agent 文件

只有在以下情况同时成立时，才值得再单独新增 custom agent：

- 某条系统长期高频使用；
- 它需要一套明显不同的工具限制或输出格式；
- 这些差异无法仅靠 Prompt 表达。

在当前阶段，真正最可能值得以后单独拆 Agent 的只有两条：

- **渲染/动画/客户端资源**
- **数据/枪包兼容**

因为它们既体量大，又有独特的验证方式。
