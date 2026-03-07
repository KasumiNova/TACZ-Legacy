# Agent 窗口截图自动分析工作流

该工作流面向 **Hyprland** 开发环境，用于把运行中的客户端窗口截图到固定路径，再交给带 `browser` 能力的 Agent 进行视觉检查。

## 工作区内脚本位置

- `scripts/capture_window.sh`

默认输出路径：

- `/tmp/agent_workspace_screenshot.png`

可通过环境变量覆盖：

- `AGENT_SCREENSHOT_OUTPUT_PATH=/your/path/output.png`

## 依赖

目标机器需要安装：

- `hyprctl`
- `jq`
- `grim`

如果这些依赖缺失，脚本会直接报错退出。

## 手动使用

在仓库根目录运行：

- 截取当前焦点窗口：`./scripts/capture_window.sh`
- 按窗口标题或类名模糊匹配：`./scripts/capture_window.sh "Minecraft 1.12.2"`
- 截取其他应用（例如 IDEA）：`./scripts/capture_window.sh "jetbrains-idea"`

## 与 focused smoke 联动

`scripts/runclient_focused_smoke.sh` 默认会调用工作区内的 `scripts/capture_window.sh`，不再依赖本机私有目录。

常见环境变量：

- `FOCUSED_SMOKE_SCREENSHOT=true`
- `FOCUSED_SMOKE_SCREENSHOT_PLAN='pose_initial|\[FocusedSmoke] ANIMATION_OBSERVED|0;pose_settled|\[FocusedSmoke] ANIMATION_OBSERVED|1'`
- `FOCUSED_SMOKE_SCREENSHOT_WINDOW_QUERY='Minecraft 1.12.2'`
- `FOCUSED_SMOKE_SCREENSHOT_SCRIPT=/custom/path/capture_window.sh`（如需覆盖默认脚本）

多截图运行结果会写到：

- `build/smoke-tests/last-focused-screenshots.txt`
- `build/smoke-tests/focused-smoke-screenshots/<run-id>/`

## Agent 视觉检查流程

1. 先运行带截图的 smoke 或手动执行 `./scripts/capture_window.sh`。
2. 用 `browser` 打开 `file:///tmp/agent_workspace_screenshot.png`，或打开 `last-focused-screenshots.txt` 中列出的归档图片。
3. 检查截图是否真的显示了目标功能，而不是黑屏、错误窗口或加载中瞬间。
4. 在报告里说明每张图分别验证了什么。

## 说明

- 该方案可跨设备复用，但前提是目标开发机也使用 **Hyprland** 并具备上述命令行依赖。
- 若目标窗口不在当前可见输出上，`grim` 可能失败；此时可先聚焦目标窗口，或让 smoke 走回退截图路径后再复查结果。
