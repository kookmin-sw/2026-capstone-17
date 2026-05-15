CREATE TABLE IF NOT EXISTS broadcast_platform_snapshot (
    snapshot_id VARCHAR(26) NOT NULL PRIMARY KEY,
    broadcast_id VARCHAR(26) NOT NULL,
    sampled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    concurrent_user_count BIGINT,
    category_type VARCHAR(30),
    category_id VARCHAR(100),
    category_name VARCHAR(255),
    live_title VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_broadcast_platform_snapshot_broadcast
        FOREIGN KEY (broadcast_id) REFERENCES broadcast(broadcast_id)
);

CREATE INDEX IF NOT EXISTS idx_broadcast_platform_snapshot_broadcast_id
    ON broadcast_platform_snapshot (broadcast_id);

CREATE INDEX IF NOT EXISTS idx_broadcast_platform_snapshot_sampled_at
    ON broadcast_platform_snapshot (sampled_at);
