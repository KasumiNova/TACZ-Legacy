#!/usr/bin/env bash
set -euo pipefail

LOCK_FILE="/tmp/tacz_focused_smoke.lock"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "[TACZ-Legacy] ERROR: Another instance of focused smoke test is already running. Exiting to prevent collisions." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/smoke-tests"
mkdir -p "$OUT_DIR"
LOG_FILE="$OUT_DIR/runclient-focused-smoke-$(date +%Y%m%d-%H%M%S).log"
TIMEOUT_SECONDS="${1:-300}"
POLL_INTERVAL_SECONDS="${SMOKE_POLL_INTERVAL_SECONDS:-0.1}"
SHUTDOWN_GRACE_SECONDS="${SMOKE_SHUTDOWN_GRACE_SECONDS:-15}"
WORLD_FOLDER="${FOCUSED_SMOKE_WORLD_FOLDER:-tacz_focused_smoke_auto}"
WORLD_NAME="${FOCUSED_SMOKE_WORLD_NAME:-tacz_focused_smoke_auto}"
WORLD_TIME="${FOCUSED_SMOKE_WORLD_TIME:-}"
FREEZE_WORLD_TIME="${FOCUSED_SMOKE_FREEZE_WORLD_TIME:-auto}"
CLEAR_WEATHER="${FOCUSED_SMOKE_CLEAR_WEATHER:-auto}"
REGULAR_GUN="${FOCUSED_SMOKE_REGULAR_GUN:-}"
if [[ ${FOCUSED_SMOKE_EXPLOSIVE_GUN+x} ]]; then
  EXPLOSIVE_GUN="$FOCUSED_SMOKE_EXPLOSIVE_GUN"
else
  EXPLOSIVE_GUN="tacz:rpg7"
fi
DISABLE_EXPLOSIVE="${FOCUSED_SMOKE_DISABLE_EXPLOSIVE:-false}"
DISABLE_ATTACHMENTS="${FOCUSED_SMOKE_DISABLE_ATTACHMENTS:-false}"
REFIT_PREVIEW="${FOCUSED_SMOKE_REFIT_PREVIEW:-false}"
REFIT_TYPE="${FOCUSED_SMOKE_REFIT_TYPE:-}"
REFIT_ATTACHMENT="${FOCUSED_SMOKE_REFIT_ATTACHMENT:-}"
AUTO_ADS="${FOCUSED_SMOKE_AUTO_ADS:-false}"
PASS_AFTER_ANIMATION="${FOCUSED_SMOKE_PASS_AFTER_ANIMATION:-false}"
PASS_AFTER_ADS="${FOCUSED_SMOKE_PASS_AFTER_ADS:-false}"
PASS_AFTER_REFIT="${FOCUSED_SMOKE_PASS_AFTER_REFIT:-false}"
SKIP_INSPECT="${FOCUSED_SMOKE_SKIP_INSPECT:-false}"
SKIP_RELOAD="${FOCUSED_SMOKE_SKIP_RELOAD:-false}"
HIT_FEEDBACK_TARGET="${FOCUSED_SMOKE_SPAWN_HIT_TARGET:-false}"
BULLET_SPEED_MULTIPLIER="${FOCUSED_SMOKE_BULLET_SPEED_MULTIPLIER:-}"
TRACER_SIZE_MULTIPLIER="${FOCUSED_SMOKE_TRACER_SIZE_MULTIPLIER:-}"
TRACER_LENGTH_MULTIPLIER="${FOCUSED_SMOKE_TRACER_LENGTH_MULTIPLIER:-}"
AUDIO_BACKEND="${TACZ_AUDIO_BACKEND:-diagnostic}"
AUDIO_PREFLIGHT="${TACZ_AUDIO_PREFLIGHT:-true}"
AUDIO_PREFLIGHT_STRICT="${TACZ_AUDIO_PREFLIGHT_STRICT:-false}"
ENABLE_SCREENSHOT="${FOCUSED_SMOKE_SCREENSHOT:-false}"
USE_XVFB="${FOCUSED_SMOKE_USE_XVFB:-auto}"
SCREENSHOT_SCRIPT="${FOCUSED_SMOKE_SCREENSHOT_SCRIPT:-$SCRIPT_DIR/capture_window.sh}"
SCREENSHOT_WINDOW_QUERY="${FOCUSED_SMOKE_SCREENSHOT_WINDOW_QUERY:-Minecraft 1.12.2}"
SCREENSHOT_AUTO_FOCUS="${FOCUSED_SMOKE_SCREENSHOT_AUTO_FOCUS:-true}"
SCREENSHOT_TRIGGER_PATTERN="${FOCUSED_SMOKE_SCREENSHOT_TRIGGER_PATTERN:-\\[FocusedSmoke] ANIMATION_OBSERVED}"
SCREENSHOT_DELAY_SECONDS="${FOCUSED_SMOKE_SCREENSHOT_DELAY_SECONDS:-1}"
SCREENSHOT_PLAN="${FOCUSED_SMOKE_SCREENSHOT_PLAN:-inspect_0s|\\[FocusedSmoke] INSPECT_TRIGGERED|0;inspect_1s|\\[FocusedSmoke] INSPECT_TRIGGERED|1;inspect_2s|\\[FocusedSmoke] INSPECT_TRIGGERED|2}"
SCREENSHOT_POST_PASS_GRACE_SECONDS="${FOCUSED_SMOKE_SCREENSHOT_POST_PASS_GRACE_SECONDS:-2}"
SCREENSHOT_ARCHIVE_ROOT="$OUT_DIR/focused-smoke-screenshots"
SCREENSHOT_LATEST_FILE="/tmp/agent_workspace_screenshot.png"

if [[ "$FREEZE_WORLD_TIME" == "auto" ]]; then
  if [[ -n "$WORLD_TIME" ]]; then
    FREEZE_WORLD_TIME="true"
  else
    FREEZE_WORLD_TIME="false"
  fi
fi

if [[ "$CLEAR_WEATHER" == "auto" ]]; then
  if [[ -n "$WORLD_TIME" ]]; then
    CLEAR_WEATHER="true"
  else
    CLEAR_WEATHER="false"
  fi
fi

SUCCESS_PATTERN="\\[FocusedSmoke] PASS"
FAIL_PATTERN="\\[FocusedSmoke] FAIL"

RUN_PID=""
RUN_STATUS=0
RUN_STATUS_CAPTURED=0
RESULT="failed"
RUN_BASENAME="$(basename "$LOG_FILE" .log)"
SCREENSHOT_RUN_DIR="$SCREENSHOT_ARCHIVE_ROOT/$RUN_BASENAME"
SCREENSHOT_MANIFEST="$OUT_DIR/${RUN_BASENAME}-screenshots.txt"
LAST_SCREENSHOT_MANIFEST="$OUT_DIR/last-focused-screenshots.txt"
declare -a SCREENSHOT_LABELS=()
declare -a SCREENSHOT_PATTERNS=()
declare -a SCREENSHOT_DELAYS=()
declare -a SCREENSHOT_CAPTURED=()
PASS_SEEN_AT=-1

sanitize_screenshot_label() {
  printf '%s' "$1" | LC_ALL=C sed 's/[^A-Za-z0-9._ -]/_/g; s/ /_/g; s/^_\+//; s/_\+$//'
}

parse_screenshot_plan() {
  local raw_plan="$1"
  local entry=""
  local label=""
  local pattern=""
  local delay=""
  local trimmed_entry=""

  IFS=';' read -r -a entries <<< "$raw_plan"
  for entry in "${entries[@]}"; do
    trimmed_entry="${entry#${entry%%[![:space:]]*}}"
    trimmed_entry="${trimmed_entry%${trimmed_entry##*[![:space:]]}}"
    if [[ -z "$trimmed_entry" ]]; then
      continue
    fi

    IFS='|' read -r label pattern delay <<< "$trimmed_entry"
    if [[ -z "$label" || -z "$pattern" ]]; then
      echo "[TACZ-Legacy] WARNING: Invalid screenshot spec '$trimmed_entry'. Expected label|pattern|delay." >&2
      continue
    fi

    pattern="$(printf '%s' "$pattern" | sed 's/\\\\/\\/g')"
    delay="${delay:-0}"
    SCREENSHOT_LABELS+=("$label")
    SCREENSHOT_PATTERNS+=("$pattern")
    SCREENSHOT_DELAYS+=("$delay")
    SCREENSHOT_CAPTURED+=(0)
  done

  if [[ ${#SCREENSHOT_LABELS[@]} -eq 0 ]]; then
    SCREENSHOT_LABELS+=("animation_observed")
    SCREENSHOT_PATTERNS+=("$SCREENSHOT_TRIGGER_PATTERN")
    SCREENSHOT_DELAYS+=("$SCREENSHOT_DELAY_SECONDS")
    SCREENSHOT_CAPTURED+=(0)
  fi
}

capture_screenshot_spec() {
  local index="$1"
  local label="${SCREENSHOT_LABELS[$index]}"
  local pattern="${SCREENSHOT_PATTERNS[$index]}"
  local delay="${SCREENSHOT_DELAYS[$index]}"
  local safe_label=""
  local target_file=""

  echo "[TACZ-Legacy] focused smoke key moment reached (label=$label, pattern=$pattern). Taking screenshot after ${delay}s delay..."
  focus_window_if_possible
  if [[ "$delay" != "0" ]]; then
    sleep "$delay"
  fi
  focus_window_if_possible

  if [[ ! -x "$SCREENSHOT_SCRIPT" ]]; then
    echo "[TACZ-Legacy] WARNING: Screenshot script not found at $SCREENSHOT_SCRIPT" >&2
    SCREENSHOT_CAPTURED[$index]=1
    return
  fi

  if ! "$SCREENSHOT_SCRIPT" "$SCREENSHOT_WINDOW_QUERY" && ! "$SCREENSHOT_SCRIPT"; then
    echo "[TACZ-Legacy] WARNING: Screenshot capture failed for label=$label." >&2
    SCREENSHOT_CAPTURED[$index]=1
    return
  fi

  if [[ ! -f "$SCREENSHOT_LATEST_FILE" ]]; then
    echo "[TACZ-Legacy] WARNING: Screenshot capture reported success but $SCREENSHOT_LATEST_FILE does not exist." >&2
    SCREENSHOT_CAPTURED[$index]=1
    return
  fi

  safe_label="$(sanitize_screenshot_label "$label")"
  if [[ -z "$safe_label" ]]; then
    safe_label="shot-$((index + 1))"
  fi
  target_file="$SCREENSHOT_RUN_DIR/$(printf '%02d' "$((index + 1))")-${safe_label}.png"
  cp "$SCREENSHOT_LATEST_FILE" "$target_file"
  printf '%s\n' "$target_file" >> "$SCREENSHOT_MANIFEST"
  cp "$SCREENSHOT_MANIFEST" "$LAST_SCREENSHOT_MANIFEST"
  echo "[TACZ-Legacy] archived screenshot [$label] -> $target_file"
  SCREENSHOT_CAPTURED[$index]=1
}

focus_window_if_possible() {
  local target_window=""
  local address=""
  local workspace_name=""

  if [[ "$SCREENSHOT_AUTO_FOCUS" != "true" ]]; then
    return
  fi
  if ! command -v hyprctl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
    return
  fi

  if [[ -n "$SCREENSHOT_WINDOW_QUERY" ]]; then
    target_window="$(hyprctl clients -j | jq -c --arg search "$SCREENSHOT_WINDOW_QUERY" '
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
    return
  fi

  address="$(printf '%s' "$target_window" | jq -r '.address // empty')"
  workspace_name="$(printf '%s' "$target_window" | jq -r '.workspace.name // empty')"
  if [[ -n "$workspace_name" && "$workspace_name" != "null" ]]; then
    hyprctl dispatch workspace "$workspace_name" >/dev/null 2>&1 || true
  fi
  if [[ -z "$address" || "$address" == "null" ]]; then
    return
  fi

  hyprctl dispatch focuswindow "address:$address" >/dev/null 2>&1 || true
  sleep 0.2
}

all_screenshots_captured() {
  local index=""
  for index in "${!SCREENSHOT_CAPTURED[@]}"; do
    if [[ "${SCREENSHOT_CAPTURED[$index]}" -eq 0 ]]; then
      return 1
    fi
  done
  return 0
}

process_alive() {
  [[ -n "$RUN_PID" ]] && kill -0 "$RUN_PID" 2>/dev/null
}

collect_run_status() {
  if [[ -z "$RUN_PID" || $RUN_STATUS_CAPTURED -eq 1 ]]; then
    return
  fi
  set +e
  wait "$RUN_PID"
  RUN_STATUS=$?
  set -e
  RUN_STATUS_CAPTURED=1
}

terminate_process_group() {
  local signal="$1"
  if [[ -z "$RUN_PID" ]]; then
    return
  fi
  kill "-$signal" -- "-$RUN_PID" 2>/dev/null || true
}

stop_process_group() {
  if ! process_alive; then
    collect_run_status
    return
  fi

  terminate_process_group TERM

  local deadline=$((SECONDS + SHUTDOWN_GRACE_SECONDS))
  while process_alive && (( SECONDS < deadline )); do
    sleep 1
  done

  if process_alive; then
    terminate_process_group KILL
  fi

  collect_run_status
}

cleanup() {
  local exit_code=$?
  if [[ "$RESULT" != "pass" ]]; then
    stop_process_group
  fi
  exit "$exit_code"
}

trap cleanup EXIT INT TERM

cd "$ROOT_DIR"

GRADLE_CMD=(
  ./gradlew
  --no-daemon
  -Dtacz.focusedSmoke=true
  -Dtacz.focusedSmoke.autoWorld=true
  "-Dtacz.focusedSmoke.worldFolder=${WORLD_FOLDER}"
  "-Dtacz.focusedSmoke.worldName=${WORLD_NAME}"
  "-Dtacz.focusedSmoke.freezeWorldTime=${FREEZE_WORLD_TIME}"
  "-Dtacz.focusedSmoke.clearWeather=${CLEAR_WEATHER}"
  "-Dtacz.focusedSmoke.explosiveGun=${EXPLOSIVE_GUN}"
  "-Dtacz.focusedSmoke.disableExplosive=${DISABLE_EXPLOSIVE}"
  "-Dtacz.focusedSmoke.disableAttachments=${DISABLE_ATTACHMENTS}"
  "-Dtacz.focusedSmoke.refitPreview=${REFIT_PREVIEW}"
  "-Dtacz.focusedSmoke.autoAim=${AUTO_ADS}"
  "-Dtacz.focusedSmoke.passAfterAnimation=${PASS_AFTER_ANIMATION}"
  "-Dtacz.focusedSmoke.passAfterAim=${PASS_AFTER_ADS}"
  "-Dtacz.focusedSmoke.passAfterRefit=${PASS_AFTER_REFIT}"
  "-Dtacz.focusedSmoke.skipInspect=${SKIP_INSPECT}"
  "-Dtacz.focusedSmoke.skipReload=${SKIP_RELOAD}"
  "-Dtacz.focusedSmoke.hitFeedbackTarget=${HIT_FEEDBACK_TARGET}"
  "-Dtacz.audio.backend=${AUDIO_BACKEND}"
  "-Dtacz.audio.preflight=${AUDIO_PREFLIGHT}"
  "-Dtacz.audio.preflight.strict=${AUDIO_PREFLIGHT_STRICT}"
  runClient
  --console=plain
)
if [[ -n "$WORLD_TIME" ]]; then
  GRADLE_CMD+=("-Dtacz.focusedSmoke.worldTime=${WORLD_TIME}")
fi
if [[ -n "$REGULAR_GUN" ]]; then
  GRADLE_CMD+=("-Dtacz.focusedSmoke.regularGun=${REGULAR_GUN}")
fi
if [[ -n "$REFIT_TYPE" ]]; then
  GRADLE_CMD+=("-Dtacz.focusedSmoke.refitType=${REFIT_TYPE}")
fi
if [[ -n "$REFIT_ATTACHMENT" ]]; then
  GRADLE_CMD+=("-Dtacz.focusedSmoke.refitAttachment=${REFIT_ATTACHMENT}")
fi
if [[ -n "$BULLET_SPEED_MULTIPLIER" ]]; then
  GRADLE_CMD+=("-Dtacz.focusedSmoke.bulletSpeedMultiplier=${BULLET_SPEED_MULTIPLIER}")
fi
if [[ -n "$TRACER_SIZE_MULTIPLIER" ]]; then
  GRADLE_CMD+=("-Dtacz.focusedSmoke.tracerSizeMultiplier=${TRACER_SIZE_MULTIPLIER}")
fi
if [[ -n "$TRACER_LENGTH_MULTIPLIER" ]]; then
  GRADLE_CMD+=("-Dtacz.focusedSmoke.tracerLengthMultiplier=${TRACER_LENGTH_MULTIPLIER}")
fi
if [[ "$USE_XVFB" == "auto" ]]; then
  if [[ "$ENABLE_SCREENSHOT" == "true" ]]; then
    USE_XVFB="false"
  else
    USE_XVFB="true"
  fi
fi
if [[ "$USE_XVFB" == "true" ]] && command -v xvfb-run >/dev/null 2>&1; then
  RUN_CMD=(xvfb-run -a "${GRADLE_CMD[@]}")
else
  RUN_CMD=("${GRADLE_CMD[@]}")
fi

: > "$LOG_FILE"
printf '%s\n' "$LOG_FILE" > "$OUT_DIR/last-focused-log.txt"
if [[ "$ENABLE_SCREENSHOT" == "true" ]]; then
  mkdir -p "$SCREENSHOT_RUN_DIR"
  : > "$SCREENSHOT_MANIFEST"
  : > "$LAST_SCREENSHOT_MANIFEST"
  parse_screenshot_plan "$SCREENSHOT_PLAN"
fi

set +e
setsid stdbuf -oL -eL "${RUN_CMD[@]}" > >(tee -a "$LOG_FILE") 2> >(tee -a "$LOG_FILE" >&2) &
RUN_PID=$!
set -e

echo "[TACZ-Legacy] focused smoke launched (pid=$RUN_PID, audioBackend=$AUDIO_BACKEND, preflight=$AUDIO_PREFLIGHT, strict=$AUDIO_PREFLIGHT_STRICT); waiting for in-world markers..."
if [[ "$ENABLE_SCREENSHOT" == "true" ]]; then
  echo "[TACZ-Legacy] focused smoke screenshot plan: $SCREENSHOT_PLAN"
  echo "[TACZ-Legacy] focused smoke screenshot archive dir: $SCREENSHOT_RUN_DIR"
fi

deadline=$((SECONDS + TIMEOUT_SECONDS))

while true; do
  if [[ "$ENABLE_SCREENSHOT" == "true" ]]; then
    for index in "${!SCREENSHOT_LABELS[@]}"; do
      if [[ "${SCREENSHOT_CAPTURED[$index]}" -eq 0 ]] && grep -q "${SCREENSHOT_PATTERNS[$index]}" "$LOG_FILE"; then
        capture_screenshot_spec "$index"
      fi
    done
  fi

  if grep -q "$SUCCESS_PATTERN" "$LOG_FILE"; then
    if [[ "$ENABLE_SCREENSHOT" == "true" ]] && ! all_screenshots_captured; then
      if (( PASS_SEEN_AT < 0 )); then
        PASS_SEEN_AT=$SECONDS
        echo "[TACZ-Legacy] focused smoke pass marker reached; waiting up to ${SCREENSHOT_POST_PASS_GRACE_SECONDS}s for remaining screenshot markers..."
      fi
      if (( SECONDS - PASS_SEEN_AT < SCREENSHOT_POST_PASS_GRACE_SECONDS )); then
        if ! process_alive; then
          collect_run_status
        fi
        sleep "$POLL_INTERVAL_SECONDS"
        continue
      fi
      echo "[TACZ-Legacy] focused smoke screenshot grace expired; shutting down with remaining markers unmet."
    fi
    RESULT="pass"
    echo "[TACZ-Legacy] focused smoke pass marker reached; shutting down client automatically..."
    stop_process_group
    break
  fi

  if grep -q "$FAIL_PATTERN" "$LOG_FILE"; then
    RESULT="failed"
    echo "[TACZ-Legacy] focused smoke reported failure. Log: $LOG_FILE" >&2
    stop_process_group
    break
  fi

  if ! process_alive; then
    collect_run_status
    break
  fi

  if (( SECONDS >= deadline )); then
    RESULT="timeout"
    echo "[TACZ-Legacy] focused smoke timed out before PASS/FAIL marker appeared. Log: $LOG_FILE" >&2
    stop_process_group
    break
  fi

  sleep "$POLL_INTERVAL_SECONDS"
done

if [[ "$RESULT" == "pass" ]]; then
  if [[ "$ENABLE_SCREENSHOT" == "true" && -s "$SCREENSHOT_MANIFEST" ]]; then
    echo "[TACZ-Legacy] focused smoke screenshots archived in $SCREENSHOT_RUN_DIR"
    echo "[TACZ-Legacy] focused smoke screenshot manifest: $SCREENSHOT_MANIFEST"
  fi
  echo "[TACZ-Legacy] focused smoke pass: animation/projectile/explosion diagnostic chain completed. Log: $LOG_FILE"
  trap - EXIT INT TERM
  exit 0
fi

echo "[TACZ-Legacy] focused smoke failed with status $RUN_STATUS. Log: $LOG_FILE" >&2
exit 1
