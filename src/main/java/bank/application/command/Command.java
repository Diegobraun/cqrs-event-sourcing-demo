package bank.application.command;

public sealed interface Command permits OpenAccount, Deposit, Withdraw, CloseAccount,
        DebitForTransfer, CreditForTransfer, ReverseTransferDebit {
    String accountId();
}
