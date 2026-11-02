package bank.application.eventstore;

import bank.domain.shared.DomainEvent;

import java.util.List;

public interface EventStore {
    void append(String streamId, long expectedVersion, List<? extends DomainEvent> events);

    List<EventEnvelope> readStream(String streamId, long afterVersion);

    List<EventEnvelope> readAll(long afterPosition, int limit);

    long lastPosition();
}
