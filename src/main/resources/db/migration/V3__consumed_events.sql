-- Phase 4: tracks consumed Kafka event IDs so the InventoryReserved consumer is idempotent
-- (EVT-005, EVT-010). A unique PK on id prevents double-processing even under concurrent
-- re-delivery.
CREATE TABLE consumed_events
(
    id          UUID                     NOT NULL,
    event_type  VARCHAR(100)             NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_consumed_events PRIMARY KEY (id)
);
