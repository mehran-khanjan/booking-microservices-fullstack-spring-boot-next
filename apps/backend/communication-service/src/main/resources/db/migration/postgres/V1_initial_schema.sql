-- postgres/V2__create_inbox_events.sql
CREATE TABLE inbox_events (
                              id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                              event_id       VARCHAR(64) NOT NULL UNIQUE,
                              event_type     VARCHAR(100) NOT NULL,
                              payload        TEXT NOT NULL,
                              status         VARCHAR(20) NOT NULL,
                              retry_count    INTEGER DEFAULT 0,
                              error_message  TEXT,
                              processed_at   BIGINT,
                              received_at    BIGINT NOT NULL
);

CREATE UNIQUE INDEX idx_inbox_event_id ON inbox_events(event_id);
CREATE INDEX idx_inbox_status ON inbox_events(status);