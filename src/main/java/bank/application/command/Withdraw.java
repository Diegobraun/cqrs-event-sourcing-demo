package bank.application.command;

import java.math.BigDecimal;

public record Withdraw(String accountId, BigDecimal amount) implements Command {
}
