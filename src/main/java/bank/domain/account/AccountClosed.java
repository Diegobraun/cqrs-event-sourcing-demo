package bank.domain.account;

import java.time.Instant;

public record AccountClosed(String aggregateId, Instant occurredAt) implements AccountEvent {
}
