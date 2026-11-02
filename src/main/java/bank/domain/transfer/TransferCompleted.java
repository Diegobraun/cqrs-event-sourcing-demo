package bank.domain.transfer;

import java.time.Instant;

public record TransferCompleted(String aggregateId, Instant occurredAt) implements TransferEvent {
}
