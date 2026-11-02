package bank.domain.account;

import bank.domain.shared.DomainEvent;

public sealed interface AccountEvent extends DomainEvent
        permits AccountOpened, MoneyDeposited, MoneyWithdrawn, AccountClosed,
        TransferDebited, TransferCredited, TransferDebitReversed {
}
