package bank.application.query;

import bank.application.eventstore.AccountStream;
import bank.application.eventstore.EventStore;

import java.util.List;

public final class AccountAuditService {
    private final EventStore eventStore;

    public AccountAuditService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public List<AuditEntry> history(String accountId) {
        List<AuditEntry> history = eventStore.readStream(AccountStream.id(accountId), 0).stream()
                .map(envelope -> new AuditEntry(envelope.position(), envelope.version(),
                        envelope.event().getClass().getSimpleName(), envelope.recordedAt(), envelope.event()))
                .toList();
        if (history.isEmpty()) {
            throw new AccountViewNotFoundException(accountId);
        }
        return history;
    }
}
