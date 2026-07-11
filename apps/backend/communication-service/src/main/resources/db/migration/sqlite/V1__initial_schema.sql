CREATE TABLE inbox_events (
                              id             INTEGER PRIMARY KEY AUTOINCREMENT,
                              event_id       TEXT NOT NULL UNIQUE,
                              event_type     TEXT NOT NULL,
                              payload        TEXT NOT NULL,
                              status         TEXT NOT NULL,
                              retry_count    INTEGER DEFAULT 0,
                              error_message  TEXT,
                              processed_at   INTEGER,
                              received_at    INTEGER NOT NULL
);

CREATE UNIQUE INDEX idx_inbox_event_id ON inbox_events(event_id);
CREATE INDEX idx_inbox_status ON inbox_events(status);