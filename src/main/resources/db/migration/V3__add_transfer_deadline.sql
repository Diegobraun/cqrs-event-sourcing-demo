ALTER TABLE transfer_view
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN refunded   BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_transfer_view_pending_deadline ON transfer_view (expires_at)
    WHERE status IN ('REQUESTED', 'DEBITED');
