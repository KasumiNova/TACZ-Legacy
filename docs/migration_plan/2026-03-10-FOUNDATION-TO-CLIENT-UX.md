# 2026-03-10 / FOUNDATION-TO-CLIENT-UX 分类分册

> 本分册承接原大文档中 **Foundation / Data / Combat / Audio / Client UX / 非 Render 验证** 的详细阶段状态。  
> **文件名中的日期表示本分类文档的建档/结构整理日期，不代表正文中的所有开发都发生在 2026-03-10。**  
> 当一份分类分册汇总多天开发内容时，真实开发日期必须继续在正文小节中明确标注；全局入口请回到 `docs/migration_plan/MAIN.md`。

## 适用范围

本分册集中记录以下轨道：

- 基础启动与注册
- 数据/枪包兼容
- 战斗/实体/网络
- 音频系统 / smoke 守门
- 客户端交互 / UI / Refit / HUD
- 非 Render 主责的 gameplay parity、脚本、验证与环境阻塞

## 当前阶段总览（截至 2026-03-10）

### Foundation

- 第一波基础启动、注册、烟测脚本与基础测试已进仓。
- 当前不再是主阻塞轨道；更多承担共享 hook / 注册 / smoke 守门职责。

## 2026-03-11：构建产物纯库 shadow 收口

### 已落地

- `build.gradle.kts` 已切换到真正可发布的 shadow 打包链，而不是保留一个未被依赖消费的 `embed` 空壳配置。
- 纯 Java 运行时库改为随 TACZ-Legacy 正式产物内嵌，并统一重定位到 `com.tacz.legacy.shadow.*`：
  - `org.luaj:luaj-jse`
  - `org.joml:joml`
  - `org.apache.commons:commons-math3`
- `reobfJar` 现在直接消费 `shadowJar` 输出，因此 `build/libs/TACZ-Legacy-<version>.jar` 已是带重定位依赖的正式发布件。
- obfuscated 运行目录已排除上述 group 的自动外部注入，避免正式产物已经自带这些类时，又在 `runObf*` 路径额外挂一份原包名副本。

### 当前边界

- 本轮只内嵌**普通 Java 运行库**，没有把 `Forgelin-Continuous`、`MixinBooter`、`CTM`、`CodeChickenLib`、`Lumenized` 这类 Forge 模组依赖打进主 jar。
- 这样做的目的是：
  1. 让最终产物自带 Lua / 数学 / 向量运行库；
  2. 避免把其他本就应作为模组独立加载的依赖错误地塞进主 jar；
  3. 避免与其他模组可能携带的同包名 `luaj` / `joml` / `commons-math3` 发生类冲突。

### 本轮验证

- `./gradlew --no-daemon clean shadowJar reobfJar` 通过。
- `./gradlew --no-daemon test` 通过。
- `build/libs/TACZ-Legacy-0.1.0.jar` 已确认包含：
  - `com/tacz/legacy/shadow/org/luaj/**`
  - `com/tacz/legacy/shadow/org/joml/**`
  - `com/tacz/legacy/shadow/org/apache/commons/math3/**`
- 同一产物中未再出现原包名：
  - `org/luaj/**`
  - `org/joml/**`
  - `org/apache/commons/math3/**`
- `javap` 抽查 `TACZClientAssetManager` 已确认字节码中的 LuaJ 引用改写到 `com.tacz.legacy.shadow.org.luaj.*`。

### 仍需记录的 obf 运行阻塞

- 尝试用 `runObfClient` 做 focused smoke 时，当前环境仍在进入世界前被既有 coremod / mixin 链路挡住：
  - `Coremod TACZMixinLoader: Unable to class load the plugin com.tacz.legacy.mixin.TACZMixinLoader`
  - `NoClassDefFoundError: zone/rong/mixinbooter/IEarlyMixinLoader`
- `run/obfuscated/mods/` 中已能看到 `TACZ-Legacy-0.1.0.jar` 与 `mixinbooter-10.7.jar`，因此这更像是仓库当前 obfuscated 启动链本身的既有阻塞，而不是本轮 pure-lib shadow 改动引入的新回归。

## 2026-03-11：坏枪包 zip 启动保底

### 问题根因

- `DefaultGunPackExporter.exportIfNeeded(...)` 会在 `preInit` 阶段直接触发 `TACZGunPackRuntimeRegistry.reload(...)`。
- 旧链路里只对“单个资源条目解析失败”做了 `try/catch`；但 `.zip` 枚举本身 (`Collections.list(zipFile.entries())`) 不在保护范围内。
- 一旦枪包 zip 含有 `MALFORMED` entry 名称或其他底层 zip 枚举异常，整个 `reload()` 会在启动早期直接炸掉，导致**一个坏包拖垮全部枪包加载与基础启动**。

### 本轮收口

- `TACZGunPackScanner` 新增单候选包级别的保护：
  - 目录包或 zip 包任一候选读取失败时，只记录 issue 并跳过该包；
  - 其余健康枪包继续扫描、解析、注册。
- `TACZZipPackSource` 现在会把 zip 枚举/读取时的 `IllegalArgumentException` 包一层带文件名上下文的 `IllegalStateException`，日志不再只剩一行抽象 `MALFORMED`。
- `TACZGunPackRuntimeRegistry.reload(...)` 额外增加总兜底：如果整轮扫描在更外层发生未预期异常，则保留上一份 runtime snapshot，并把失败原因追加到 `issues`，避免把内存态直接打空。
- 新增回归测试 `scanner skips malformed zip pack and keeps valid packs loaded`，用真实构造的坏 zip 证明单坏包不会阻断正常枪包。

### 本轮验证

- `./gradlew --no-daemon test --tests '*TACZGunPackRuntimeTest'` 通过。
- `./gradlew --no-daemon test` 通过。
- focused smoke 运行日志：`build/smoke-tests/runclient-focused-smoke-20260311-211405.log`
  - 已出现 `Skipping pack broken_pack.zip due to load failure`
  - 已出现 `Pack broken_pack.zip skipped because it could not be read safely (...)`
  - 已出现 `Loaded 4 pack(s)...`
  - 已出现 `Default gun pack export: ...`
  - 已出现 `[FoundationSmoke] PREINIT complete`
- 这说明**真实客户端启动链**已经跑过 `preInit -> gun pack export -> runtime reload -> 坏 zip 跳过 -> 基础注册继续` 这条目标路径；本轮验证重点是启动保底 reachability，而不是进世界后的 render marker。

## 2026-03-11：客户端私有成员访问改为 obf-safe mixin invoker

### 问题根因

- `GunPackSoundResourcePack.java` 之前直接反射 `Minecraft.defaultResourcePacks`。
- `FocusedSmokeClientHooks.kt` 之前直接反射：
  - `Minecraft.clickMouse()`
  - `Minecraft.sendClickBlockToController(boolean)`
  - `Minecraft.leftClickCounter`
- 这些写法依赖 **MCP 开发环境名**；在生产环境未反混淆运行时，会因为成员名变成 `field_...` / `func_...` 而直接找不到，导致客户端启动或 smoke 路径崩溃。

### 本轮收口

- 新增 `mixin/minecraft/client/MinecraftInvoker.java`，通过 Mixin 的 `@Accessor` / `@Invoker` 暴露：
  - `defaultResourcePacks`
  - `leftClickCounter`
  - `clickMouse`
  - `sendClickBlockToController`
- `GunPackSoundResourcePack` 已不再使用裸反射读取 `defaultResourcePacks`，改为通过 `MinecraftInvoker` 访问。
- `FocusedSmokeClientHooks` 的左键抑制探针也已不再通过 MCP 名反射调用私有成员，而是通过 `MinecraftInvoker` 直接访问。
- `mixins.tacz.json` 已登记新的 `client.MinecraftInvoker`，与现有 `EntityRendererInvoker` 路线保持一致。

### 本轮验证

- 本地 SRG 映射已核对到目标成员：
  - `defaultResourcePacks -> field_110449_ao`
  - `leftClickCounter -> field_71429_W`
  - `clickMouse -> func_147116_af`
  - `sendClickBlockToController -> func_147115_a`
- `./gradlew --no-daemon classes` 通过。
- `./gradlew --no-daemon test` 通过。
- 运行态验证（真实客户端链路）补充如下：
  - `build/smoke-tests/runclient-focused-smoke-20260311-213605.log`
    - 以 `TACZ_AUDIO_BACKEND=vanilla-minecraft` 运行 focused smoke。
    - 已明确打到 `Refreshing resources after gun pack sound bridge update (domains=[...])`。
    - `SimpleReloadableResourceManager` 的资源包列表中已出现 `TACZ Gun Pack Sounds`。
    - 这证明 `GunPackSoundResourcePack.synchronize(..., true)` 的**安装分支**已在真实客户端链路执行。
    - 该次运行随后被既有 vanilla 音频链问题截断：`CodecJOrbis` / `Error reading the header`，最终 `runClient` 以 `137` 退出；这不是本轮 accessor/invoker 改动引入的新失败。
  - `build/smoke-tests/runclient-focused-smoke-20260311-213737.log`
    - 以 `TACZ_AUDIO_BACKEND=direct_openal` 运行 focused smoke。
    - 已明确打到 `LEFT_CLICK_SUPPRESSED target=BlockPos{x=657, y=8, z=84} ... swing=false hittingBlock=false temporary=true`。
    - 这证明 `FocusedSmokeClientHooks` 中改为 `MinecraftInvoker` 的 `clickMouse` / `sendClickBlockToController` / `leftClickCounter` 路径已在真实客户端链路执行成功。
    - 该次运行随后被既有 focused-smoke reload 断言截断：`FAIL reason=reload_timing_delta_-1316`；这也是与本轮反射修复无关的旧问题。

### 结论

- 这两处生产环境敏感的 Minecraft 私有成员访问现已从“硬编码 MCP 名反射”收口为“Mixin remap 管理的 accessor/invoker”。
- 后续若还需要访问 obfuscated 的 Minecraft 私有字段/方法，应优先复用这种 Mixin accessor 路线，而不是再次直接 `getDeclaredField("devName")` / `getDeclaredMethod("devName")`。

### 数据 / 枪包兼容

- 核心扫描 / 解析 / 索引 / modifier / 兼容读取主链已落地。
- 已开始被 item、tooltip、workbench 摘要、recipe filter、attachment tag 等真实路径消费。
- 当前优先级已从“主链是否存在”转为：
  - 回归修复
  - 缺口补齐
  - 新消费点接入

### 战斗 / 实体 / 网络

已落地的主链结论：

- 服务端 shooter 状态机、网络通道与主消息骨架已落地。
- ammo 搜索 parity、缺失的 S2C 消息类型与基础客户端事件投递链路已补齐。
- `burst_data` 读取、客户端 fire-mode 输入分流、服务端 `BurstFireTaskScheduler` 连发调度已按上游收口。
- focused smoke 已验证 `tacz:b93r` + `tacz:rpg7` 的实机链路可打出：
  - `GUN_FIRE side=SERVER gun=tacz:b93r count=1/2/3`
  - `REGULAR_PROJECTILE_GATE_OPEN fireCount=3/3`
  - 最终 `PASS`

#### 已落地的 2026-03-08 / 2026-03-09 combat parity 项

1. burst fire 客户端调度完善
2. draw / holster / reload 音效补齐
3. 弹道计算与子弹渲染修正（`EntityKineticBullet` 自定义 `onUpdate`）
4. 数据脚本系统（Lua data script）完整接线
5. 特殊弹药消耗修正（含 rc 无弹匣开栓与 fuel 类型兼容）
6. 完整过热系统（`heatPerShot` / 冷却延迟 / 过热锁定 / 过热时间）
7. 命中 / 击杀反馈系统（Hit / Kill Feedback）
8. 客户端自动拉拴缺失补齐

当前 combat 侧仍建议继续关注：

- heat → RPM / cadence 真实接线
- 少量边界 fire-mode / script / server accept gate 收尾

## 音频系统 / smoke 守门

### 当前结论

- 专用音频 runtime 的阶段 A/B/C 仍然是当前真值。
- 2026-03-08 曾发现工作树发生过部分回退：
  - `TACZOpenALSoundEngine` 与相关测试文件还在
  - 但 `TACZAudioBackendMode`、`TACZAudioRuntime`、`GunSoundPlayManager` 与 `ClientProxy` 的关键接线被退回旧 `SoundHandler` 主链
- 现已重新对齐：
  - `legacy-minecraft` / `legacy` / `minecraft` 属性别名重新解析到 `direct_openal`
  - 旧链路退回 `vanilla-minecraft` 调试 fallback
  - `GunPackSoundResourcePack` 只在显式 fallback 模式下启用

### 运行验证

- `./gradlew --no-daemon compileKotlin compileJava` 已通过。
- `build/smoke-tests/runclient-focused-smoke-20260308-190637.log` 已给出 direct backend 运行证据：
  - `OpenAL direct audio ready: loaded=1515 failed=0 totalBuffers=1515`
  - `Audio manifest ready: 1829 sounds (...), backend=direct_openal`
  - `[FocusedSmoke] AUDIO_PLAYBACK_OBSERVED ... class=TACZOpenALSoundHandle`
  - 多条 `backend=direct_openal result=SUBMITTED_TO_BACKEND`
  - 最终 `PASS`
- 同轮复核中已确认不再出现：
  - `CodecJOrbis`
  - `Error reading the header`
  - `Unable to acquire inputstream`

### 当前剩余重点

- 继续清理 `minecraft:*` 但资源缺失的动画音效引用
- direct backend 的缓存 / 诊断文档收口

## Client UX / HUD / Refit / Gunsmith

### 已落地主链

- runtime 翻译 / display / recipe filter / workbench 摘要 / attachment tag 已有真实客户端消费入口
- `GunEvents`、输入桥、overlay、tooltip bridge、`TACZGunPackPresentation` 等客户端桥接层已接上
- `gun_smith_table` 已具备基础 `GUI / container / craft` 闭环
- `IGun` / `IAttachment` 与相关物品接线已足以支撑 `gun result` 预装附件与 by-hand filter 行为

### Refit backend / accessor parity

已补齐：

- builtin attachment
- aim zoom / zoom number
- laser color 访问语义
- `TACZGunPackPresentation` 的 builtin attachment / iron zoom / attachment zoom / laser config helper
- `RefitAttachmentAccessorParityTest` 回归

### `GunRefitScreen` 当前基线

当前工作树的真实生产基线是：

- 旧版稳定绝对坐标布局
- `LegacyGunRefitRuntime` + `ClientMessageRefitGun*` + `ClientMessageUnloadAttachment` + `ClientMessageLaserColor` + `ClientMessagePlayerFireSelect`
- 可用附件槽、候选附件列表、卸下、fire mode 切换、laser 颜色预览提交与属性面板均已重新进入生产路径
- `refit_view` / `refit_<type>_view` 节点驱动的小视口预览已接上
- `FocusedSmokeRuntime` / `FocusedSmokeClientHooks` 已支持 `-Dtacz.focusedSmoke.refitPreview=true`

focused smoke 已验证：

- `REFIT_SCREEN_OPEN`
- `REFIT_PROPERTIES_TOGGLE`
- `REFIT_SLOT_FOCUS`
- `REFIT_ATTACHMENT_CLICK`
- `REFIT_ATTACHMENT_APPLIED`
- `REFIT_PREVIEW_COMPLETE`
- 最终 `PASS`

### 当前 Client UX 结论

- `GunRefitScreen` **不是完全不可用**。
- 但它仍未达到上游沉浸式 world-to-screen 开场、遮罩、焦点过渡与视觉 polish。
- 后续应在当前稳定基线之上继续补体验，而不是再次从零重构布局。

## 2026-03-09：脚本系统完整对齐

### 问题背景

此前落地的脚本系统只实现了 `shoot` hook，且存在缺失 API、吞异常问题，导致脚本枪开火后不出子弹。

### 本轮收口内容

1. **`TACZDataScriptManager` 重写**
   - 注入全局 Lua 常量
   - 脚本加载 fail-fast，不再静默吞 `LuaError`
2. **`GunDataAccessor / GunCombatData` 扩展**
   - `scriptParams`
   - `boltFeedTimeS`
3. **`TACZGunScriptAPI` 完整对齐**
   - 补齐上游 `ModernKineticGunScriptAPI` 主要 API
   - 新增 `create()` 工厂方法
   - `performSingleFire` 支持 `handle_shoot_heat` hook
4. **`BurstFireTaskScheduler` 支持延迟启动重载**
5. **`LivingEntityShoot` / `LivingEntityReload` / `LivingEntityBolt` 脚本 hook 全面接线**
6. **`LivingEntityMixin (tacz$tickHeat)`**
   - 添加 `tick_heat` hook 分发

### 覆盖枪包脚本

- Applied Armorer：`hmg22`, `win_win`, `melee_task_manager`
- TRIS-dyna：`cf007`, `cms92x`, `fs2000`, `noammo`, `rc`, `rc_ar`
- Cyber Armorer：`carnage`, `grad`, `m2038`, `mantis_blade`

### 仍待后续

- `calcSpread` hook 仅少数脚本使用，当前未完整实现

## 2026-03-09：fuel / inventory 枪械创造模式无法射击

### 根因

`LivingEntityShoot.kt` 之前对 `hasInventoryAmmo` 硬编码传入 `true`，没有按上游使用 `gunOperator.needCheckAmmo()`。这会导致：

- 客户端认为可射击，播放动画与音效
- 服务端仍执行真实库存检查
- 创造模式玩家没有实际弹药物品
- 最终被服务端判成 `NO_AMMO`

### 修复结论

- `hasInventoryAmmo` / `reduceAmmoOnce` / `consumeAmmoFromPlayer` 已统一按 `needCheckAmmo` 语义收口
- `TACZGunScriptAPI` 路径已确认没有同类 bug
- focused smoke：`ch104`、`cms92x`、`s10dmd` 均已回到 PASS

## 2026-03-09：HUD 弹药格式兼容迁移（AmmoCountStyle）

### 已落地

- 新增 `AmmoCountStyle.java`
- `GunDisplay.java` / `GunDisplayInstance.java` 新增 `ammo_count_style`
- `LegacyClientOverlayEventHandler.kt` 已按 `NORMAL` / `PERCENT` 渲染 HUD
- 4 项新增回归测试通过

### 结论

Legacy 已不再强制所有 HUD 都走三位绝对数格式，`percent` 模式现在可被真实消费。

## 2026-03-10：HUD / ASCII / spread / recoil parity

### HUD under GUI 与 ASCII helper

- 右下枪械 HUD 已从共享的 `LegacyInputExtraCheck.isInGame()` 门禁中拆出
- 当前只要求 `player/world` 存在且未按 `F1` 隐藏 GUI
- ASCII 路径从全局 `FontRendererMixin` 回退为显式 helper：
  - 新增 `client/foundation/TACZAsciiFontHelper.java`
  - 在 HUD / GUI / tooltip / `TextShowRender` 等调用点只对纯 ASCII 文本临时关闭 Unicode flag
  - 渲染完成后立即恢复
- `mixins.tacz.json` 中的 `FontRendererMixin` 已移除

### spread / recoil / property resolver

- 新增 `TACZGunPropertyResolver.kt` 与 `TACZBulletSpreadResolver.kt`
- `fire_mode_adjust`
- heat `min/max_inaccuracy`
- `crawl_recoil_multiplier`
- attachment `inaccuracy/recoil` modifier
- 递归 attachment tag 匹配（含 `tacz:intrinsic/slug`）
均已重新接回 `LivingEntityShoot.kt` 与 `TACZGunScriptAPI.kt`

### camera recoil

- 新增 `TACZCameraRecoilHandler.kt`
- 由 `LegacyClientShootCoordinator.kt` 在本地击发确认后初始化 recoil spline
- `ServerMessageGunFire.kt` 对本地玩家回声做跳过，避免服务端广播重复叠加

### 当前限制

- `heat min/max_rpm_mod` 已进入 `GunDataAccessor.kt` 与 `TACZGunScriptAPI.kt` getter
- 但尚未改变真实发射 cadence
- 这项后续应交给专门的 heat cadence Prompt 收口

## 2026-03-10：命中 / 击杀反馈系统

### 已落地范围

完整移植或对齐了：

- `ClientHitMark`
- `RenderCrosshairEvent` 中 hit marker 渲染部分
- `KillAmountOverlay`
- `ServerMessageGunHurt` / `ServerMessageGunKill`
- `EntityHurtByGunEvent` / `EntityKillByGunEvent`
- 命中 / 爆头 / 击杀音效路径

### 关键落点

- `GunEvents.kt`
- `ServerMessageGunHurt.kt` / `ServerMessageGunKill.kt`
- `TACZNetworkHandler.kt`
- `TACZClientGunSoundCoordinator.kt`
- `GunDisplayInstance.java`
- `LegacyHitFeedbackState.kt`
- `LegacyClientHitMarkHandler.kt`
- `LegacyClientOverlayEventHandler.kt`
- `LegacyEntities.kt`
- `ClientProxy.kt`

### 验证结论

- `LegacyHitFeedbackStateTest` 与 `GunDisplayInstanceHitFeedbackSoundTest` 已通过
- focused smoke 已打到：
  - `HIT_FEEDBACK_TARGET_READY`
  - `KILL_FEEDBACK_TRIGGERED`
  - 最终 `PASS`

## 验证状态与环境阻塞

### 已确认通过

- `./gradlew test` 当前全量通过
- focused smoke 历史 PASS 已覆盖：
  - `tacz:timeless50`
  - `tacz:b93r`
  - `tacz:rpg7`
  - `trisdyna:cms92x`
  - `trisdyna:ch104`
  - `tacz:s10dmd`
- HUD / ASCII helper 路径已有 focused smoke marker 与截图证据

### 仍需记录为环境阻塞的事项

- 针对 HUD-under-GUI 与 camera recoil 的额外 smoke 截图尝试，被 Forge 1.12 对多版本 Kotlin jar 的 ASM 扫描问题挡在模组初始化前
- 这类问题应记录为**环境阻塞**，不要误判为功能回退

## 交接建议

后续如果任务继续集中在以下问题，请优先转交对应专项 Prompt：

- heat → RPM / cadence：`tacz-stage-combat-heat-rpm-cadence.prompt.md`
- Refit 沉浸式 polish：`tacz-stage-client-ux-refit-immersive-polish.prompt.md`
- JEI / KubeJS：继续留在 compat 轨道，不在本分册继续展开
