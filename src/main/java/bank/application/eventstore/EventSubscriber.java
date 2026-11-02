package bank.application.eventstore;

public interface EventSubscriber {
    String name();

    void handle(EventEnvelope envelope);
}
