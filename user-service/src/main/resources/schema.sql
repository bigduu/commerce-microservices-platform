CREATE TABLE IF NOT EXISTS domain_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (aggregate_id, version)
);

CREATE INDEX IF NOT EXISTS idx_domain_events_aggregate_id ON domain_events(aggregate_id);

CREATE TABLE IF NOT EXISTS outbox_messages (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_messages_published_at ON outbox_messages(published_at);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(36) NOT NULL,
    consumer_id VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, consumer_id)
);
