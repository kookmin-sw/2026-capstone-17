DROP TABLE IF EXISTS tracking_session CASCADE;
DROP TABLE IF EXISTS streaming_platform_connection CASCADE;
DROP TABLE IF EXISTS broadcast CASCADE;
DROP TABLE IF EXISTS avatar CASCADE;
DROP TABLE IF EXISTS member_image CASCADE;
DROP TABLE IF EXISTS member CASCADE;

CREATE TABLE member (
                        member_id       VARCHAR(26) NOT NULL PRIMARY KEY,
                        kakao_id        BIGINT NOT NULL UNIQUE,
                        email           VARCHAR(100) UNIQUE,
                        nickname        VARCHAR(50),
                        role            VARCHAR(20) DEFAULT 'USER',
                        face_embedding  JSONB,
                        created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_member_email ON member(email);
CREATE INDEX idx_member_nickname ON member(nickname);

CREATE TABLE member_image (
                        image_id            VARCHAR(26) NOT NULL PRIMARY KEY,
                        member_id           VARCHAR(26) NOT NULL,
                        image_url           VARCHAR(255) NOT NULL,
                        object_key          VARCHAR(255) NOT NULL UNIQUE,
                        original_filename   VARCHAR(255),
                        content_type        VARCHAR(100),
                        size_bytes          BIGINT NOT NULL,
                        created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_member_image_member FOREIGN KEY (member_id) REFERENCES member(member_id)
);

CREATE INDEX idx_member_image_member_id ON member_image(member_id);

CREATE TABLE avatar (
                        avatar_id       VARCHAR(26) NOT NULL PRIMARY KEY,
                        avatar_name     VARCHAR(50),
                        object_key      VARCHAR(255) NOT NULL,
                        thumbnail_url   VARCHAR(255),
                        gender          VARCHAR(10),
                        age_group       VARCHAR(20),
                        ethnicity       VARCHAR(20),
                        is_active       BOOLEAN DEFAULT TRUE,
                        created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE broadcast (
                           broadcast_id    VARCHAR(26) NOT NULL PRIMARY KEY,
                           member_id       VARCHAR(26) NOT NULL,
                           stream_key      VARCHAR(100) NOT NULL UNIQUE,
                           title           VARCHAR(200),
                           status          VARCHAR(20) DEFAULT 'READY',
                           platform        VARCHAR(20) DEFAULT 'CHZZK',
                           output_mode     VARCHAR(20) DEFAULT 'CHZZK_RTMP',
                           platform_channel_id VARCHAR(100),
                           watch_url       VARCHAR(255),
                           hls_url         VARCHAR(255),
                           last_start_failure_reason VARCHAR(500),
                           started_at      TIMESTAMP WITH TIME ZONE,
                           ended_at        TIMESTAMP WITH TIME ZONE,
                           created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           deleted_at TIMESTAMP WITH TIME ZONE,
                           CONSTRAINT fk_broadcast_member FOREIGN KEY (member_id) REFERENCES member(member_id)
);

CREATE TABLE streaming_platform_connection (
                           connection_id              VARCHAR(26) NOT NULL PRIMARY KEY,
                           member_id                  VARCHAR(26) NOT NULL,
                           platform                   VARCHAR(20) NOT NULL,
                           platform_user_id           VARCHAR(100) NOT NULL,
                           platform_channel_id        VARCHAR(100) NOT NULL,
                           platform_channel_name      VARCHAR(100),
                           access_token               TEXT NOT NULL,
                           refresh_token              TEXT NOT NULL,
                           access_token_expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
                           connected_at               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           revoked_at                 TIMESTAMP WITH TIME ZONE,
                           created_at                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_streaming_platform_connection_member FOREIGN KEY (member_id) REFERENCES member(member_id),
                           CONSTRAINT uk_streaming_platform_connection_member_platform UNIQUE (member_id, platform)
);

CREATE INDEX idx_streaming_platform_connection_member_id ON streaming_platform_connection(member_id);
CREATE INDEX idx_streaming_platform_connection_platform_channel_id ON streaming_platform_connection(platform_channel_id);

CREATE TABLE tracking_session (
                                  tracking_session_id VARCHAR(26) NOT NULL PRIMARY KEY,
                                  broadcast_id        VARCHAR(26) NOT NULL,
                                  tracking_id         VARCHAR(100) NOT NULL,
                                  avatar_id           VARCHAR(26) NOT NULL,
                                  first_seen_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  last_seen_at        TIMESTAMP(3) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  is_active           BOOLEAN DEFAULT TRUE,
                                  created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_mapping_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcast(broadcast_id),
                                  CONSTRAINT fk_mapping_avatar FOREIGN KEY (avatar_id) REFERENCES avatar(avatar_id)
);

CREATE INDEX idx_tracking_session_lookup ON tracking_session (broadcast_id, tracking_id);
