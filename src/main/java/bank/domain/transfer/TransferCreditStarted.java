package bank.domain.transfer;

import java.time.Instant;

public record TransferCreditStarted(String aggregateId, Instant occurredAt) implements TransferEvent {
}
