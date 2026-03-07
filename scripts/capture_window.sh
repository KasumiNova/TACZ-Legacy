#!/usr/bin/env bash
set -euo pipefail

OUTPUT_PATH="${AGENT_SCREENSHOT_OUTPUT_PATH:-/tmp/agent_workspace_screenshot.png}"
SEARCH_TERM="${*:-}"

for dependency in hyprctl jq grim; do
  if ! command -v "$dependency" >/dev/null 2>&1; then
    echo "缺少依赖：$dependency。该截图脚本需要运行在安装了 Hyprland、jq、grim 的环境中。" >&2
    exit 1
  fi
done

if [[ -n "$SEARCH_TERM" ]]; then
  target_window="$(hyprctl clients -j | jq -c --arg search "$SEARCH_TERM" '
    [ .[]
      | select(
          ((.class // "") | ascii_downcase | contains($search | ascii_downcase)) or
          ((.title // "") | ascii_downcase | contains($search | ascii_downcase))
        )
    ]
    | .[0]
  ')"
else
  target_window="$(hyprctl clients -j | jq -c '[.[] | select(.focusHistoryID == 0)] | .[0]')"
fi

if [[ -z "$target_window" || "$target_window" == "null" ]]; then
  echo "未找到匹配的窗口，请检查执行环境或搜索词。" >&2
  exit 1
fi

x="$(printf '%s' "$target_window" | jq -r '.at[0]')"
y="$(printf '%s' "$target_window" | jq -r '.at[1]')"
w="$(printf '%s' "$target_window" | jq -r '.size[0]')"
h="$(printf '%s' "$target_window" | jq -r '.size[1]')"

mkdir -p "$(dirname "$OUTPUT_PATH")"

grimg_result=0
if ! grim -g "${x},${y} ${w}x${h}" "$OUTPUT_PATH"; then
  grimg_result=$?
fi

if [[ $grimg_result -ne 0 || ! -f "$OUTPUT_PATH" ]]; then
  echo "截图失败，请确认目标窗口位于可见输出上，并确保 grim 可以访问该区域。" >&2
  exit 1
fi

if [[ -n "$SEARCH_TERM" ]]; then
  echo "成功截取目标窗口并保存至 $OUTPUT_PATH"
else
  echo "成功截取当前窗口并保存至 $OUTPUT_PATH"
fi
