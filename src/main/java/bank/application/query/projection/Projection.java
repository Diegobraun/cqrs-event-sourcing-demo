package bank.application.query.projection;

import bank.application.eventstore.EventSubscriber;

public interface Projection extends EventSubscriber {
    void reset();
}
