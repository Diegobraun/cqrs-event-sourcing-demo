package bank.domain.account;

import java.math.BigDecimal;
import java.time.Instant;

public record MoneyWithdrawn(String aggregateId, BigDecimal amount, Instant occurredAt) implements AccountEvent {
}
