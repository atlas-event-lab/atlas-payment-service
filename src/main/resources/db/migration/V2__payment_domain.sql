-- Phase 2: Payment domain (services/payment/service.md §Owns).
-- Entities owned by Payment Service: Payment, PaymentAttempt, PaymentProviderResponse.
-- Payment state is independent from Booking state (state_machine.md).

CREATE TABLE payments
(
    id                      UUID                     NOT NULL,
    booking_id              UUID                     NOT NULL,
    status                  VARCHAR(20)              NOT NULL,
    amount                  NUMERIC(19, 2)           NOT NULL,
    currency                VARCHAR(3)               NOT NULL,
    reason                  VARCHAR(500),
    provider_transaction_id VARCHAR(100),
    correlation_id          VARCHAR(36),
    saga_id                 VARCHAR(36),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    -- A Payment belongs to exactly one Booking (state_machine.md §Consistency Rules).
    CONSTRAINT uq_payments_booking_id UNIQUE (booking_id)
);

CREATE TABLE payment_attempts
(
    id             UUID                     NOT NULL,
    payment_id     UUID                     NOT NULL,
    attempt_number INTEGER                  NOT NULL,
    outcome        VARCHAR(20)              NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    error_detail   VARCHAR(500),
    CONSTRAINT pk_payment_attempts PRIMARY KEY (id),
    CONSTRAINT fk_payment_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT uq_payment_attempts_payment_number UNIQUE (payment_id, attempt_number)
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts (payment_id);

CREATE TABLE payment_provider_responses
(
    id              UUID                     NOT NULL,
    attempt_id      UUID                     NOT NULL,
    http_status     INTEGER                  NOT NULL,
    provider_status VARCHAR(50),
    transaction_id  VARCHAR(100),
    reason          VARCHAR(500),
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_payment_provider_responses PRIMARY KEY (id),
    CONSTRAINT fk_payment_provider_responses_attempt FOREIGN KEY (attempt_id) REFERENCES payment_attempts (id),
    CONSTRAINT uq_payment_provider_responses_attempt UNIQUE (attempt_id)
);
