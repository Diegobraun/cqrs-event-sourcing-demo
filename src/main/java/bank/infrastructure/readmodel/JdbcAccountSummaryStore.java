package bank.infrastructure.readmodel;

import bank.application.query.AccountSummary;
import bank.application.query.AccountSummaryStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public class JdbcAccountSummaryStore implements AccountSummaryStore {
    private static final RowMapper<AccountSummary> MAPPER = (rs, rowNum) -> new AccountSummary(
            rs.getString("account_id"),
            rs.getString("owner"),
            rs.getBigDecimal("balance"),
            rs.getString("status"),
            rs.getInt("transactions"),
            rs.getLong("last_version"));

    private final JdbcClient jdbc;

    public JdbcAccountSummaryStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AccountSummary summary) {
        jdbc.sql("""
                        INSERT INTO account_summary (account_id, owner, balance, status, transactions, last_version)
                        VALUES (:accountId, :owner, :balance, :status, :transactions, :lastVersion)
                        ON CONFLICT (account_id) DO UPDATE
                        SET owner = EXCLUDED.owner, balance = EXCLUDED.balance, status = EXCLUDED.status,
                            transactions = EXCLUDED.transactions, last_version = EXCLUDED.last_version
                        """)
                .param("accountId", summary.accountId())
                .param("owner", summary.owner())
                .param("balance", summary.balance())
                .param("status", summary.status())
                .param("transactions", summary.transactions())
                .param("lastVersion", summary.lastVersion())
                .update();
    }

    @Override
    public Optional<AccountSummary> find(String accountId) {
        return jdbc.sql("SELECT * FROM account_summary WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query(MAPPER)
                .optional();
    }

    @Override
    public Collection<AccountSummary> findAll() {
        return jdbc.sql("SELECT * FROM account_summary").query(MAPPER).list();
    }

    @Override
    public void clear() {
        jdbc.sql("DELETE FROM account_summary").update();
    }
}
