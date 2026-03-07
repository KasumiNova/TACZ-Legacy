---
name: "TACZ Stage Audio Engine Compatibility"
description: "Build a TACZ-only gun-pack audio engine that no longer depends on Minecraft 1.12 resource refresh, legacy codec behavior, or real audio devices during smoke validation."
agent: "TACZ Migration"
argument-hint: "填写枪包音频格式不兼容、focused smoke 卡死、动画/网络音效统一后端、解码缓存、验收约束"
---
迁移并落地 `TACZ-Legacy` 的**专用枪包音频引擎**，让 gun-pack 音频不再被 `Minecraft 1.12.2` 原版声音系统、资源刷新和旧编解码行为绑死。

本 Prompt 默认以 `docs/TACZ_AUDIO_ENGINE_PLAN.md` 为设计基线执行；若实现偏离该文档，必须解释原因。

## 新一轮对比测试反馈（必须覆盖）

用户当前的直观反馈非常明确：**“武器没音效。”** 本轮不允许只用 `diagnostic/null backend` 的日志证明“链路存在”。必须明确回答以下问题：

1. 现在是**声音请求根本没提交到 runtime**，还是**runtime 收到了请求，但 playback backend 仍未真正播出**？
2. 当前默认客户端到底跑在 `diagnostic/null/legacy-minecraft/dedicated` 的哪种 backend 上？如果默认仍是只记日志不出声，就不能把“无音效”归咎为用户错觉。
3. `draw / inspect / reload / shoot / server broadcast` 这些入口里，至少哪些已经能在真实客户端听到，哪些仍然只有日志/manifest 证据？

## 当前危机与必须修复的严重问题

1. **当前枪包音频仍然强绑定到 1.12 原版资源系统，已经开始阻塞 focused smoke。**
   - 当前真实链路是：
     - `TACZClientAssetManager.reload()` 收集 `soundResources`
     - `GunPackSoundResourcePack.installOrUpdate(soundResources)` 把枪包音频挂进 `Minecraft.defaultResourcePacks`
     - 域变化时触发 `minecraft.refreshResources()`
     - `GunSoundPlayManager` 再通过 `GunSoundInstance` / `SoundHandler` 播放
   - 这意味着：**枪包音频能不能播、会不会卡住 smoke、会不会因为编解码差异挂死，全部取决于 1.12 的老声音栈。**
   - 本轮必须把 TACZ gun-pack 音频从这条链上拆出来，至少做到“专用后端 + 可控 fallback + smoke-safe 验证”。

2. **动画音效与网络广播音效必须走同一套 TACZ 音频运行时，而不是两条各玩各的。**
   - 当前有两条真实入口：
     - `ObjectAnimationSoundChannel.playSound(...)`
     - `ServerMessageSound.Handler`
   - 两者现在都调 `GunSoundPlayManager.playClientSound(...)`，这正是统一切口。
   - 不允许只让动画 keyframe sound 走新引擎、把 `ServerMessageSound` 留在旧链路；否则 multiplayer / 第三人称 / 命中反馈还会继续分裂。

3. **focused smoke / CI 必须可以在无音频设备、无真实播放的情况下完成“音频链路验证”。**
   - 必须提供 `NullAudioBackend` 或等价静默后端。
   - 必须提供 preflight / diagnostic 模式，能回答：
     - 哪些音频资源被引用到
     - 哪些资源存在
     - 哪些资源可被专用引擎解码
     - 哪些资源仍不兼容
   - 目标不是“烟测里真的听到声音”，而是**烟测不再因为音频卡死，同时能证明音频链已经走通。**

4. **不能把问题伪装成“手工重编码资源”来糊过去。**
   - 不允许通过手工批量改枪包资产、偷偷删音频引用、或把不兼容资源简单跳过不报错来假装修好。
   - 真正要做的是：**设计并接入专用音频运行时、解码/探测/缓存/诊断链。**

## 默认关注范围

- `src/main/java/com/tacz/legacy/client/sound/**`
- 任何新增的 `src/main/java|kotlin/com/tacz/legacy/client/audio/**`
- `src/main/kotlin/com/tacz/legacy/client/resource/TACZClientAssetManager.kt`
- `src/main/java/com/tacz/legacy/api/client/animation/ObjectAnimationSoundChannel.java`
- `src/main/kotlin/com/tacz/legacy/common/network/message/event/ServerMessageSound.kt`
- `src/main/kotlin/com/tacz/legacy/client/foundation/**`
- `src/main/kotlin/com/tacz/legacy/common/foundation/**`
- `scripts/runclient_focused_smoke.sh`
- `build.gradle.kts`

## 执行要求

- 以**专用 TACZ 音频后端**为目标，不要试图替换整个 Minecraft 环境音 / 原版音效系统。
- 允许保留 `Minecraft` 声音后端作为**过渡 fallback**，但 TACZ gun-pack 音频必须有独立可选的 backend，且 focused smoke 默认不依赖它。
- 优先复用 `GunSoundPlayManager` 作为统一 facade，不要再引入第二套对外公开的音频入口。
- 必须建立**资源清单 / probe / preflight / 诊断**能力，而不是只在播放时报错。
- 必须支持**静默 backend** 和**可脚本化的 smoke/CI 模式**。
- 但“静默/诊断模式能跑 smoke”**不等于**“用户能听到声音”。本轮必须区分 smoke-safe backend 与真实可听播放 backend，避免继续把两者混为一谈。
- 若引入第三方解码库，必须说明：
  - Java 8 / Forge 1.12.2 可用性
  - 是否纯 Java / 是否引入原生依赖
  - 许可证是否适合当前仓库
- 不允许保留“每次枪包音频域变化都 `refreshResources()`”作为最终方案。
- 可以加临时日志、focused smoke 标记、诊断输出，但要可维护、可回退。

## 本轮最值得优先完成的里程碑

1. **音频资源清单与预检层**
   - 让 `TACZClientAssetManager` 在 reload 后产出 TACZ 专用音频 manifest / probe 结果。
   - 明确记录每个 sound id 的来源（display / animation / server sound）、资源定位结果、探测状态、解码兼容性。

2. **后端抽象层**
   - 把 `GunSoundPlayManager` 后端抽象提升为真正可切换的 runtime backend。
   - 至少具备：`null/diagnostic`、`legacy-minecraft`、`dedicated` 三种模式中的前两种；第三种若本轮能落更好，但不能让 smoke 继续被 legacy path 卡死。

3. **动画音效与网络音效统一接入**
   - 证明 `ObjectAnimationSoundChannel` 与 `ServerMessageSound` 都通过同一 runtime/backend 提交播放请求。
   - 至少要能给出一条动画 keyframe sound 和一条 server broadcast sound 的运行证据。

4. **focused smoke 音频守门**
   - `scripts/runclient_focused_smoke.sh` 或其下游 runtime 需要能切到静默/预检模式。
   - smoke 输出必须能明确回答：资源预检是否通过、是否仍存在不兼容音频、测试到底卡在哪一段。

5. **真实可听播放根因说明**
   - 本轮必须明确回答当前“武器没音效”到底是**没对接**还是**实现有 bug**。
   - 若 playback 仍未真正落地，也要给出最短闭环路径，而不是继续把“request 已记录”当成完成。

## 本 Prompt 不是干什么的

- 不是用来修动画状态机 trigger 本体
- 不是用来补 projectile / 爆炸 / combat 逻辑
- 不是用来重做 GUI / HUD
- 不是用来批量修改枪包资源文件规避问题

如果问题本质是“动画没触发”“子弹没生成”“GUI 半成品”，请交回对应业务 Prompt；本 Prompt 只负责**TACZ 音频运行时与其验证链**。

## 推荐核对的真值与现有接缝

- Legacy 现有接缝：
  - `GunSoundPlayManager`
  - `GunPackSoundResourcePack`
  - `TACZClientAssetManager.reload()`
  - `ObjectAnimationSoundChannel.playSound(...)`
  - `ServerMessageSound.Handler`
- 上游行为真值：
  - `TACZ/src/main/java/com/tacz/guns/client/sound/SoundPlayManager.java`
  - `TACZ/src/main/java/com/tacz/guns/api/client/animation/ObjectAnimationSoundChannel.java`
  - 上游与 gun display / animation sound keyframe 相关的资源消费逻辑
- 1.12 落地参考：
  - 工作区内 `PrototypeMachinery` 或 `Kirino-Engine` 中可复用的 runtime / cache / diagnostics 分层思路（若适合）

## 必验场景

- focused smoke 在静默/预检模式下不再因音频链路卡死。
- 至少一把普通枪能在动画或事件驱动下提交音频播放请求，并被新 runtime/backend 记录或播放。
- 至少一条 `ServerMessageSound` 音频广播链路被证明走通。
- 若本轮仍保留 legacy fallback，必须说明它是否仍会触发 `refreshResources()`，以及为什么 focused smoke 可以绕开这条路径。

## 输出必须包含

- 上游真值源文件
- Legacy 落点文件
- 本轮新增或调整的音频 backend / manifest / probe / smoke 机制
- focused smoke 或定向测试结果
- 明确说明：
  - 当前是否仍依赖 `GunPackSoundResourcePack`
  - `refreshResources()` 是否已从 TACZ 专用音频主链移除
  - 哪些音频格式/资源现在已可控
  - 哪些资源仍不兼容、准备在哪一轮继续处理
