package bank.interfaces.http.command;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MoneyRequest(@NotNull BigDecimal amount) {
}
