# TACZ Agent 协作与阶段汇报规范

## 目标

为 `TACZ-Legacy` 的多 Agent 迁移流程建立一套**可追踪、低冲突、无需截图回报**的交付规范。

这套规范解决两个问题：

1. **正式结论必须回写到 Git 受控文档**，避免阶段成果只停留在聊天记录里。
2. **每轮 Agent 结果必须落到一个本地临时文档工作区**，方便主 Agent 直接读取确认，而不是依赖用户手动截图转述。

## 适用范围

- 默认适用于所有**有写权限、会改代码或文档**的迁移 Agent。
- 只读侦察 Agent（例如 `Explore` / `Ask` 的纯侦察任务）可不写本地阶段报告文件，但其研究结论一旦被采纳并进入实施，实施 Agent 必须按本规范补齐文档与阶段报告。

## 两层交付要求

每个可写 Agent 在完成任务时，必须同时完成以下两层交付：

### 1. 更新 Git 受控文档

至少更新以下文档中的一类：

- `docs/TACZ_AGENT_MIGRATION_PLAN.md`
  - 用于同步当前阶段进度、子轨状态、文件所有权、下一阶段顺序。
- 对应子系统设计文档，例如：
  - `docs/TACZ_AUDIO_ENGINE_PLAN.md`
  - 未来新增的 render/combat/refit 设计文档
- 对应 Prompt 文档：
  - 当阶段目标、边界、验收标准发生变化时，必须同步更新 `.github/prompts/*.prompt.md`

规则：

- **正式状态、边界、验收标准**必须进入 Git 受控文档。
- 如果本轮任务改变了“谁负责什么”“下一轮还剩什么”，优先更新 `docs/TACZ_AGENT_MIGRATION_PLAN.md`。
- 如果本轮任务改变了“这个子系统现在怎么设计/怎么验证”，优先更新对应设计文档。
- 如果本轮任务改变了“下次 Agent 应该怎么做”，必须更新对应 Prompt。

### 2. 写入本地临时阶段报告

每个可写 Agent 完成任务后，必须在本地临时工作区写一份**阶段报告文件**。

默认目录：

- `.agent-workspace/stage-reports/`

该目录**只用于本地协作，不提交 Git**。

## 本地临时工作区规范

### 目录

- `.agent-workspace/stage-reports/`
  - 每次完成一个阶段任务，新增一份独立报告文件。
- `.agent-workspace/templates/`
  - 可选，本地模板或草稿。

### 文件命名规范

建议使用：

`YYYY-MM-DDTHH-mm-ss--<track>--<agent>--<status>.md`

示例：

- `2026-03-08T10-30-00--audio--tacz-migration--completed.md`
- `2026-03-08T15-10-00--combat--tacz-migration--blocked.md`
- `2026-03-08T18-20-00--render-animation--tacz-migration--completed.md`

要求：

- 一个 Agent 一次交付写一份新文件，**不要共享追加同一个总表**，避免多 Agent 冲突。
- 文件名必须带：时间、轨道、Agent、状态。
- `status` 建议使用：`completed`、`partial`、`blocked`。

## 阶段报告模板

每份报告至少包含以下栏目：

```md
# <track> / <agent> / <status>

- 时间：
- Prompt：
- Agent：
- 状态：completed | partial | blocked
- 关联文档更新：

## 本轮目标

## 上游真值来源

## 本轮改动文件

## 测试与运行验证

## 结果摘要

## 剩余问题

## 建议下个接手 Agent

## 阻塞（如果有）
```

## 主 Agent 的读取规则

主 Agent 在收口时，优先读取：

1. `docs/TACZ_AGENT_MIGRATION_PLAN.md` 中的正式进度
2. `.agent-workspace/stage-reports/` 中最近的阶段报告文件

这样可以做到：

- Git 文档负责长期状态
- 本地阶段报告负责快速确认与短周期 handoff
- 用户不再需要手动截图同步阶段结果

## 推荐工作流

1. 侦察 Agent 做只读分析
2. 实施 Agent 改代码 / 测试 / smoke
3. 实施 Agent 更新 Git 受控文档
4. 实施 Agent 写入 `.agent-workspace/stage-reports/*.md`
5. 主 Agent 读取阶段报告并决定下一轮分工
6. 用户只在需要方向调整时介入

## 注意事项

- `.agent-workspace/` 必须加入 `.gitignore`。
- 阶段报告可以写详细一些，但**不要把正式结论只放在这里**；正式状态仍必须回写到 Git 文档。
- 若一个任务只改测试/脚本，也仍然要写阶段报告，因为它改变了当前验证结论。
- 若任务包含视觉冒烟验证，可把 `build/smoke-tests/last-focused-screenshots.txt` 中列出的截图文件作为辅助证据引用；多截图场景下应在报告里说明各张图分别验证了什么。
- 若任务完成后没有任何文档更新，应视为流程未完成。
- 若任务属于“阶段完成”，报告中必须明确：
  - 当前已完成内容
  - 仍未完成内容
  - 下个建议接手轨道

## 当前仓库建议

- 音频 Agent：完成后同步更新 `docs/TACZ_AUDIO_ENGINE_PLAN.md`
- 渲染 Agent：完成后同步更新 render 相关 Prompt / 设计文档 / 迁移计划
- Combat Agent：完成后同步更新 `docs/TACZ_AGENT_MIGRATION_PLAN.md` 中的 combat 状态与验收结论
- Foundation Agent：完成后同步更新 smoke / hook / 诊断覆盖范围说明
- 视觉检查优先使用工作区内 `scripts/capture_window.sh`；便携化说明见 `docs/AGENT_SCREENSHOT_WORKFLOW.md`
