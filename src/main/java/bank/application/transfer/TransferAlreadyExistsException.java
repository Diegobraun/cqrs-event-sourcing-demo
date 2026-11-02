package bank.application.transfer;

public class TransferAlreadyExistsException extends RuntimeException {
    public TransferAlreadyExistsException(String transferId) {
        super("Transferencia " + transferId + " ja existe");
    }
}
