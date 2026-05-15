#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${FOCUS_APP_HOME:-$HOME}"
COMPOSE_FILE="$APP_HOME/docker-compose.yaml"
LOG_DIR="$APP_HOME/logs"
LOG_FILE="$LOG_DIR/focus-health-watchdog.log"
TIMESTAMP="$(date '+%Y-%m-%d %H:%M:%S')"

mkdir -p "$LOG_DIR"

log() {
  echo "[$TIMESTAMP] $*" >> "$LOG_FILE"
}

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$COMPOSE_FILE" "$@"
  else
    docker-compose -f "$COMPOSE_FILE" "$@"
  fi
}

if [[ ! -f "$COMPOSE_FILE" ]]; then
  log "skip: compose file not found at $COMPOSE_FILE"
  exit 0
fi

if ! docker ps --format '{{.Names}}' | grep -qx 'focus-app'; then
  log "focus-app missing, attempting compose up -d"
  compose up -d >> "$LOG_FILE" 2>&1 || log "compose up failed"
  exit 0
fi

health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' focus-app 2>/dev/null || echo unknown)"

case "$health_status" in
  healthy)
    exit 0
    ;;
  starting)
    log "focus-app still starting"
    exit 0
    ;;
  unhealthy|none|unknown)
    log "focus-app health=$health_status, restarting compose stack"
    compose restart app >> "$LOG_FILE" 2>&1 || compose up -d >> "$LOG_FILE" 2>&1 || log "restart failed"
    ;;
  *)
    log "focus-app unexpected health status=$health_status"
    ;;
esac
