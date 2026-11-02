package bank.application.query;

import java.util.Optional;

public interface TransferViewStore {
    void save(TransferView view);

    Optional<TransferView> find(String transferId);

    void clear();
}
