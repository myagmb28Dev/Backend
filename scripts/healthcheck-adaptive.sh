#!/bin/sh

set -eu

STATE_FILE="${HEALTHCHECK_STATE_FILE:-/tmp/pogun-healthcheck.state}"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://localhost:8080/health}"

current_epoch="$(date +%s)"
last_probe_epoch=0
streak=0

if [ -f "$STATE_FILE" ]; then
  IFS='|' read -r last_probe_epoch streak < "$STATE_FILE" || true
  last_probe_epoch="${last_probe_epoch:-0}"
  streak="${streak:-0}"
fi

case "$streak" in
  0|1)
    probe_interval=2
    ;;
  2|3)
    probe_interval=5
    ;;
  4|5)
    probe_interval=10
    ;;
  *)
    probe_interval=30
    ;;
esac

if [ "$last_probe_epoch" -ne 0 ] && [ $((current_epoch - last_probe_epoch)) -lt "$probe_interval" ]; then
  exit 0
fi

if curl -fsS "$HEALTHCHECK_URL" >/dev/null; then
  next_streak=$((streak + 1))
  printf '%s|%s\n' "$current_epoch" "$next_streak" > "$STATE_FILE"
  exit 0
fi

printf '%s|%s\n' "$current_epoch" 0 > "$STATE_FILE"
exit 1