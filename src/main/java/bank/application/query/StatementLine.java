package bank.application.query;

import java.math.BigDecimal;
import java.time.Instant;

public record StatementLine(long version, Instant when, String description, BigDecimal amount, BigDecimal balanceAfter) {
}
