-- Alter existing event_status_enum to include 'IN_PROGRESS' if not already present
ALTER TYPE event_status_enum ADD VALUE IF NOT EXISTS 'IN_PROGRESS';

-- Add claim-and-lease columns for persistent distributed row claiming and backoff retry
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ DEFAULT NULL;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ DEFAULT NULL;

-- Index for efficient polling of claimable outbox records
CREATE INDEX IF NOT EXISTS idx_outbox_claim_lease 
ON outbox (status, next_retry_at, locked_until);

