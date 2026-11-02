CREATE TABLE transfer_view (
    transfer_id       TEXT PRIMARY KEY,
    source_account_id TEXT          NOT NULL,
    target_account_id TEXT          NOT NULL,
    amount            NUMERIC(19,2) NOT NULL,
    status            TEXT          NOT NULL,
    failure_reason    TEXT,
    updated_at        TIMESTAMPTZ   NOT NULL
);
