package bank.application.transfer;

import java.math.BigDecimal;

public record RequestTransfer(String transferId, String sourceAccountId, String targetAccountId, BigDecimal amount) {
}
