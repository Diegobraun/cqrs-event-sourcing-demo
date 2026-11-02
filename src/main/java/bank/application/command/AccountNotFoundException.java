package bank.application.command;

public class AccountNotFoundException extends CommandRejectedException {
    public AccountNotFoundException(String accountId) {
        super("Conta " + accountId + " nao encontrada");
    }
}
