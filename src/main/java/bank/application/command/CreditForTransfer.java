package bank.application.command;

import java.math.BigDecimal;

public record CreditForTransfer(String accountId, String transferId, BigDecimal amount) implements Command {
}
