# TACZ-Legacy 项目总览

## 1. 项目目标

`TACZ-Legacy` 旨在将 TACZ 的核心系统迁移到 Minecraft 1.12.2（Forge）并保持：

- 功能体验与关键行为一致
- 枪包/数据包高兼容
- 美术资源高复用
- 渲染框架可扩展、可维护

## 2. 当前工程基线

已完成基础初始化：

- Kotlin + RetroFuturaGradle 工程可用
- Mod 主入口：`src/main/kotlin/com/tacz/legacy/TACZLegacy.kt`
- 代理骨架：`client/ClientProxy.kt`、`common/CommonProxy.kt`
- Mixin 配置：`src/main/resources/mixins.tacz.json`
- 元数据：`mcmod.info`、`pack.mcmeta` 已切换至 TACZ-Legacy

## 3. 建议中的分层架构

- `api`: 对外可依赖的稳定接口与数据模型
- `common`:
  - 注册与生命周期（方块、物品、网络、配置）
  - 枪包解析/校验/兼容转换
  - 射击逻辑、弹道、命中、状态同步
- `client`:
  - 第一人称/第三人称渲染
  - HUD、准星、后坐力视觉反馈
  - 渲染管线与调优模块
- `integration`: 可选模组兼容（JEI、OptiFine 兼容策略等）
- `mixin`: 必要的底层钩子补丁

### 3.1 程序架构与 MC 架构“分离但不断裂”

为兼顾可测试性与维护成本，建议采用 **边界分离（Boundary Separation）**，而不是“硬切割”Minecraft：

1. **Domain（纯 Kotlin 领域层）**
  - 放武器状态机、弹道计算、配件覆盖、枪包归一化等核心规则
  - 禁止直接引用 `net.minecraft.*`
  - 作为单测主战场（高覆盖率）

2. **Application（用例编排层）**
  - 负责 tick、输入事件、网络事件到领域逻辑的编排
  - 依赖抽象端口（Ports），不依赖具体 MC 实现

3. **Infrastructure（MC 适配层）**
  - 提供 `MinecraftWorldPort` / `EntityPort` / `SoundPort` 等适配器
  - 仅这一层接触 Forge 与原版对象
  - 通过适配器把 MC 状态映射到领域对象

4. **Integration（兼容层）**
  - 面向第三方模组与脚本系统
  - 避免第三方依赖渗透至领域层

这套方式让我们既能保留对 MC 运行时的直接可维护对接，又能把核心程序逻辑从“难测的环境耦合”中释放出来。

## 4. 渲染框架方向（高优先）

建议引入统一渲染调度模型：

1. **RenderData 生产层**：逻辑层生成与帧无关的数据快照。
2. **RenderPass 声明层**：定义 OPAQUE / TRANSLUCENT / POST 等阶段。
3. **RenderDispatcher 执行层**：统一调度提交与绘制顺序。
4. **Feature 插件层**：以模块方式注入功能（枪焰、镜片、屏幕特效）。

### 4.1 参考 Kirino-Engine 的可复刻理念（建议）

可参考并“按 1.12 约束降阶实现”的关键点：

1. **帧阶段显式化**（类似 `FramePhase` + FSM）
  - `PRE_UPDATE -> UPDATE -> RENDER_OPAQUE -> RENDER_TRANSLUCENT -> POST_UPDATE -> OVERLAY`
  - 避免渲染流程在事件里隐式膨胀

2. **Headless/Graphics 双视图思想**
  - Headless：只做逻辑计算与 RenderData 生成
  - Graphics：只做 GL 状态、Pass 执行与提交
  - 即便同线程执行，也保持职责分离

3. **RenderPass + Subpass 组合**
  - `RenderPass` 管顺序，`Subpass` 管具体绘制逻辑
  - 每个 Subpass 绑定固定状态描述（可先用轻量 PSO 描述）

4. **高层绘制命令（DrawCommand）**
  - Feature 产出高层命令，底层统一编译为 GL 调用
  - 避免业务逻辑直接拼接 OpenGL 调用

5. **扩展注册点（类似 ModuleInstaller / Extension）**
  - 让新特效通过注册机制接入，不改 dispatcher 主循环

> 注意：TACZ-Legacy 不是 Kirino 的 1:1 搬运。我们应复刻其“显式生命周期 + 可组合渲染结构”理念，同时保留对 1.12 Forge 生态的兼容与回退能力。

## 5. 迁移优先级

1. 数据与资源兼容（枪包加载、命名空间、字段映射）
2. 核心玩法闭环（开火、换弹、命中、配件）
3. 网络同步一致性（客户端预测 + 服务端裁决）
4. 渲染系统重构与性能优化
5. 生态兼容与工具链完善

## 6. 参考仓库

- 目标功能来源：`../TACZ`
- 架构/代码组织参考：`../PrototypeMachinery`
