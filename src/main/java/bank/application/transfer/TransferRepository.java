package bank.application.transfer;

import bank.domain.transfer.Transfer;

import java.util.Optional;

public interface TransferRepository {
    Optional<Transfer> load(String transferId);

    void save(Transfer transfer);
}
