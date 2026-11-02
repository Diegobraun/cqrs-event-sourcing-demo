package bank.application.eventstore;

public final class AccountStream {
    private AccountStream() {
    }

    public static String id(String accountId) {
        return "account-" + accountId;
    }
}
