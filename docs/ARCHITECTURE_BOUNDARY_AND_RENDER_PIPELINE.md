# TACZ-Legacy 架构边界与渲染管线蓝图

> 目标：在不割裂 Minecraft 运行时的前提下，提升可测试性与渲染架构可扩展性。

## 1. 设计原则

1. **分离但不断裂**
   - 领域逻辑尽量不依赖 `net.minecraft.*`
   - Minecraft/Forge 依赖集中到适配层
   - 保留与原版对象的可追踪映射，避免维护期“黑盒化”

2. **显式生命周期**
   - 尽量避免“事件回调里堆逻辑”
   - 生命周期、阶段顺序、扩展点都应显式可见

3. **扩展优先于修改**
   - 新功能应通过注册机制接入，不改主循环

4. **可诊断与可回退**
   - 关键路径必须有 debug 指标
   - 新渲染路径失败时可回退 Vanilla/Legacy 路径

## 2. 架构边界（推荐目录）

```text
src/main/kotlin/com/tacz/legacy/
  api/
  common/
    domain/              # 纯业务规则（可单测）
    application/         # 用例编排（tick/输入/网络）
    infrastructure/
      mc/                # 与 Minecraft/Forge 交互的适配器
      network/
      persistence/
  client/
    render/
      frame/
      pass/
      feature/
      debug/
  integration/
  mixin/
```

## 3. 端口与适配器（Ports & Adapters）

### 3.1 端口示例

- `WorldPort`：读取世界状态、射线检测、方块查询
- `EntityPort`：读取实体状态、位置、生命值、姿态
- `AudioPort`：声音播放与参数控制
- `ParticlePort`：粒子提交
- `TimePort`：时间/DeltaTick
- `RandomPort`：可注入随机源（便于 deterministic 测试）

### 3.2 约束

- `domain` 和 `application` 只能依赖端口接口
- `infrastructure/mc` 实现端口并引用 `net.minecraft.*`
- 在边界层维护 `MC对象 <-> 领域对象` 双向映射

## 4. 单测与稳定性策略

## 4.1 测试分层

1. **领域单测（主力）**
   - 枪械状态机、弹道、后坐力参数、配件覆盖
2. **契约测试（边界层）**
   - `FakeWorldPort` 与 `MinecraftWorldPort` 行为一致性
3. **集成测试（少量）**
   - 网络同步与关键交互链路

## 4.2 建议指标

- 领域层覆盖率目标：`>= 70%`
- 核心状态机分支覆盖：`>= 90%`
- 高风险逻辑（弹道/命中/配件冲突）必须有 golden cases

## 5. 参考 Kirino-Engine 的渲染复刻路线

> 参考的是“设计理念”，不是代码照搬。

## 5.1 关键可复刻思想

1. **FramePhase + FSM**（显式阶段顺序）
2. **Headless/Graphics 双视图**（职责分离）
3. **RenderPass + Subpass**（组合式渲染）
4. **DrawCommand**（高层命令到低层提交）
5. **扩展注册机制**（shader/post/debug HUD）

## 5.2 TACZ-Legacy 建议阶段

```text
PRE_UPDATE -> UPDATE -> RENDER_OPAQUE -> RENDER_TRANSLUCENT -> POST_UPDATE -> RENDER_OVERLAY
```

- `PRE_UPDATE`：捕获状态、更新帧上下文
- `UPDATE`：生成 RenderData 快照
- `RENDER_OPAQUE`：不透明提交
- `RENDER_TRANSLUCENT`：透明与特效提交
- `POST_UPDATE`：后处理、状态恢复
- `RENDER_OVERLAY`：HUD 与调试信息

## 5.3 最小实现接口（建议）

- `FramePhase`
- `FramePhaseFSM`
- `RenderContext`
- `RenderPass`
- `Subpass`
- `DrawCommand`
- `RenderFeature`
- `RenderFeatureRegistry`
- `RenderPipelineConfig`（开关/回退/诊断）

## 5.4 与 1.12 现实约束对齐

- TESR/实体渲染不做激进替换，先走“提交点统一 + 顺序可控”
- 渐进接管：先客户端枪械相关渲染，再扩展到更多路径
- 新旧路径并存，支持运行时开关和 A/B 对比

## 6. 迭代建议（4 个里程碑）

1. **R0（骨架）**：FramePhase/FSM + 空 Pass 管线 + Debug HUD
2. **R1（接入）**：枪械一人称渲染迁入 Subpass
3. **R2（扩展）**：后处理与透明排序统一
4. **R3（稳定）**：性能指标、回退验证、兼容性回归

## 7. 非目标（当前阶段）

- 不直接引入复杂 GPU-driven meshlet 全套技术栈
- 不重写所有 Vanilla 渲染路径
- 不在首轮迁移中追求“最高 FPS”，先保证行为一致与可维护
