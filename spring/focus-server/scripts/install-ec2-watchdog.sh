#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${FOCUS_APP_HOME:-$HOME}"
SCRIPT_PATH="$APP_HOME/scripts/ec2-health-watchdog.sh"
CRON_FILE="/etc/cron.d/focus-health-watchdog"

if [[ ! -f "$SCRIPT_PATH" ]]; then
  echo "watchdog script not found: $SCRIPT_PATH" >&2
  exit 1
fi

chmod +x "$SCRIPT_PATH"

cat > "$CRON_FILE" <<EOF
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
* * * * * root FOCUS_APP_HOME=$APP_HOME bash $SCRIPT_PATH
EOF

chmod 644 "$CRON_FILE"
echo "Installed watchdog cron at $CRON_FILE"
