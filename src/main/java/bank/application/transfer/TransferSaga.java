package bank.application.transfer;

import bank.application.command.AccountCommandHandler;
import bank.application.command.CommandRejectedException;
import bank.application.command.CreditForTransfer;
import bank.application.command.DebitForTransfer;
import bank.application.command.ReverseTransferDebit;
import bank.application.eventstore.EventEnvelope;
import bank.application.eventstore.EventSubscriber;
import bank.domain.account.TransferCredited;
import bank.domain.account.TransferDebited;
import bank.domain.shared.DomainException;
import bank.domain.transfer.Transfer;
import bank.domain.transfer.TransferFailed;
import bank.domain.transfer.TransferRequested;
import bank.domain.transfer.TransferStatus;

public final class TransferSaga implements EventSubscriber {
    private final TransferRepository transfers;
    private final TransferCommandHandler transferCommands;
    private final AccountCommandHandler accountCommands;

    public TransferSaga(TransferRepository transfers, TransferCommandHandler transferCommands,
                        AccountCommandHandler accountCommands) {
        this.transfers = transfers;
        this.transferCommands = transferCommands;
        this.accountCommands = accountCommands;
    }

    @Override
    public String name() {
        return "transfer-saga";
    }

    @Override
    public void handle(EventEnvelope envelope) {
        switch (envelope.event()) {
            case TransferRequested e -> debitSource(e.aggregateId());
            case TransferDebited e -> afterDebit(e.transferId());
            case TransferCredited e -> transferCommands.complete(e.transferId());
            case TransferFailed e -> refundSource(e.aggregateId(), e.reason());
            default -> {
            }
        }
    }

    private void debitSource(String transferId) {
        Transfer transfer = load(transferId);
        if (transfer.status() != TransferStatus.REQUESTED) {
            return;
        }
        try {
            accountCommands.handle(new DebitForTransfer(transfer.sourceAccountId(), transferId, transfer.amount()));
        } catch (DomainException | CommandRejectedException e) {
            transferCommands.fail(transferId, "Debito recusado: " + e.getMessage());
        }
    }

    private void afterDebit(String transferId) {
        Transfer transfer = load(transferId);
        switch (transfer.status()) {
            case REQUESTED, CREDITING -> creditTarget(transfer);
            case FAILED -> refundSource(transferId, transfer.failureReason());
            case COMPLETED -> {
            }
        }
    }

    private void creditTarget(Transfer transfer) {
        if (!transferCommands.startCredit(transfer.id())) {
            return;
        }
        try {
            accountCommands.handle(new CreditForTransfer(transfer.targetAccountId(), transfer.id(), transfer.amount()));
        } catch (DomainException | CommandRejectedException e) {
            transferCommands.fail(transfer.id(), "Credito recusado: " + e.getMessage());
        }
    }

    private void refundSource(String transferId, String reason) {
        Transfer transfer = load(transferId);
        try {
            accountCommands.handle(new ReverseTransferDebit(transfer.sourceAccountId(), transferId,
                    transfer.amount(), reason));
        } catch (CommandRejectedException e) {
            return;
        }
    }

    private Transfer load(String transferId) {
        return transfers.load(transferId).orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}
