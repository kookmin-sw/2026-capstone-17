# YouTube Platform Integration

## Flow

1. Client opens `GET /api/v1/platforms/youtube/connect`.
2. Spring stores OAuth `state` in Redis and redirects the user to Google OAuth.
3. Google redirects to `GET /api/v1/platforms/youtube/callback`.
4. Spring exchanges the authorization code for access/refresh tokens.
5. Spring calls YouTube `channels?mine=true` and stores the connected channel.
6. Broadcast created with `outputMode=YOUTUBE_RTMP` creates the YouTube target on start:
   - `liveBroadcast`
   - `liveStream`
   - broadcast-stream binding
7. Spring passes the YouTube RTMP ingest URL to FastAPI.
8. FastAPI relays MediaMTX RTSP input to YouTube RTMP.

## Required Google Cloud Setup

- Enable YouTube Data API v3.
- Configure OAuth consent screen.
- Create an OAuth client for a web application.
- Add the redirect URI used by Spring.
- Ensure the YouTube channel is allowed to live stream.

## Required Environment Variables

- `YOUTUBE_CLIENT_ID`
- `YOUTUBE_CLIENT_SECRET`
- `YOUTUBE_REDIRECT_URI`

## Broadcast Selection

- Set `outputMode` to `YOUTUBE_RTMP` in `POST /api/v1/broadcasts` to start that broadcast through YouTube.
- If `outputMode` is omitted, Spring uses `focus.broadcast.output-mode` / `BROADCAST_OUTPUT_MODE`.
- Keep `BROADCAST_FALLBACK_TO_HLS=true` if local HLS should be used when YouTube or CHZZK setup fails.

## Optional Environment Variables

- `YOUTUBE_LIVE_PRIVACY_STATUS` defaults to `private`.
- `YOUTUBE_LIVE_LATENCY_PREFERENCE` defaults to `low`.
- `YOUTUBE_PREFER_RTMPS` defaults to `false`.
- `YOUTUBE_SCOPE` defaults to `https://www.googleapis.com/auth/youtube.force-ssl`.

## Current Limitations

- YouTube live start relies on `enableAutoStart=true`.
- Broadcast stop attempts to transition the YouTube broadcast to `complete`, but will not fail the local stop flow if the platform transition fails.
- Brand account/channel selection must be validated during real OAuth testing.

## Official References

- YouTube Live Streaming API reference: https://developers.google.com/youtube/v3/live/docs
- LiveBroadcasts resource: https://developers.google.com/youtube/v3/live/docs/liveBroadcasts
- LiveStreams insert: https://developers.google.com/youtube/v3/live/docs/liveStreams/insert
- LiveBroadcasts bind: https://developers.google.com/youtube/v3/live/docs/liveBroadcasts/bind
