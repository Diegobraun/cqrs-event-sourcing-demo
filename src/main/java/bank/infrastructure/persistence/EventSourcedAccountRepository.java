package bank.infrastructure.persistence;

import bank.application.command.AccountRepository;
import bank.application.eventstore.AccountStream;
import bank.application.eventstore.EventEnvelope;
import bank.application.eventstore.EventStore;
import bank.domain.account.Account;
import bank.domain.account.AccountEvent;
import bank.domain.account.AccountSnapshot;

import java.util.List;
import java.util.Optional;

public final class EventSourcedAccountRepository implements AccountRepository {
    private final EventStore eventStore;
    private final SnapshotStore snapshots;
    private final int snapshotEvery;

    public EventSourcedAccountRepository(EventStore eventStore, SnapshotStore snapshots, int snapshotEvery) {
        this.eventStore = eventStore;
        this.snapshots = snapshots;
        this.snapshotEvery = snapshotEvery;
    }

    @Override
    public Optional<Account> load(String accountId) {
        AccountSnapshot snapshot = snapshots.find(accountId).orElse(null);
        long afterVersion = snapshot == null ? 0 : snapshot.version();
        List<AccountEvent> events = toAccountEvents(eventStore.readStream(AccountStream.id(accountId), afterVersion));
        if (snapshot == null && events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Account.restore(snapshot, events));
    }

    @Override
    public Optional<Account> loadAtVersion(String accountId, long version) {
        List<AccountEvent> events = toAccountEvents(eventStore.readStream(AccountStream.id(accountId), 0).stream()
                .filter(envelope -> envelope.version() <= version)
                .toList());
        return events.isEmpty() ? Optional.empty() : Optional.of(Account.restore(null, events));
    }

    @Override
    public void save(Account account) {
        List<AccountEvent> changes = account.pendingChanges();
        if (changes.isEmpty()) {
            return;
        }
        long versionBefore = account.version();
        eventStore.append(AccountStream.id(account.id()), versionBefore, changes);
        account.markChangesAsCommitted();
        if (account.version() / snapshotEvery > versionBefore / snapshotEvery) {
            snapshots.save(account.snapshot());
        }
    }

    private static List<AccountEvent> toAccountEvents(List<EventEnvelope> envelopes) {
        return envelopes.stream().map(envelope -> (AccountEvent) envelope.event()).toList();
    }
}
