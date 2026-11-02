package bank.application.query;

import bank.domain.shared.DomainEvent;

import java.time.Instant;

public record AuditEntry(long position, long version, String type, Instant recordedAt, DomainEvent data) {
}
