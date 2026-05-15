DROP TABLE IF EXISTS tracking_session CASCADE;
DROP TABLE IF EXISTS broadcast_platform_snapshot CASCADE;
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

CREATE TABLE broadcast_media_asset (
                           media_asset_id      VARCHAR(26) NOT NULL PRIMARY KEY,
                           broadcast_id        VARCHAR(26) NOT NULL,
                           asset_type          VARCHAR(30) NOT NULL,
                           storage_provider    VARCHAR(20) NOT NULL,
                           storage_key         VARCHAR(500) NOT NULL,
                           storage_url         VARCHAR(1000),
                           duration_sec        BIGINT,
                           resolution_width    INT,
                           resolution_height   INT,
                           file_size_bytes     BIGINT,
                           created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_broadcast_media_asset_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcast(broadcast_id)
);

CREATE INDEX idx_broadcast_media_asset_broadcast_id ON broadcast_media_asset (broadcast_id);

CREATE TABLE broadcast_analysis_job (
                           analysis_job_id     VARCHAR(26) NOT NULL PRIMARY KEY,
                           broadcast_id        VARCHAR(26) NOT NULL,
                           media_asset_id      VARCHAR(26) NOT NULL,
                           job_type            VARCHAR(30) NOT NULL,
                           job_status          VARCHAR(20) NOT NULL,
                           completed_at        TIMESTAMP WITH TIME ZONE,
                           error_message       VARCHAR(2000),
                           created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_broadcast_analysis_job_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcast(broadcast_id),
                           CONSTRAINT fk_broadcast_analysis_job_media_asset FOREIGN KEY (media_asset_id) REFERENCES broadcast_media_asset(media_asset_id)
);

CREATE INDEX idx_broadcast_analysis_job_broadcast_id ON broadcast_analysis_job (broadcast_id);
CREATE INDEX idx_broadcast_analysis_job_status ON broadcast_analysis_job (job_status);

CREATE TABLE broadcast_platform_snapshot (
                           snapshot_id         VARCHAR(26) NOT NULL PRIMARY KEY,
                           broadcast_id        VARCHAR(26) NOT NULL,
                           sampled_at          TIMESTAMP WITH TIME ZONE NOT NULL,
                           concurrent_user_count BIGINT,
                           category_type       VARCHAR(30),
                           category_id         VARCHAR(100),
                           category_name       VARCHAR(255),
                           live_title          VARCHAR(255),
                           created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_broadcast_platform_snapshot_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcast(broadcast_id)
);

CREATE INDEX idx_broadcast_platform_snapshot_broadcast_id ON broadcast_platform_snapshot (broadcast_id);
CREATE INDEX idx_broadcast_platform_snapshot_sampled_at ON broadcast_platform_snapshot (sampled_at);

CREATE TABLE broadcast_ai_report (
                           ai_report_id        VARCHAR(26) NOT NULL PRIMARY KEY,
                           broadcast_id        VARCHAR(26) NOT NULL,
                           analysis_job_id     VARCHAR(26) NOT NULL,
                           report_type         VARCHAR(30) NOT NULL,
                           title               VARCHAR(255) NOT NULL,
                           summary             VARCHAR(5000) NOT NULL,
                           strengths_json      JSONB NOT NULL DEFAULT '[]'::jsonb,
                           weaknesses_json     JSONB NOT NULL DEFAULT '[]'::jsonb,
                           action_items_json   JSONB NOT NULL DEFAULT '[]'::jsonb,
                           peak_viewer_count   BIGINT,
                           peak_viewer_occurred_at TIMESTAMP WITH TIME ZONE,
                           peak_scene_description VARCHAR(2000),
                           total_replaced_face_count BIGINT,
                           max_simultaneous_crowd_count INT,
                           content_ratios_json JSONB NOT NULL DEFAULT '[]'::jsonb,
                           created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_broadcast_ai_report_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcast(broadcast_id),
                           CONSTRAINT fk_broadcast_ai_report_analysis_job FOREIGN KEY (analysis_job_id) REFERENCES broadcast_analysis_job(analysis_job_id)
);

CREATE INDEX idx_broadcast_ai_report_broadcast_id ON broadcast_ai_report (broadcast_id);

CREATE TABLE broadcast_highlight_candidate (
                           highlight_candidate_id VARCHAR(26) NOT NULL PRIMARY KEY,
                           broadcast_id           VARCHAR(26) NOT NULL,
                           analysis_job_id        VARCHAR(26) NOT NULL,
                           start_sec              BIGINT NOT NULL,
                           end_sec                BIGINT NOT NULL,
                           title                  VARCHAR(255) NOT NULL,
                           reason                 VARCHAR(2000) NOT NULL,
                           score                  DOUBLE PRECISION NOT NULL,
                           created_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_broadcast_highlight_candidate_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcast(broadcast_id),
                           CONSTRAINT fk_broadcast_highlight_candidate_analysis_job FOREIGN KEY (analysis_job_id) REFERENCES broadcast_analysis_job(analysis_job_id)
);

CREATE INDEX idx_broadcast_highlight_candidate_broadcast_id ON broadcast_highlight_candidate (broadcast_id);
CREATE INDEX idx_broadcast_highlight_candidate_analysis_job_id ON broadcast_highlight_candidate (analysis_job_id);

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
