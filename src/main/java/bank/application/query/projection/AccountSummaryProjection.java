package bank.application.query.projection;

import bank.application.eventstore.EventEnvelope;
import bank.application.query.AccountSummary;
import bank.application.query.AccountSummaryStore;
import bank.domain.account.AccountClosed;
import bank.domain.account.AccountEvent;
import bank.domain.account.AccountOpened;
import bank.domain.account.MoneyDeposited;
import bank.domain.account.MoneyWithdrawn;
import bank.domain.account.TransferCredited;
import bank.domain.account.TransferDebitReversed;
import bank.domain.account.TransferDebited;

import java.math.BigDecimal;

public final class AccountSummaryProjection implements Projection {
    private final AccountSummaryStore store;

    public AccountSummaryProjection(AccountSummaryStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "account-summary";
    }

    @Override
    public void handle(EventEnvelope envelope) {
        if (!(envelope.event() instanceof AccountEvent event)) {
            return;
        }
        if (event instanceof AccountOpened e) {
            store.save(new AccountSummary(e.aggregateId(), e.owner(), BigDecimal.ZERO, "OPEN", 0, envelope.version()));
            return;
        }
        store.find(event.aggregateId())
                .filter(current -> envelope.version() > current.lastVersion())
                .ifPresent(current -> store.save(evolve(current, event, envelope.version())));
    }

    @Override
    public void reset() {
        store.clear();
    }

    private AccountSummary evolve(AccountSummary s, AccountEvent event, long version) {
        return switch (event) {
            case AccountOpened e -> s;
            case MoneyDeposited e -> move(s, e.amount(), version);
            case MoneyWithdrawn e -> move(s, e.amount().negate(), version);
            case TransferDebited e -> move(s, e.amount().negate(), version);
            case TransferCredited e -> move(s, e.amount(), version);
            case TransferDebitReversed e -> move(s, e.amount(), version);
            case AccountClosed e -> new AccountSummary(s.accountId(), s.owner(), s.balance(),
                    "CLOSED", s.transactions(), version);
        };
    }

    private AccountSummary move(AccountSummary s, BigDecimal delta, long version) {
        return new AccountSummary(s.accountId(), s.owner(), s.balance().add(delta),
                s.status(), s.transactions() + 1, version);
    }
}
