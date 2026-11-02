package bank.application.query;

public class AccountViewNotFoundException extends RuntimeException {
    public AccountViewNotFoundException(String accountId) {
        super("Conta " + accountId + " nao encontrada");
    }
}
