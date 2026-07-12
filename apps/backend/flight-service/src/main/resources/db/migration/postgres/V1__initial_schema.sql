-- =========================================================
-- outbox_events
-- =========================================================
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

-- =========================================================
-- aircraft_types
-- =========================================================
CREATE TABLE aircraft_types (
                                id                      BIGSERIAL PRIMARY KEY,
                                model                   VARCHAR(100) NOT NULL,
                                manufacturer            VARCHAR(100),
                                total_seats             INTEGER NOT NULL,
                                economy_seats           INTEGER NOT NULL,
                                premium_economy_seats   INTEGER NOT NULL DEFAULT 0,
                                business_seats          INTEGER NOT NULL DEFAULT 0,
                                first_class_seats       INTEGER NOT NULL DEFAULT 0,
                                created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT uq_aircraft_types_model UNIQUE (model)
);

-- =========================================================
-- airlines
-- =========================================================
CREATE TABLE airlines (
                          id          BIGSERIAL PRIMARY KEY,
                          code        VARCHAR(3)   NOT NULL,
                          name        VARCHAR(100) NOT NULL,
                          country     VARCHAR(100),
                          logo_url    VARCHAR(255),
                          active      BOOLEAN      NOT NULL DEFAULT TRUE,
                          created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT uq_airlines_code UNIQUE (code)
);

-- =========================================================
-- airports
-- =========================================================
CREATE TABLE airports (
                          id          BIGSERIAL PRIMARY KEY,
                          iata_code   VARCHAR(3)  NOT NULL,
                          icao_code   VARCHAR(4),
                          name        VARCHAR(255) NOT NULL,
                          city        VARCHAR(100) NOT NULL,
                          country     VARCHAR(100) NOT NULL,
                          timezone    VARCHAR(50),
                          latitude    NUMERIC(10,6),
                          longitude   NUMERIC(10,6),
                          created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT uq_airports_iata_code UNIQUE (iata_code)
);

-- =========================================================
-- flights
-- =========================================================
CREATE TABLE flights (
                         id                          BIGSERIAL PRIMARY KEY,
                         flight_number               VARCHAR(10) NOT NULL,

                         airline_id                  BIGINT NOT NULL
                             REFERENCES airlines(id),
                         origin_airport_id           BIGINT NOT NULL
                             REFERENCES airports(id),
                         destination_airport_id      BIGINT NOT NULL
                             REFERENCES airports(id),
                         aircraft_type_id            BIGINT NOT NULL
                             REFERENCES aircraft_types(id),

                         departure_date              DATE NOT NULL,
                         departure_time              TIME NOT NULL,
                         arrival_date                DATE NOT NULL,
                         arrival_time                TIME NOT NULL,
                         duration_minutes            INTEGER NOT NULL,

                         base_price_economy          NUMERIC(10,2) NOT NULL,
                         base_price_premium_economy  NUMERIC(10,2),
                         base_price_business         NUMERIC(10,2),
                         base_price_first_class      NUMERIC(10,2),
                         currency                    VARCHAR(3) NOT NULL DEFAULT 'USD',

                         total_seats                 INTEGER NOT NULL,
                         available_seats             INTEGER NOT NULL,
                         economy_available           INTEGER NOT NULL,
                         premium_economy_available   INTEGER NOT NULL DEFAULT 0,
                         business_available          INTEGER NOT NULL DEFAULT 0,
                         first_class_available       INTEGER NOT NULL DEFAULT 0,

                         status                      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                             CONSTRAINT ck_flights_status
                                 CHECK (status IN ('SCHEDULED','DELAYED','CANCELLED','DEPARTED','ARRIVED','FULL')),

                         baggage_allowance_kg        INTEGER NOT NULL DEFAULT 23,
                         cabin_baggage_allowance_kg  INTEGER NOT NULL DEFAULT 7,
                         cancellation_policy         TEXT,

                         version                     INTEGER NOT NULL DEFAULT 0,

                         created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_flights_route_date
    ON flights (origin_airport_id, destination_airport_id, departure_date);

CREATE INDEX idx_flights_departure
    ON flights (departure_date, departure_time);

CREATE INDEX idx_flights_status
    ON flights (status);

