package bank.infrastructure.persistence;

import bank.application.eventstore.EventStore;
import bank.application.eventstore.TransferStream;
import bank.application.transfer.TransferRepository;
import bank.domain.transfer.Transfer;
import bank.domain.transfer.TransferEvent;

import java.util.List;
import java.util.Optional;

public final class EventSourcedTransferRepository implements TransferRepository {
    private final EventStore eventStore;

    public EventSourcedTransferRepository(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public Optional<Transfer> load(String transferId) {
        List<TransferEvent> events = eventStore.readStream(TransferStream.id(transferId), 0).stream()
                .map(envelope -> (TransferEvent) envelope.event())
                .toList();
        return events.isEmpty() ? Optional.empty() : Optional.of(Transfer.restore(events));
    }

    @Override
    public void save(Transfer transfer) {
        if (!transfer.hasPendingChanges()) {
            return;
        }
        eventStore.append(TransferStream.id(transfer.id()), transfer.version(), transfer.pendingChanges());
        transfer.markChangesAsCommitted();
    }
}
