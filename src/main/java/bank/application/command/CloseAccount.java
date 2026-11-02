package bank.application.command;

public record CloseAccount(String accountId) implements Command {
}
