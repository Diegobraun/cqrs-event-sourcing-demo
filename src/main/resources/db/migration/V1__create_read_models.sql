CREATE TABLE projection_checkpoints (
    projection_name TEXT PRIMARY KEY,
    position        BIGINT NOT NULL,
    stream_cursor   TEXT
);

CREATE TABLE account_summary (
    account_id   TEXT PRIMARY KEY,
    owner        TEXT          NOT NULL,
    balance      NUMERIC(19,2) NOT NULL,
    status       TEXT          NOT NULL,
    transactions INT           NOT NULL,
    last_version BIGINT        NOT NULL
);

CREATE TABLE statement_lines (
    account_id    TEXT          NOT NULL,
    version       BIGINT        NOT NULL,
    occurred_at   TIMESTAMPTZ   NOT NULL,
    description   TEXT          NOT NULL,
    amount        NUMERIC(19,2) NOT NULL,
    balance_after NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (account_id, version)
);
