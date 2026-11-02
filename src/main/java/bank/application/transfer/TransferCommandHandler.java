package bank.application.transfer;

import bank.domain.transfer.Transfer;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public final class TransferCommandHandler {
    private final TransferRepository repository;
    private final Duration timeout;

    public TransferCommandHandler(TransferRepository repository, Duration timeout) {
        this.repository = repository;
        this.timeout = timeout;
    }

    public TransferResult request(RequestTransfer command) {
        if (repository.load(command.transferId()).isPresent()) {
            throw new TransferAlreadyExistsException(command.transferId());
        }
        Transfer transfer = Transfer.request(command.transferId(), command.sourceAccountId(),
                command.targetAccountId(), command.amount(), timeout);
        repository.save(transfer);
        return new TransferResult(transfer.id(), transfer.version());
    }

    public boolean startCredit(String transferId) {
        Transfer transfer = load(transferId);
        boolean crediting = transfer.startCredit();
        repository.save(transfer);
        return crediting;
    }

    public void complete(String transferId) {
        execute(transferId, Transfer::complete);
    }

    public void fail(String transferId, String reason) {
        execute(transferId, transfer -> transfer.fail(reason));
    }

    public boolean expire(String transferId, Instant now) {
        Transfer transfer = load(transferId);
        transfer.expire(now);
        boolean expired = transfer.hasPendingChanges();
        repository.save(transfer);
        return expired;
    }

    private void execute(String transferId, Consumer<Transfer> action) {
        Transfer transfer = load(transferId);
        action.accept(transfer);
        repository.save(transfer);
    }

    private Transfer load(String transferId) {
        return repository.load(transferId).orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}
