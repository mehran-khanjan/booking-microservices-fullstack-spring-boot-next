CREATE TABLE outbox_events (
                               id              INTEGER PRIMARY KEY AUTOINCREMENT,
                               event_id        TEXT NOT NULL UNIQUE,
                               event_type      TEXT NOT NULL,
                               aggregate_type  TEXT NOT NULL,
                               aggregate_id    TEXT NOT NULL,
                               payload         TEXT NOT NULL,
                               status          TEXT NOT NULL,
                               routing_key     TEXT NOT NULL,
                               exchange        TEXT NOT NULL,
                               retry_count     INTEGER DEFAULT 0,
                               error_message   TEXT,
                               processed_at    INTEGER,
                               created_at      INTEGER NOT NULL,
                               updated_at      INTEGER NOT NULL
);

CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);