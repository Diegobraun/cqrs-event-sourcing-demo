package bank.application.query.projection;

public record ProjectionStatus(String name, long checkpoint, long lag, boolean running) {
}
