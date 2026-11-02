package bank.domain.transfer;

import java.time.Instant;

public record TransferFailed(String aggregateId, String reason, Instant occurredAt) implements TransferEvent {
}
