# TACZ 大系统与 Agent 迁移规划（兼容入口）

> 本文件从 2026-03-10 起不再承载完整阶段正文。  
> 正式迁移计划已拆分到 `docs/migration_plan/` 目录，保留本文件只是为了兼容旧引用与旧工作流入口。

## 现在应该看哪里

### 1. 总入口

- `docs/migration_plan/MAIN.md`
  - 轨道边界、推荐顺序、Prompt 总路由、分册维护规则。

### 2. Foundation 到 Client UX 分册

- `docs/migration_plan/2026-03-10-FOUNDATION-TO-CLIENT-UX.md`
  - Foundation / Data / Combat / Audio / Client UX / 非 Render 主责的阶段状态。

### 3. Render / Validation / Prompts 分册

- `docs/migration_plan/2026-03-10-RENDER-VALIDATION-AND-PROMPTS.md`
  - Render / Animation / 视觉 reopen / focused smoke 结论 / Prompt 细分与并行建议。

### 4. 工作流规范

- `docs/TACZ_AGENT_WORKFLOW.md`
  - Agent 交付、阶段报告、文档更新与 handoff 规则。

## 更新规则

- **不要**再把长篇阶段状态直接堆回本文件。
- 若改的是分工、顺序、Prompt 总路由或分册维护规则，更新 `docs/migration_plan/MAIN.md`。
- 若改的是具体轨道的阶段状态、smoke 证据、reopen 结论，更新对应 `docs/migration_plan/YYYY-MM-DD-CATEGORY.md` 风格的分类分册。
- 若改的是协作规范、报告模板或 handoff 机制，更新 `docs/TACZ_AGENT_WORKFLOW.md`。

补充命名语义：

- 分类分册统一使用 `[yyyy-MM-dd]-[CATEGORY].md`。
- `CATEGORY` 表示文档的大分类，不再使用 `CATEGORY_A` / `CATEGORY_B` 这类编号名。
- 文件名日期表示分类文档的建档/结构整理日期；正文若汇总多天开发内容，必须在章节里继续标明真实开发日期。

## 为什么还保留这个文件

- 兼容旧 Prompt / 旧笔记 / 旧手工引用
- 给第一次进仓库的人一个稳定落脚点
- 防止后续 Agent 还在旧路径上“找不到文档就自创规范”

## 一句话结论

`docs/TACZ_AGENT_MIGRATION_PLAN.md` 现在是**跳板页**；真正要维护的正文，请进入 `docs/migration_plan/`。
