package bank.application.command;

import bank.domain.account.Account;

import java.util.function.Consumer;

public final class AccountCommandHandler {
    private final AccountRepository repository;

    public AccountCommandHandler(AccountRepository repository) {
        this.repository = repository;
    }

    public CommandResult handle(Command command) {
        return switch (command) {
            case OpenAccount c -> {
                if (repository.load(c.accountId()).isPresent()) {
                    throw new AccountAlreadyExistsException(c.accountId());
                }
                yield save(Account.open(c.accountId(), c.owner()));
            }
            case Deposit c -> execute(c.accountId(), account -> account.deposit(c.amount()));
            case Withdraw c -> execute(c.accountId(), account -> account.withdraw(c.amount()));
            case CloseAccount c -> execute(c.accountId(), Account::close);
            case DebitForTransfer c -> execute(c.accountId(),
                    account -> account.debitForTransfer(c.transferId(), c.amount()));
            case CreditForTransfer c -> execute(c.accountId(),
                    account -> account.creditForTransfer(c.transferId(), c.amount()));
            case ReverseTransferDebit c -> execute(c.accountId(),
                    account -> account.reverseTransferDebit(c.transferId(), c.amount(), c.reason()));
        };
    }

    private CommandResult execute(String accountId, Consumer<Account> action) {
        Account account = repository.load(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        action.accept(account);
        return save(account);
    }

    private CommandResult save(Account account) {
        repository.save(account);
        return new CommandResult(account.id(), account.version());
    }
}
