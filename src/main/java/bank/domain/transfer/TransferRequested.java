package bank.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferRequested(String aggregateId, String sourceAccountId, String targetAccountId, BigDecimal amount,
                                Instant expiresAt, Instant occurredAt) implements TransferEvent {
}
