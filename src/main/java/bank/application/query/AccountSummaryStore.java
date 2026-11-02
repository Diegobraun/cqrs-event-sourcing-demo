package bank.application.query;

import java.util.Collection;
import java.util.Optional;

public interface AccountSummaryStore {
    void save(AccountSummary summary);

    Optional<AccountSummary> find(String accountId);

    Collection<AccountSummary> findAll();

    void clear();
}
