package bank.domain.shared;

import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot<E extends DomainEvent> {
    private final List<E> pendingChanges = new ArrayList<>();
    private long version;

    protected abstract void apply(E event);

    protected void raise(E event) {
        apply(event);
        pendingChanges.add(event);
    }

    protected void replay(List<? extends E> history) {
        for (E event : history) {
            apply(event);
            version++;
        }
    }

    protected void restoreVersion(long version) {
        this.version = version;
    }

    public List<E> pendingChanges() {
        return List.copyOf(pendingChanges);
    }

    public boolean hasPendingChanges() {
        return !pendingChanges.isEmpty();
    }

    public void markChangesAsCommitted() {
        version += pendingChanges.size();
        pendingChanges.clear();
    }

    public long version() {
        return version;
    }
}
