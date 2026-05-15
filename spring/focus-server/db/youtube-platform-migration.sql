ALTER TABLE broadcast DROP CONSTRAINT IF EXISTS broadcast_platform_check;
ALTER TABLE broadcast
    ADD CONSTRAINT broadcast_platform_check
        CHECK (platform IN ('CHZZK', 'YOUTUBE'));

ALTER TABLE broadcast DROP CONSTRAINT IF EXISTS broadcast_output_mode_check;
ALTER TABLE broadcast
    ADD CONSTRAINT broadcast_output_mode_check
        CHECK (output_mode IN ('HLS', 'CHZZK_RTMP', 'YOUTUBE_RTMP'));

ALTER TABLE streaming_platform_connection DROP CONSTRAINT IF EXISTS streaming_platform_connection_platform_check;
ALTER TABLE streaming_platform_connection
    ADD CONSTRAINT streaming_platform_connection_platform_check
        CHECK (platform IN ('CHZZK', 'YOUTUBE'));
