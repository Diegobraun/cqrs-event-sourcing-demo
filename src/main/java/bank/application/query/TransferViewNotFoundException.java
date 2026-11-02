package bank.application.query;

public class TransferViewNotFoundException extends RuntimeException {
    public TransferViewNotFoundException(String transferId) {
        super("Transferencia " + transferId + " nao encontrada");
    }
}
