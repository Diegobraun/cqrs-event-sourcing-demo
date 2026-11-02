package bank.domain.account;

import java.time.Instant;

public record AccountOpened(String aggregateId, String owner, Instant occurredAt) implements AccountEvent {
}
