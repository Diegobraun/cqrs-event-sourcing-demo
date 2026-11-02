package bank.application.eventstore;

public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(String streamId, long expectedVersion, long actualVersion) {
        super("Conflito em " + streamId + ": esperava versao " + expectedVersion + ", atual " + actualVersion);
    }
}
