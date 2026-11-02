package bank.domain.account;

import bank.domain.shared.AggregateRoot;
import bank.domain.shared.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Account extends AggregateRoot<AccountEvent> {
    private static final String DEBIT = "debit";
    private static final String CREDIT = "credit";
    private static final String REVERSAL = "reversal";

    private final Set<String> transferOperations = new HashSet<>();
    private String id;
    private String owner;
    private BigDecimal balance = BigDecimal.ZERO;
    private AccountStatus status;

    private Account() {
    }

    public static Account open(String id, String owner) {
        if (owner == null || owner.isBlank()) {
            throw new DomainException("Titular obrigatorio");
        }
        Account account = new Account();
        account.raise(new AccountOpened(id, owner, Instant.now()));
        return account;
    }

    public static Account restore(AccountSnapshot snapshot, List<AccountEvent> history) {
        Account account = new Account();
        if (snapshot != null) {
            account.id = snapshot.accountId();
            account.owner = snapshot.owner();
            account.balance = snapshot.balance();
            account.status = snapshot.status();
            account.transferOperations.addAll(snapshot.transferOperations());
            account.restoreVersion(snapshot.version());
        }
        account.replay(history);
        return account;
    }

    public void deposit(BigDecimal amount) {
        requireOpen();
        requirePositive(amount);
        raise(new MoneyDeposited(id, amount, Instant.now()));
    }

    public void withdraw(BigDecimal amount) {
        requireOpen();
        requirePositive(amount);
        requireBalance(amount);
        raise(new MoneyWithdrawn(id, amount, Instant.now()));
    }

    public void close() {
        requireOpen();
        if (balance.signum() != 0) {
            throw new DomainException("Conta so pode ser encerrada com saldo zero, saldo atual " + balance);
        }
        raise(new AccountClosed(id, Instant.now()));
    }

    public void debitForTransfer(String transferId, BigDecimal amount) {
        if (alreadyApplied(DEBIT, transferId)) {
            return;
        }
        requireOpen();
        requirePositive(amount);
        requireBalance(amount);
        raise(new TransferDebited(id, transferId, amount, Instant.now()));
    }

    public void creditForTransfer(String transferId, BigDecimal amount) {
        if (alreadyApplied(CREDIT, transferId)) {
            return;
        }
        requireOpen();
        requirePositive(amount);
        raise(new TransferCredited(id, transferId, amount, Instant.now()));
    }

    public void reverseTransferDebit(String transferId, BigDecimal amount, String reason) {
        if (!alreadyApplied(DEBIT, transferId) || alreadyApplied(REVERSAL, transferId)) {
            return;
        }
        raise(new TransferDebitReversed(id, transferId, amount, reason, Instant.now()));
    }

    public AccountSnapshot snapshot() {
        if (hasPendingChanges()) {
            throw new IllegalStateException("Snapshot exige aggregate sem mudancas pendentes");
        }
        return new AccountSnapshot(id, owner, balance, status, Set.copyOf(transferOperations), version());
    }

    public String id() {
        return id;
    }

    public BigDecimal balance() {
        return balance;
    }

    public AccountStatus status() {
        return status;
    }

    @Override
    protected void apply(AccountEvent event) {
        switch (event) {
            case AccountOpened e -> {
                id = e.aggregateId();
                owner = e.owner();
                status = AccountStatus.OPEN;
            }
            case MoneyDeposited e -> balance = balance.add(e.amount());
            case MoneyWithdrawn e -> balance = balance.subtract(e.amount());
            case AccountClosed e -> status = AccountStatus.CLOSED;
            case TransferDebited e -> {
                balance = balance.subtract(e.amount());
                transferOperations.add(operationKey(DEBIT, e.transferId()));
            }
            case TransferCredited e -> {
                balance = balance.add(e.amount());
                transferOperations.add(operationKey(CREDIT, e.transferId()));
            }
            case TransferDebitReversed e -> {
                balance = balance.add(e.amount());
                transferOperations.add(operationKey(REVERSAL, e.transferId()));
            }
        }
    }

    private boolean alreadyApplied(String operation, String transferId) {
        return transferOperations.contains(operationKey(operation, transferId));
    }

    private static String operationKey(String operation, String transferId) {
        return operation + ":" + transferId;
    }

    private void requireOpen() {
        if (status != AccountStatus.OPEN) {
            throw new DomainException("Conta " + id + " nao esta aberta");
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException("Valor deve ser positivo");
        }
    }

    private void requireBalance(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new DomainException("Saldo insuficiente: saldo " + balance + ", valor " + amount);
        }
    }
}
