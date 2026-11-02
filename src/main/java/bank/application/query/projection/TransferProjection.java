package bank.application.query.projection;

import bank.application.eventstore.EventEnvelope;
import bank.application.query.TransferView;
import bank.application.query.TransferViewStore;
import bank.domain.account.TransferCredited;
import bank.domain.account.TransferDebitReversed;
import bank.domain.account.TransferDebited;
import bank.domain.transfer.TransferCompleted;
import bank.domain.transfer.TransferFailed;
import bank.domain.transfer.TransferRequested;

import java.time.Instant;
import java.util.Map;

public final class TransferProjection implements Projection {
    private static final Map<String, Integer> STAGE = Map.of(
            "REQUESTED", 0,
            "DEBITED", 1,
            "CREDITED", 2,
            "REVERSED", 2,
            "COMPLETED", 3,
            "FAILED", 3);

    private final TransferViewStore store;

    public TransferProjection(TransferViewStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "transfer-status";
    }

    @Override
    public void handle(EventEnvelope envelope) {
        switch (envelope.event()) {
            case TransferRequested e -> {
                if (store.find(e.aggregateId()).isEmpty()) {
                    store.save(new TransferView(e.aggregateId(), e.sourceAccountId(), e.targetAccountId(), e.amount(),
                            "REQUESTED", null, e.occurredAt()));
                }
            }
            case TransferDebited e -> advance(e.transferId(), "DEBITED", null, e.occurredAt());
            case TransferCredited e -> advance(e.transferId(), "CREDITED", null, e.occurredAt());
            case TransferDebitReversed e -> advance(e.transferId(), "REVERSED", e.reason(), e.occurredAt());
            case TransferCompleted e -> advance(e.aggregateId(), "COMPLETED", null, e.occurredAt());
            case TransferFailed e -> advance(e.aggregateId(), "FAILED", e.reason(), e.occurredAt());
            default -> {
            }
        }
    }

    @Override
    public void reset() {
        store.clear();
    }

    private void advance(String transferId, String status, String reason, Instant at) {
        store.find(transferId)
                .filter(current -> STAGE.get(status) > STAGE.get(current.status()))
                .ifPresent(current -> store.save(current.advance(status, reason, at)));
    }
}
