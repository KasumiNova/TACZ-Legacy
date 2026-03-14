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

脚本当前会在 Hyprland 下自动：

- 切到目标窗口所在 workspace
- 尝试把目标窗口提到前台
- 等待约 `0.2s` 让 compositor 落稳后再调用 `grim`

这能避免“坐标来自 Minecraft 窗口，但当前 workspace 仍是 VS Code / 浏览器，导致抓到覆盖窗口”的错拍。

## 与 focused smoke 联动

`scripts/runclient_focused_smoke.sh` 默认会调用工作区内的 `scripts/capture_window.sh`，不再依赖本机私有目录。

常见环境变量：

- `FOCUSED_SMOKE_SCREENSHOT=true`
- `FOCUSED_SMOKE_USE_XVFB=false`（需要可见窗口截图时建议显式关闭；脚本在 `FOCUSED_SMOKE_SCREENSHOT=true` 且未指定时也会默认跳过 `xvfb-run`）
- `FOCUSED_SMOKE_SCREENSHOT_PLAN='pose_initial|\[FocusedSmoke] ANIMATION_OBSERVED|0;pose_settled|\[FocusedSmoke] ANIMATION_OBSERVED|1'`
- `FOCUSED_SMOKE_WORLD_TIME=18000`（把自动世界直接锁到午夜；便于抓第一人称枪焰、曳光、镜头动画）
- `FOCUSED_SMOKE_FREEZE_WORLD_TIME=true`（与 `FOCUSED_SMOKE_WORLD_TIME` 联用时，关闭昼夜循环，避免截图过程中天色漂移）
- `FOCUSED_SMOKE_CLEAR_WEATHER=true`（联动关闭天气与天气循环，避免雨雪/雷电污染截图）
- `FOCUSED_SMOKE_SCREENSHOT_AUTO_FOCUS=true`（默认开启；截图前会尝试把匹配窗口拉到前台，适合第一人称枪焰/镜头这类对前台渲染帧率更敏感的可视采样）
- `FOCUSED_SMOKE_SCREENSHOT_POST_PASS_GRACE_SECONDS=2`（当目标 marker 出现在服务端 `PASS` 之后的 client 下一帧时很有用，例如 `CAMERA_ANIMATION_APPLIED`、`MUZZLE_FLASH_VISIBLE` 这类晚一点才落日志/截图的可视证据）
- `FOCUSED_SMOKE_SCREENSHOT_WINDOW_QUERY='Minecraft 1.12.2'`
- `FOCUSED_SMOKE_REFIT_TYPE='extended_mag'`（focused smoke 自动改装时优先聚焦指定槽位，适合条件渲染验收）
- `FOCUSED_SMOKE_REFIT_ATTACHMENT='tacz:scope_standard_8x'`（focused smoke 自动改装时直接请求指定候选附件，避免分页/候选顺序导致样本不稳定）
- `FOCUSED_SMOKE_AUTO_ADS=true`（focused smoke 会主动进入 ADS，并通过现有 `IGunOperator.aim(...) + ClientMessagePlayerAim` 链路维持开镜）
- `FOCUSED_SMOKE_PASS_AFTER_ADS=true`（在命中 `ADS_READY` 后直接 PASS，适合专门验证 scope/sight/倍镜的 fully-aimed 可视状态）
- `FOCUSED_SMOKE_PASS_AFTER_ANIMATION=true`（只验证动画/准星时，在 `ANIMATION_OBSERVED` 后直接 PASS）
- `FOCUSED_SMOKE_PASS_AFTER_REFIT=true`（只验证改装预览/附件条件渲染时，在 `REFIT_ATTACHMENT_APPLIED` + `REFIT_PREVIEW_COMPLETE` 后直接 PASS）
- `FOCUSED_SMOKE_SKIP_INSPECT=true`（专门验证第一枪的 `INPUT_SHOOT` 视觉效果时很有用；默认脚本的 inspect 态会先打断检视并回到 idle，不会在同一次输入里触发 `popShellFrom()`）
- `FOCUSED_SMOKE_BULLET_SPEED_MULTIPLIER=0.45`（focused smoke 专用弹速倍率；可把 tracer 留在镜头里更久，方便抓图）
- `FOCUSED_SMOKE_TRACER_SIZE_MULTIPLIER=6.0`（focused smoke 专用 tracer 粗细倍率）
- `FOCUSED_SMOKE_TRACER_LENGTH_MULTIPLIER=5.0`（focused smoke 专用 tracer 长度倍率）
- `FOCUSED_SMOKE_SCREENSHOT_SCRIPT=/custom/path/capture_window.sh`（如需覆盖默认脚本）

补充经验：像 `MUZZLE_FLASH_VISIBLE` 这样与 `PASS` 非常接近的 marker，在当前 focused smoke 退出时序下，`15ms+` 的晚帧截图可能已经被客户端断线界面污染。遇到这类短命视觉效果时，优先尝试 `0ms / 5ms / 10ms` 的更早截图，或把最终验收写成“同轮 runtime marker + 早帧截图联合成立”，不要把被断线页覆盖的晚帧 PNG 当成 feature 本体的反证。

补充经验：像第一人称 tracer 这种“沿准星近轴出射”的效果，即便逻辑已经对齐，单帧截图里也可能只剩一段短亮束或高亮点。此时不要只靠单张 PNG 主观判断，优先把 `WORLD_VISUAL_OVERRIDES` / `TRACER_CAPTURE_OVERRIDES` / `FIRST_PERSON_LATCH` / `TRACER_PROJECTION` 一起保留：

- `WORLD_VISUAL_OVERRIDES` 证明抓图环境（例如午夜、晴天、冻结时间）确实生效；
- `TRACER_CAPTURE_OVERRIDES` 证明抓图倍率（弹速 / 粗细 / 长度）不是默认值；
- `FIRST_PERSON_LATCH` 证明 tracer 锁定到的第一人称相机 yaw/pitch 与玩家视角一致；
- `TRACER_PROJECTION` 则直接给出 tail/head/center 的屏幕坐标，可用来判定 tracer 是否仍然从屏幕边缘斜插进来。

补充经验：scope / sight 验收现在优先使用 `FOCUSED_SMOKE_AUTO_ADS=true`，并把截图触发点改成 `ADS_READY`，例如：

- `FOCUSED_SMOKE_SCREENSHOT_PLAN='ads_ready|\[FocusedSmoke] ADS_READY|1'`

这样抓到的帧会比单纯盯 `ANIMATION_OBSERVED` 更接近真实的 fully-aimed 画面；若某个倍率镜仍显得没完全“贴眼”，优先增加 delay（如 `1.5` / `2` 秒）再判断，而不是先怀疑 optic runtime 没跑起来。

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
