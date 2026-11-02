package bank.application.command;

import bank.domain.account.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> load(String accountId);

    Optional<Account> loadAtVersion(String accountId, long version);

    void save(Account account);
}
