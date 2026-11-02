package bank.application.command;

public class CommandRejectedException extends RuntimeException {
    public CommandRejectedException(String message) {
        super(message);
    }
}
