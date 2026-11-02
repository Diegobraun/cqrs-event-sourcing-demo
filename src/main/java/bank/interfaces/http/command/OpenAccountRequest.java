package bank.interfaces.http.command;

import jakarta.validation.constraints.NotBlank;

public record OpenAccountRequest(@NotBlank String accountId, @NotBlank String owner) {
}
