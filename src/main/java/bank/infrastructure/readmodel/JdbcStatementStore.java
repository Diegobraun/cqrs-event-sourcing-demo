package bank.infrastructure.readmodel;

import bank.application.query.StatementLine;
import bank.application.query.StatementStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcStatementStore implements StatementStore {
    private final JdbcClient jdbc;

    public JdbcStatementStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String accountId, StatementLine line) {
        jdbc.sql("""
                        INSERT INTO statement_lines (account_id, version, occurred_at, description, amount, balance_after)
                        VALUES (:accountId, :version, :occurredAt, :description, :amount, :balanceAfter)
                        ON CONFLICT (account_id, version) DO NOTHING
                        """)
                .param("accountId", accountId)
                .param("version", line.version())
                .param("occurredAt", Timestamp.from(line.when()))
                .param("description", line.description())
                .param("amount", line.amount())
                .param("balanceAfter", line.balanceAfter())
                .update();
    }

    @Override
    public List<StatementLine> find(String accountId) {
        return jdbc.sql("""
                        SELECT version, occurred_at, description, amount, balance_after
                        FROM statement_lines
                        WHERE account_id = :accountId
                        ORDER BY version
                        """)
                .param("accountId", accountId)
                .query((rs, rowNum) -> new StatementLine(
                        rs.getLong("version"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("description"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("balance_after")))
                .list();
    }

    @Override
    public void clear() {
        jdbc.sql("DELETE FROM statement_lines").update();
    }
}
