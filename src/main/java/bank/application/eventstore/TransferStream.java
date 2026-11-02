package bank.application.eventstore;

public final class TransferStream {
    private TransferStream() {
    }

    public static String id(String transferId) {
        return "transfer-" + transferId;
    }
}
