package bank.infrastructure.readmodel;

import bank.application.query.TransferView;
import bank.application.query.TransferViewStore;
import bank.application.transfer.OverdueTransferFinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcTransferViewStore implements TransferViewStore, OverdueTransferFinder {
    private final JdbcClient jdbc;

    public JdbcTransferViewStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(TransferView view) {
        jdbc.sql("""
                        INSERT INTO transfer_view (transfer_id, source_account_id, target_account_id, amount,
                                                   status, failure_reason, refunded, expires_at, updated_at)
                        VALUES (:transferId, :source, :target, :amount, :status, :reason, :refunded, :expiresAt,
                                :updatedAt)
                        ON CONFLICT (transfer_id) DO UPDATE
                        SET status = EXCLUDED.status, failure_reason = EXCLUDED.failure_reason,
                            refunded = EXCLUDED.refunded, updated_at = EXCLUDED.updated_at
                        """)
                .param("transferId", view.transferId())
                .param("source", view.sourceAccountId())
                .param("target", view.targetAccountId())
                .param("amount", view.amount())
                .param("status", view.status())
                .param("reason", view.failureReason())
                .param("refunded", view.refunded())
                .param("expiresAt", view.expiresAt() == null ? null : Timestamp.from(view.expiresAt()))
                .param("updatedAt", Timestamp.from(view.updatedAt()))
                .update();
    }

    @Override
    public Optional<TransferView> find(String transferId) {
        return jdbc.sql("SELECT * FROM transfer_view WHERE transfer_id = :transferId")
                .param("transferId", transferId)
                .query((rs, rowNum) -> new TransferView(
                        rs.getString("transfer_id"),
                        rs.getString("source_account_id"),
                        rs.getString("target_account_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("status"),
                        rs.getString("failure_reason"),
                        rs.getBoolean("refunded"),
                        toInstant(rs.getTimestamp("expires_at")),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    @Override
    public List<String> findOverdue(Instant now) {
        return jdbc.sql("""
                        SELECT transfer_id FROM transfer_view
                        WHERE status IN ('REQUESTED', 'DEBITED') AND expires_at < :now
                        ORDER BY expires_at
                        """)
                .param("now", Timestamp.from(now))
                .query(String.class)
                .list();
    }

    @Override
    public void clear() {
        jdbc.sql("DELETE FROM transfer_view").update();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
