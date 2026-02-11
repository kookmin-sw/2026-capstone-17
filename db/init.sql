DROP TABLE IF EXISTS tracking_session CASCADE;
DROP TABLE IF EXISTS broadcast CASCADE;
DROP TABLE IF EXISTS avatar CASCADE;
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
                           hls_url         VARCHAR(255),
                           started_at      TIMESTAMP WITH TIME ZONE,
                           ended_at        TIMESTAMP WITH TIME ZONE,
                           created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_broadcast_member FOREIGN KEY (member_id) REFERENCES member(member_id)
);

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