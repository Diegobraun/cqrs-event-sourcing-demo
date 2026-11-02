package bank.application.query;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferView(String transferId, String sourceAccountId, String targetAccountId, BigDecimal amount,
                           String status, String failureReason, Instant updatedAt) {

    public TransferView advance(String newStatus, String reason, Instant at) {
        return new TransferView(transferId, sourceAccountId, targetAccountId, amount, newStatus,
                reason == null ? failureReason : reason, at);
    }
}
