# Copilot instructions — TACZ-Legacy

## 项目定位
- 这是 **TACZ 在 Minecraft 1.12.2 Forge 的 Kotlin 移植工程**。
- 迁移目标不是“功能删减版”，而是以 **功能一致性、数据兼容性、可扩展渲染架构** 为核心。

## 硬约束（优先级最高）
1. **美术素材可复用**：资源命名、目录结构与格式转换策略应优先保证复用。
2. **枪包/数据包兼容**：外部包格式尽量保持兼容；若存在版本差异，必须提供迁移层或适配器。
3. **功能一致性**：同名功能在行为上应与 TACZ 对齐（允许底层实现不同）。
4. **渲染管线重构**：必须走可扩展框架化设计，而非散点式 TESR/事件堆叠。
5. **架构分离但不割裂 MC**：核心程序逻辑尽量去 MC 依赖以支持单测；但必须保留清晰、可维护的 MC 适配层，不做不可逆硬分叉。

## 技术与构建
- Forge: `1.12.2-14.23.5.2847`
- Kotlin: Forgelin-Continuous
- Build: RetroFuturaGradle
- Mixin: MixinBooter + `mixins.tacz.json`

常用命令：
- 初始化：`gradlew setupDecompWorkspace`
- 客户端：`gradlew runClient`
- 服务端：`gradlew runServer`
- 构建：`gradlew build`

## 代码组织约定（参考 PrototypeMachinery 风格）
- 按职责分层：
  - `api/`：稳定对外接口（尽量保持简洁）
  - `common/`：跨端核心逻辑（数据、注册、网络协议定义）
  - `client/`：渲染、输入、客户端 UI
  - `integration/`：第三方兼容层（可选依赖）
  - `mixin/`：最小侵入式补丁，按目标域分包
- Mixin 源码统一放在 `src/main/java/com/tacz/legacy/mixin/**`（不要放在 Kotlin 源集）。
- 禁止把高层业务逻辑直接塞进 Mixin。
- 建议增加边界层：
  - `domain/`：纯 Kotlin 领域逻辑（禁止 `net.minecraft.*`）
  - `application/`：用例编排
  - `infrastructure/mc/`：Minecraft/Forge 适配器

## 渲染框架约定
- 渲染层按“阶段（Stage）+ 提交（Submit）+ 执行（Execute）”组织：
  - 逻辑系统产出 `RenderData`
  - 渲染系统只消费 `RenderData`
  - 批处理、透明顺序、后处理（如 bloom）在统一调度器中执行
- 所有新增渲染特性必须可开关、可回退、可诊断（debug HUD 或日志）。
- 可参考 Kirino-Engine 的思想：`FramePhase/FSM`、`RenderPass+Subpass`、`Headless/Graphics` 双视图。

## 兼容性实践
- 与枪包兼容相关的字段/键名不要随意改名。
- 若不得不改，新增“兼容读取 + 新格式写入”的过渡策略。
- 资源路径尽量保持 `assets/tacz/...` 语义一致。

## 变更原则
- 优先小步提交：每次改动应可编译、可验证。
- 避免无关重构；先实现兼容，再做内部优化。
- 修改构建开关（`use_mixins/use_coremod/...`）后，提醒重新 setup 工作区。
