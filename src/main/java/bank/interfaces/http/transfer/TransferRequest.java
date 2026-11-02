package bank.interfaces.http.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(@NotBlank String transferId,
                              @NotBlank String sourceAccountId,
                              @NotBlank String targetAccountId,
                              @NotNull BigDecimal amount) {
}
