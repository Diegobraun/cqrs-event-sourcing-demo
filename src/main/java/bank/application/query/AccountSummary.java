package bank.application.query;

import java.math.BigDecimal;

public record AccountSummary(String accountId, String owner, BigDecimal balance, String status,
                             int transactions, long lastVersion) {
}
