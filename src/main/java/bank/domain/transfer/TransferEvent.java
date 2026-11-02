package bank.domain.transfer;

import bank.domain.shared.DomainEvent;

public sealed interface TransferEvent extends DomainEvent
        permits TransferRequested, TransferCreditStarted, TransferCompleted, TransferFailed {
}
