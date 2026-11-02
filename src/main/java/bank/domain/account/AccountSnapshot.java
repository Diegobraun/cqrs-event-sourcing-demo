package bank.domain.account;

import java.math.BigDecimal;
import java.util.Set;

public record AccountSnapshot(String accountId, String owner, BigDecimal balance, AccountStatus status,
                              Set<String> transferOperations, long version) {
}
