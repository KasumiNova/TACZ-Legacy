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
POLL_INTERVAL_SECONDS="${SMOKE_POLL_INTERVAL_SECONDS:-1}"
SHUTDOWN_GRACE_SECONDS="${SMOKE_SHUTDOWN_GRACE_SECONDS:-15}"
WORLD_FOLDER="${FOCUSED_SMOKE_WORLD_FOLDER:-tacz_focused_smoke_auto}"
WORLD_NAME="${FOCUSED_SMOKE_WORLD_NAME:-tacz_focused_smoke_auto}"
EXPLOSIVE_GUN="${FOCUSED_SMOKE_EXPLOSIVE_GUN:-tacz:rpg7}"
AUDIO_BACKEND="${TACZ_AUDIO_BACKEND:-diagnostic}"
AUDIO_PREFLIGHT="${TACZ_AUDIO_PREFLIGHT:-true}"
AUDIO_PREFLIGHT_STRICT="${TACZ_AUDIO_PREFLIGHT_STRICT:-false}"
ENABLE_SCREENSHOT="${FOCUSED_SMOKE_SCREENSHOT:-false}"
SCREENSHOT_SCRIPT="${FOCUSED_SMOKE_SCREENSHOT_SCRIPT:-$SCRIPT_DIR/capture_window.sh}"
SCREENSHOT_WINDOW_QUERY="${FOCUSED_SMOKE_SCREENSHOT_WINDOW_QUERY:-Minecraft 1.12.2}"
SCREENSHOT_TRIGGER_PATTERN="${FOCUSED_SMOKE_SCREENSHOT_TRIGGER_PATTERN:-\\[FocusedSmoke] ANIMATION_OBSERVED}"
SCREENSHOT_DELAY_SECONDS="${FOCUSED_SMOKE_SCREENSHOT_DELAY_SECONDS:-1}"
SCREENSHOT_PLAN="${FOCUSED_SMOKE_SCREENSHOT_PLAN:-animation_observed|${SCREENSHOT_TRIGGER_PATTERN}|${SCREENSHOT_DELAY_SECONDS}}"
SCREENSHOT_ARCHIVE_ROOT="$OUT_DIR/focused-smoke-screenshots"
SCREENSHOT_LATEST_FILE="/tmp/agent_workspace_screenshot.png"

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
  if [[ "$delay" != "0" ]]; then
    sleep "$delay"
  fi

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
  "-Dtacz.focusedSmoke.explosiveGun=${EXPLOSIVE_GUN}"
  "-Dtacz.audio.backend=${AUDIO_BACKEND}"
  "-Dtacz.audio.preflight=${AUDIO_PREFLIGHT}"
  "-Dtacz.audio.preflight.strict=${AUDIO_PREFLIGHT_STRICT}"
  runClient
  --console=plain
)
if command -v xvfb-run >/dev/null 2>&1; then
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
