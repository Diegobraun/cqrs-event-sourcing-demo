package bank.domain.shared;

import java.time.Instant;

public interface DomainEvent {
    String aggregateId();
    Instant occurredAt();
}
