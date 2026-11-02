package bank.application.query.projection;

public interface CheckpointStore {
    Checkpoint load(String projectionName);

    void save(String projectionName, Checkpoint checkpoint);
}
