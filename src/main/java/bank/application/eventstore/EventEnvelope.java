package bank.application.eventstore;

import bank.domain.shared.DomainEvent;

import java.time.Instant;

public record EventEnvelope(long position, String streamId, long version, Instant recordedAt, DomainEvent event) {
}
