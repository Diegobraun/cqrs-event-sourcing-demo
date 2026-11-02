package bank.domain.account;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferDebited(String aggregateId, String transferId, BigDecimal amount, Instant occurredAt)
        implements AccountEvent {
}
