package bank.application.command;

import java.math.BigDecimal;

public record Deposit(String accountId, BigDecimal amount) implements Command {
}
