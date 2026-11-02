package bank.application.command;

import java.math.BigDecimal;

public record DebitForTransfer(String accountId, String transferId, BigDecimal amount) implements Command {
}
