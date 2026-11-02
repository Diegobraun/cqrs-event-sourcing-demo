package bank.application.command;

import java.math.BigDecimal;

public record ReverseTransferDebit(String accountId, String transferId, BigDecimal amount, String reason)
        implements Command {
}
