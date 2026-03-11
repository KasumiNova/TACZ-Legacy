---
name: "TACZ Stage Combat Heat RPM Cadence"
description: "Wire heat-driven RPM modifiers into TACZ-Legacy firing cadence without breaking existing shoot/network parity."
agent: "TACZ Migration"
argument-hint: "填写高热状态射速不变的枪、上游真值文件、验收枪包或脚本要求"
---
修复 `TACZ-Legacy` **heat 对开火 cadence / shoot interval 的真实影响**，让 `min_rpm_mod / max_rpm_mod` 不再只是数据 plumbing。

## 当前已知缺口
- `GunDataAccessor` 已经读取 `heat.min_rpm_mod` / `heat.max_rpm_mod`
- `TACZGunPropertyResolver.resolveHeatRpmModifier()` 已能计算 modifier
- `TACZGunScriptAPI` 也已暴露 `getHeatMinRpm()` / `getHeatMaxRpm()`
- 但当前 `resolveHeatRpmModifier()` 没有 call site，**热量并没有真正改变射击 cadence**

## 上游真值与排查要求
- 先查上游 `TACZ` 中 heat 与 shoot interval / rpm / data script 的真实落点，再决定 Legacy 应该接在哪一层：
  - 默认射击链
  - Lua / data script 的 `adjustShootInterval`
  - 或二者共同作用
- 如果上游是“脚本消费 getter + 主链再做基础乘子”，Legacy 也必须保持同样层级，不要自创公式。
- 若某些枪完全依赖 pack script 决定 cadence，必须先确认脚本路径是否已经在 Legacy 可达。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/common/resource/GunDataAccessor.kt`
- `src/main/kotlin/com/tacz/legacy/common/resource/TACZGunPropertyResolver.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/TACZGunScriptAPI.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/LivingEntityShoot.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientShootCoordinator.kt`
- 任何当前负责 shoot interval / cooldown / cadence 计算的文件

## 任务目标
1. 把 heat RPM modifier 接进真实射击 cadence。
2. 确保客户端/服务端对 interval 的认知一致，不要修完后变成假快射或服务端拒绝。
3. 保持与现有：
   - burst cadence
   - fire mode adjust
   - script-adjusted shoot interval
   的组合语义可解释且可验证。
4. 如果枪包脚本已经直接消费 `getHeatMinRpm()` / `getHeatMaxRpm()`，不能把兼容性打坏。

## 执行要求
- 不要只补 getter / log；必须让射速真实变化。
- 不要为了热量 RPM 去改坏 burst、reload、ammo gate、server accept window。
- 若真值需要拆成“主链基础 modifier + 脚本可再调整”，请明确两者顺序。
- 如无法一次性覆盖所有枪，也要至少让一把明确带 heat 数据的枪真实体现射速变化。

## 必验场景
- 至少一把 heat-enabled 枪，在低热 / 高热下射击间隔可观测不同。
- 日志或测试要给出 interval / rpm 前后对比，而不是只说“理论上会变”。
- 若存在脚本枪，也要至少验证一把脚本路径不会被这次改动破坏。

## 输出必须包含
- 上游 heat→cadence 真值落点
- Legacy 最终接入层级
- 客户端/服务端如何保持一致
- 实测结果与残余风险
