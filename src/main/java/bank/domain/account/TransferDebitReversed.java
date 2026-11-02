package bank.domain.account;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferDebitReversed(String aggregateId, String transferId, BigDecimal amount, String reason,
                                    Instant occurredAt) implements AccountEvent {
}
