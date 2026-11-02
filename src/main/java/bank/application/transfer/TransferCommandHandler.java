package bank.application.transfer;

import bank.domain.transfer.Transfer;

import java.util.function.Consumer;

public final class TransferCommandHandler {
    private final TransferRepository repository;

    public TransferCommandHandler(TransferRepository repository) {
        this.repository = repository;
    }

    public TransferResult request(RequestTransfer command) {
        if (repository.load(command.transferId()).isPresent()) {
            throw new TransferAlreadyExistsException(command.transferId());
        }
        Transfer transfer = Transfer.request(command.transferId(), command.sourceAccountId(),
                command.targetAccountId(), command.amount());
        repository.save(transfer);
        return new TransferResult(transfer.id(), transfer.version());
    }

    public void complete(String transferId) {
        execute(transferId, Transfer::complete);
    }

    public void fail(String transferId, String reason) {
        execute(transferId, transfer -> transfer.fail(reason));
    }

    private void execute(String transferId, Consumer<Transfer> action) {
        Transfer transfer = repository.load(transferId).orElseThrow(() -> new TransferNotFoundException(transferId));
        action.accept(transfer);
        repository.save(transfer);
    }
}
