package bank.application.query.projection;

public record Checkpoint(long position, String streamCursor) {

    public static Checkpoint initial() {
        return new Checkpoint(0, null);
    }
}
