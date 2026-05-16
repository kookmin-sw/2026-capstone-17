#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  test_chzzk_srt_stream.sh \
    --video /absolute/path/to/video.mp4 \
    --api-host http://your-api-host \
    --token your_access_token \
    [--media-host 13.125.126.120] \
    [--media-port 8890] \
    [--title "file test broadcast"] \
    [--avatar-id avatar-a] \
    [--heartbeat-interval 10]

Required:
  --video            Local mp4 file to replay as a live SRT stream
  --api-host         Backend base URL, for example http://43.203.120.71:8080
  --token            Bearer access token for broadcast APIs

Optional:
  --media-host       MediaMTX ingest host. Default: 13.125.126.120
  --media-port       MediaMTX ingest port. Default: 8890
  --title            Broadcast title. Default: file test broadcast
  --avatar-id        Avatar id sent to /start. Default: avatar-a
  --heartbeat-interval
                     Seconds between heartbeat calls. Default: 10

What this script does:
  1. Creates a broadcast through the backend API
  2. Extracts broadcastId and streamKey from the response
  3. Replays the mp4 as a live SRT stream to MediaMTX
  4. Confirms broadcast start through the backend API
  5. Sends streamer heartbeats until ffmpeg exits
  6. Stops the broadcast on exit

Dependencies:
  - curl
  - ffmpeg
  - python3
EOF
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

VIDEO_PATH=""
API_HOST=""
ACCESS_TOKEN=""
MEDIA_HOST="13.125.126.120"
MEDIA_PORT="8890"
BROADCAST_TITLE="file test broadcast"
AVATAR_ID="avatar-a"
HEARTBEAT_INTERVAL="10"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --video)
      VIDEO_PATH="${2:-}"
      shift 2
      ;;
    --api-host)
      API_HOST="${2:-}"
      shift 2
      ;;
    --token)
      ACCESS_TOKEN="${2:-}"
      shift 2
      ;;
    --media-host)
      MEDIA_HOST="${2:-}"
      shift 2
      ;;
    --media-port)
      MEDIA_PORT="${2:-}"
      shift 2
      ;;
    --title)
      BROADCAST_TITLE="${2:-}"
      shift 2
      ;;
    --avatar-id)
      AVATAR_ID="${2:-}"
      shift 2
      ;;
    --heartbeat-interval)
      HEARTBEAT_INTERVAL="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$VIDEO_PATH" || -z "$API_HOST" || -z "$ACCESS_TOKEN" ]]; then
  echo "Missing required arguments." >&2
  usage
  exit 1
fi

if [[ ! -f "$VIDEO_PATH" ]]; then
  echo "Video file not found: $VIDEO_PATH" >&2
  exit 1
fi

require_command curl
require_command ffmpeg
require_command python3

API_HOST="${API_HOST%/}"

FFMPEG_PID=""
HEARTBEAT_PID=""
BROADCAST_ID=""
STREAM_KEY=""
STOP_SENT="false"

stop_broadcast() {
  if [[ "$STOP_SENT" == "true" || -z "$BROADCAST_ID" ]]; then
    return 0
  fi

  echo ""
  echo "Stopping broadcast: $BROADCAST_ID"
  curl -sS -X POST "${API_HOST}/api/v1/broadcasts/${BROADCAST_ID}/stop" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" >/dev/null || true

  STOP_SENT="true"
}

cleanup() {
  set +e

  if [[ -n "$HEARTBEAT_PID" ]]; then
    kill "$HEARTBEAT_PID" >/dev/null 2>&1 || true
  fi

  if [[ -n "$FFMPEG_PID" ]]; then
    kill "$FFMPEG_PID" >/dev/null 2>&1 || true
  fi

  stop_broadcast
}

trap cleanup EXIT INT TERM

echo "Creating broadcast on backend..."
CREATE_RESPONSE="$(
  curl -sS -X POST "${API_HOST}/api/v1/broadcasts" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$(printf '{"title":"%s"}' "$BROADCAST_TITLE")"
)"

export CREATE_RESPONSE
PARSED_VALUES="$(
  python3 - <<'PY'
import json
import os
import sys

payload = json.loads(os.environ["CREATE_RESPONSE"])

def pick(obj, *keys):
    for key in keys:
        if isinstance(obj, dict) and key in obj and obj[key] is not None:
            return obj[key]
    return None

broadcast_id = (
    pick(payload, "broadcastId", "id")
    or pick(payload.get("data", {}), "broadcastId", "id")
)
stream_key = (
    pick(payload, "streamKey")
    or pick(payload.get("data", {}), "streamKey")
)

if not broadcast_id or not stream_key:
    sys.stderr.write("Could not parse broadcastId/streamKey from response:\n")
    sys.stderr.write(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.stderr.write("\n")
    sys.exit(1)

print(f"{broadcast_id}\n{stream_key}")
PY
)"

BROADCAST_ID="$(printf '%s\n' "$PARSED_VALUES" | sed -n '1p')"
STREAM_KEY="$(printf '%s\n' "$PARSED_VALUES" | sed -n '2p')"

echo "Broadcast created."
echo "  broadcastId: $BROADCAST_ID"
echo "  streamKey:   $STREAM_KEY"
echo "  mediaMtx:    ${MEDIA_HOST}:${MEDIA_PORT}"

SRT_URL="srt://${MEDIA_HOST}:${MEDIA_PORT}?streamid=publish:live/${STREAM_KEY}"

echo ""
echo "Starting ffmpeg replay..."
ffmpeg -re -i "$VIDEO_PATH" \
  -vf "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2" \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p \
  -c:a aac -ar 48000 -b:a 128k \
  -f mpegts \
  "$SRT_URL" &
FFMPEG_PID="$!"

sleep 2

echo "Confirming broadcast start..."
curl -sS -X POST "${API_HOST}/api/v1/broadcasts/${BROADCAST_ID}/start" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$(printf '{"avatarId":"%s"}' "$AVATAR_ID")" >/dev/null

echo "Broadcast started."

heartbeat_loop() {
  while true; do
    sleep "$HEARTBEAT_INTERVAL"
    curl -sS -X POST "${API_HOST}/api/v1/broadcasts/${BROADCAST_ID}/streamer-heartbeat" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" >/dev/null || true
  done
}

heartbeat_loop &
HEARTBEAT_PID="$!"

wait "$FFMPEG_PID"
FFMPEG_PID=""

kill "$HEARTBEAT_PID" >/dev/null 2>&1 || true
HEARTBEAT_PID=""

stop_broadcast

echo ""
echo "Done. Video replay finished and broadcast stop was requested."
