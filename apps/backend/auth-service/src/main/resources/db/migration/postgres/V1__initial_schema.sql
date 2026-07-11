CREATE TABLE outbox_events (
                               id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               event_id        VARCHAR(64) NOT NULL UNIQUE,
                               event_type      VARCHAR(100) NOT NULL,
                               aggregate_type  VARCHAR(100) NOT NULL,
                               aggregate_id    VARCHAR(100) NOT NULL,
                               payload         TEXT NOT NULL,
                               status          VARCHAR(20) NOT NULL,
                               routing_key     VARCHAR(255) NOT NULL,
                               exchange        VARCHAR(255) NOT NULL,
                               retry_count     INTEGER DEFAULT 0,
                               error_message   TEXT,
                               processed_at    BIGINT,
                               created_at      BIGINT NOT NULL,
                               updated_at      BIGINT NOT NULL
);

CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);