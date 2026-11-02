package bank.application.transfer;

import bank.application.command.AccountCommandHandler;
import bank.application.command.CommandRejectedException;
import bank.application.command.CreditForTransfer;
import bank.application.command.DebitForTransfer;
import bank.application.command.ReverseTransferDebit;
import bank.application.eventstore.EventEnvelope;
import bank.application.eventstore.EventSubscriber;
import bank.domain.account.TransferCredited;
import bank.domain.account.TransferDebitReversed;
import bank.domain.account.TransferDebited;
import bank.domain.shared.DomainException;
import bank.domain.transfer.Transfer;
import bank.domain.transfer.TransferRequested;

import java.util.Optional;

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
            case TransferDebited e -> creditTarget(e.transferId());
            case TransferCredited e -> transferCommands.complete(e.transferId());
            case TransferDebitReversed e -> transferCommands.fail(e.transferId(), e.reason());
            default -> {
            }
        }
    }

    private void debitSource(String transferId) {
        pendingTransfer(transferId).ifPresent(transfer -> {
            try {
                accountCommands.handle(new DebitForTransfer(transfer.sourceAccountId(), transferId, transfer.amount()));
            } catch (DomainException | CommandRejectedException e) {
                transferCommands.fail(transferId, "Debito recusado: " + e.getMessage());
            }
        });
    }

    private void creditTarget(String transferId) {
        pendingTransfer(transferId).ifPresent(transfer -> {
            try {
                accountCommands.handle(new CreditForTransfer(transfer.targetAccountId(), transferId, transfer.amount()));
            } catch (DomainException | CommandRejectedException e) {
                accountCommands.handle(new ReverseTransferDebit(transfer.sourceAccountId(), transferId,
                        transfer.amount(), "Credito recusado: " + e.getMessage()));
            }
        });
    }

    private Optional<Transfer> pendingTransfer(String transferId) {
        return transfers.load(transferId).filter(Transfer::isPending);
    }
}
