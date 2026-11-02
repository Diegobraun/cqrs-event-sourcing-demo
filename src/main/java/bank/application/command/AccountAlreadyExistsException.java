package bank.application.command;

public class AccountAlreadyExistsException extends CommandRejectedException {
    public AccountAlreadyExistsException(String accountId) {
        super("Conta " + accountId + " ja existe");
    }
}
