# TACZ-Legacy

`TACZ-Legacy` 是 `TACZ` 的 **Minecraft 1.12.2 Forge 移植工程**，并采用 **Kotlin + RetroFuturaGradle** 技术栈。

项目目标：

- 迁移并保持 TACZ 的核心玩法、交互与功能一致性
- 最大化复用现有美术素材与资源命名体系
- 让 1.20 时代的“模组数据包 / 枪包”尽可能平滑迁移并保持兼容
- 重构 1.12.2 渲染侧架构，提供可扩展的渲染管线

---

## 当前阶段

当前仓库已完成：

- Kotlin 1.12.2 工程基础配置初始化
- 模组主入口/代理骨架建立
- Mixin 基础环境初始化（`mixins.tacz.json`）
- 项目级 Copilot 指导文件初始化（`.github/copilot-instructions.md`）
- 迁移蓝图文档初始化（`docs/MIGRATION_PLAN.md`）

---

## 技术栈

- Minecraft Forge `1.12.2`（`14.23.5.2847`）
- Kotlin（Forgelin-Continuous）
- RetroFuturaGradle
- Sponge Mixin（通过 MixinBooter 接入）

---

## 快速开始

1. 初始化反编译工作区：`gradlew setupDecompWorkspace`
2. 导入/刷新 Gradle 工程（IDEA）
3. 运行开发环境：`gradlew runClient` / `gradlew runServer`

> 若修改了 `gradle.properties` 中的 `use_mixins/use_coremod/use_access_transformer` 等开关，建议重新执行 setup 并刷新 Gradle。

---

## 文档导航

- 项目总览：`PROJECT_OVERVIEW.md`
- 迁移蓝图：`docs/MIGRATION_PLAN.md`
- 架构与渲染蓝图：`docs/ARCHITECTURE_BOUNDARY_AND_RENDER_PIPELINE.md`
- Copilot 协作指引：`.github/copilot-instructions.md`

---

## 设计原则（迁移期）

1. **兼容优先**：先保证行为与数据兼容，再做结构升级。
2. **分层迁移**：资源/数据层、逻辑层、渲染层分阶段推进。
3. **可观测性**：关键系统在迁移期必须有日志与诊断开关。
4. **可回滚**：高风险改动（尤其渲染）要支持灰度开关。
5. **分离但不断裂 MC**：核心逻辑尽量去 MC 依赖以支持单测，同时保留清晰 MC 适配层。

---

## License

继承上游 TACZ 的版权与许可约束；本仓库仅包含移植开发所需代码骨架与文档。
