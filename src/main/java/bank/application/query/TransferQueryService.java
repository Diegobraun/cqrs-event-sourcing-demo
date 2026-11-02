package bank.application.query;

public final class TransferQueryService {
    private final TransferViewStore store;

    public TransferQueryService(TransferViewStore store) {
        this.store = store;
    }

    public TransferView find(String transferId) {
        return store.find(transferId).orElseThrow(() -> new TransferViewNotFoundException(transferId));
    }
}
