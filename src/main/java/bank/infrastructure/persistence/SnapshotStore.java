package bank.infrastructure.persistence;

import bank.domain.account.AccountSnapshot;

import java.util.Optional;

public interface SnapshotStore {
    void save(AccountSnapshot snapshot);

    Optional<AccountSnapshot> find(String accountId);
}
