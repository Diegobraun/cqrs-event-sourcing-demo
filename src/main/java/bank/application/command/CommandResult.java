package bank.application.command;

public record CommandResult(String accountId, long version) {
}
