package bank.infrastructure.readmodel;

import bank.application.query.TransferView;
import bank.application.query.TransferViewStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcTransferViewStore implements TransferViewStore {
    private final JdbcClient jdbc;

    public JdbcTransferViewStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(TransferView view) {
        jdbc.sql("""
                        INSERT INTO transfer_view (transfer_id, source_account_id, target_account_id, amount,
                                                   status, failure_reason, updated_at)
                        VALUES (:transferId, :source, :target, :amount, :status, :reason, :updatedAt)
                        ON CONFLICT (transfer_id) DO UPDATE
                        SET status = EXCLUDED.status, failure_reason = EXCLUDED.failure_reason,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("transferId", view.transferId())
                .param("source", view.sourceAccountId())
                .param("target", view.targetAccountId())
                .param("amount", view.amount())
                .param("status", view.status())
                .param("reason", view.failureReason())
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
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    @Override
    public void clear() {
        jdbc.sql("DELETE FROM transfer_view").update();
    }
}
