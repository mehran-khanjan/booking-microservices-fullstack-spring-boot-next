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
-- bookings
-- =========================================================
CREATE TABLE bookings (
                          id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          booking_reference       VARCHAR(6) NOT NULL UNIQUE,
                          user_id                 VARCHAR(100) NOT NULL,
                          booking_type            VARCHAR(20) NOT NULL,         -- SINGLE, ROUND_TRIP, MULTI_CITY
                          status                  VARCHAR(20) NOT NULL,         -- PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED
                          total_amount            NUMERIC(10,2) NOT NULL,
                          currency                VARCHAR(3) DEFAULT 'USD',
                          contact_email           VARCHAR(255) NOT NULL,
                          contact_phone           VARCHAR(20),
                          payment_transaction_id  VARCHAR(255),
                          hold_expires_at         BIGINT,                       -- epoch milliseconds
                          confirmed_at            BIGINT,
                          cancelled_at            BIGINT,
                          cancellation_reason     TEXT,
                          refund_amount           NUMERIC(10,2),
                          version                 INTEGER,
                          created_at              BIGINT NOT NULL,
                          updated_at              BIGINT NOT NULL
);

CREATE INDEX idx_bookings_reference ON bookings(booking_reference);
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_status ON bookings(status);

-- =========================================================
-- booking_flights
-- =========================================================
CREATE TABLE booking_flights (
                                 id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 booking_id               BIGINT NOT NULL,
                                 flight_id                BIGINT NOT NULL,
                                 flight_number            VARCHAR(10) NOT NULL,
                                 airline_name             VARCHAR(100) NOT NULL,
                                 origin_airport_code      VARCHAR(3) NOT NULL,
                                 destination_airport_code VARCHAR(3) NOT NULL,
                                 departure_date           DATE NOT NULL,
                                 departure_time           TIME NOT NULL,
                                 arrival_date             DATE NOT NULL,
                                 arrival_time             TIME NOT NULL,
                                 segment_order            INTEGER NOT NULL,
                                 cabin_class              VARCHAR(20) NOT NULL,
                                 base_price               NUMERIC(10,2) NOT NULL,
                                 taxes_fees               NUMERIC(10,2) DEFAULT 0,
                                 CONSTRAINT fk_booking_flights_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

-- =========================================================
-- passengers
-- =========================================================
CREATE TABLE passengers (
                            id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            booking_id              BIGINT NOT NULL,
                            title                   VARCHAR(10),
                            first_name              VARCHAR(100) NOT NULL,
                            last_name               VARCHAR(100) NOT NULL,
                            middle_name             VARCHAR(100),
                            date_of_birth           DATE NOT NULL,            -- uses native DATE type
                            gender                  VARCHAR(10),
                            nationality             VARCHAR(3),
                            passport_number         VARCHAR(50),
                            passport_expiry_date    DATE,
                            frequent_flyer_number   VARCHAR(50),
                            meal_preference         VARCHAR(50),
                            special_assistance      TEXT,
                            created_at              BIGINT NOT NULL,
                            CONSTRAINT fk_passengers_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

-- =========================================================
-- additional_services
-- =========================================================
CREATE TABLE additional_services (
                                     id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     booking_id      BIGINT NOT NULL,
                                     passenger_id    BIGINT,
                                     service_type    VARCHAR(50) NOT NULL,        -- EXTRA_BAGGAGE, MEAL, TRAVEL_INSURANCE, etc.
                                     description     TEXT,
                                     price           NUMERIC(10,2) NOT NULL,
                                     currency        VARCHAR(3) DEFAULT 'USD',
                                     created_at      BIGINT NOT NULL,
                                     CONSTRAINT fk_additional_services_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
                                     CONSTRAINT fk_additional_services_passenger FOREIGN KEY (passenger_id) REFERENCES passengers(id) ON DELETE SET NULL
);

CREATE INDEX idx_additional_services_booking ON additional_services(booking_id);
CREATE INDEX idx_additional_services_passenger ON additional_services(passenger_id);
