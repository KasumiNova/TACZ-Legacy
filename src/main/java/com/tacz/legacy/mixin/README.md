# TACZ-Legacy Mixin 包约定（Java 模块）

> 按项目约定，Mixin 类统一放在 `src/main/java`，避免与 Kotlin 业务层耦合。

- 根包：`com.tacz.legacy.mixin`
- Minecraft 原版目标放在：`com.tacz.legacy.mixin.minecraft`
- Forge/三方兼容目标按模块拆分子包（例如 `mixin.jei`、`mixin.optifine`）
- 配置文件：`src/main/resources/mixins.tacz.json`

建议：

1. 单个 Mixin 只解决一个职责，避免“巨型 Mixin”。
2. 优先使用 `@Inject` + `@At`，只有在必要时才用 `@Overwrite`。
3. 为兼容性逻辑添加条件守卫（mod loaded / side checks）。
