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
-- bookings
-- =========================================================
CREATE TABLE bookings (
                          id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                          booking_reference    TEXT NOT NULL UNIQUE,
                          user_id              TEXT NOT NULL,
                          booking_type         TEXT NOT NULL,          -- SINGLE, ROUND_TRIP, MULTI_CITY
                          status               TEXT NOT NULL,          -- PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED
                          total_amount         NUMERIC(10,2) NOT NULL,
                          currency             TEXT DEFAULT 'USD',
                          contact_email        TEXT NOT NULL,
                          contact_phone        TEXT,
                          payment_transaction_id TEXT,
                          hold_expires_at      INTEGER,                -- epoch milliseconds
                          confirmed_at         INTEGER,
                          cancelled_at         INTEGER,
                          cancellation_reason  TEXT,
                          refund_amount        NUMERIC(10,2),
                          version              INTEGER,
                          created_at           INTEGER NOT NULL,
                          updated_at           INTEGER NOT NULL
);

CREATE INDEX idx_bookings_reference ON bookings(booking_reference);
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_status ON bookings(status);

-- =========================================================
-- booking_flights
-- =========================================================
CREATE TABLE booking_flights (
                                 id                       INTEGER PRIMARY KEY AUTOINCREMENT,
                                 booking_id               INTEGER NOT NULL,
                                 flight_id                INTEGER NOT NULL,
                                 flight_number            TEXT NOT NULL,
                                 airline_name             TEXT NOT NULL,
                                 origin_airport_code      TEXT NOT NULL,
                                 destination_airport_code TEXT NOT NULL,
                                 departure_date           TEXT NOT NULL,
                                 departure_time           TEXT NOT NULL,
                                 arrival_date             TEXT NOT NULL,
                                 arrival_time             TEXT NOT NULL,
                                 segment_order            INTEGER NOT NULL,
                                 cabin_class              TEXT NOT NULL,
                                 base_price               NUMERIC(10,2) NOT NULL,
                                 taxes_fees               NUMERIC(10,2) DEFAULT 0,
                                 FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

-- =========================================================
-- passengers
-- =========================================================
CREATE TABLE passengers (
                            id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                            booking_id              INTEGER NOT NULL,
                            title                   TEXT,
                            first_name              TEXT NOT NULL,
                            last_name               TEXT NOT NULL,
                            middle_name             TEXT,
                            date_of_birth           TEXT NOT NULL,       -- ISO date (YYYY-MM-DD)
                            gender                  TEXT,
                            nationality             TEXT,
                            passport_number         TEXT,
                            passport_expiry_date    TEXT,
                            frequent_flyer_number   TEXT,
                            meal_preference         TEXT,
                            special_assistance      TEXT,
                            created_at              INTEGER NOT NULL,
                            FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

-- =========================================================
-- additional_services
-- =========================================================
CREATE TABLE additional_services (
                                     id              INTEGER PRIMARY KEY AUTOINCREMENT,
                                     booking_id      INTEGER NOT NULL,
                                     passenger_id    INTEGER,
                                     service_type    TEXT NOT NULL,               -- EXTRA_BAGGAGE, MEAL, TRAVEL_INSURANCE, etc.
                                     description     TEXT,
                                     price           NUMERIC(10,2) NOT NULL,
                                     currency        TEXT DEFAULT 'USD',
                                     created_at      INTEGER NOT NULL,
                                     FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
                                     FOREIGN KEY (passenger_id) REFERENCES passengers(id) ON DELETE SET NULL
);

CREATE INDEX idx_additional_services_booking ON additional_services(booking_id);
CREATE INDEX idx_additional_services_passenger ON additional_services(passenger_id);
