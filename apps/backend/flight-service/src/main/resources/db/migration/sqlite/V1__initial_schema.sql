-- =========================================================
-- outbox_events
-- =========================================================
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

-- =========================================================
-- aircraft_types
-- =========================================================
CREATE TABLE aircraft_types (
                                id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                                model                   VARCHAR(100) NOT NULL UNIQUE,
                                manufacturer            VARCHAR(100),
                                total_seats             INTEGER NOT NULL,
                                economy_seats           INTEGER NOT NULL,
                                premium_economy_seats   INTEGER NOT NULL DEFAULT 0,
                                business_seats          INTEGER NOT NULL DEFAULT 0,
                                first_class_seats       INTEGER NOT NULL DEFAULT 0,
                                created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- airlines
-- =========================================================
CREATE TABLE airlines (
                          id          INTEGER PRIMARY KEY AUTOINCREMENT,
                          code        VARCHAR(3)   NOT NULL UNIQUE,
                          name        VARCHAR(100) NOT NULL,
                          country     VARCHAR(100),
                          logo_url    VARCHAR(255),
                          active      INTEGER      NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
                          created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- airports
-- =========================================================
CREATE TABLE airports (
                          id          INTEGER PRIMARY KEY AUTOINCREMENT,
                          iata_code   VARCHAR(3)   NOT NULL UNIQUE,
                          icao_code   VARCHAR(4),
                          name        VARCHAR(255) NOT NULL,
                          city        VARCHAR(100) NOT NULL,
                          country     VARCHAR(100) NOT NULL,
                          timezone    VARCHAR(50),
                          latitude    DECIMAL(10,6),
                          longitude   DECIMAL(10,6),
                          created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- flights
-- =========================================================
CREATE TABLE flights (
                         id                          INTEGER PRIMARY KEY AUTOINCREMENT,
                         flight_number               VARCHAR(10) NOT NULL,

                         airline_id                  INTEGER NOT NULL
                             REFERENCES airlines(id),
                         origin_airport_id           INTEGER NOT NULL
                             REFERENCES airports(id),
                         destination_airport_id      INTEGER NOT NULL
                             REFERENCES airports(id),
                         aircraft_type_id            INTEGER NOT NULL
                             REFERENCES aircraft_types(id),

                         departure_date               DATE NOT NULL,
                         departure_time               TIME NOT NULL,
                         arrival_date                 DATE NOT NULL,
                         arrival_time                 TIME NOT NULL,
                         duration_minutes             INTEGER NOT NULL,

                         base_price_economy           DECIMAL(10,2) NOT NULL,
                         base_price_premium_economy   DECIMAL(10,2),
                         base_price_business          DECIMAL(10,2),
                         base_price_first_class       DECIMAL(10,2),
                         currency                     VARCHAR(3) NOT NULL DEFAULT 'USD',

                         total_seats                  INTEGER NOT NULL,
                         available_seats               INTEGER NOT NULL,
                         economy_available             INTEGER NOT NULL,
                         premium_economy_available     INTEGER NOT NULL DEFAULT 0,
                         business_available            INTEGER NOT NULL DEFAULT 0,
                         first_class_available         INTEGER NOT NULL DEFAULT 0,

                         status                        VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                             CHECK (status IN ('SCHEDULED','DELAYED','CANCELLED','DEPARTED','ARRIVED','FULL')),

                         baggage_allowance_kg          INTEGER NOT NULL DEFAULT 23,
                         cabin_baggage_allowance_kg    INTEGER NOT NULL DEFAULT 7,
                         cancellation_policy           TEXT,

                         version                       INTEGER NOT NULL DEFAULT 0,

                         created_at                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_flights_route_date
    ON flights (origin_airport_id, destination_airport_id, departure_date);

CREATE INDEX idx_flights_departure
    ON flights (departure_date, departure_time);

CREATE INDEX idx_flights_status
    ON flights (status);

