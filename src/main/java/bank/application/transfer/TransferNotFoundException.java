package bank.application.transfer;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(String transferId) {
        super("Transferencia " + transferId + " nao encontrada");
    }
}
