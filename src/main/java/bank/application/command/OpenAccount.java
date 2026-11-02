package bank.application.command;

public record OpenAccount(String accountId, String owner) implements Command {
}
