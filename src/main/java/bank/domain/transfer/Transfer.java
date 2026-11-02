package bank.domain.transfer;

import bank.domain.shared.AggregateRoot;
import bank.domain.shared.DomainException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class Transfer extends AggregateRoot<TransferEvent> {
    private String id;
    private String sourceAccountId;
    private String targetAccountId;
    private BigDecimal amount;
    private Instant expiresAt;
    private TransferStatus status;
    private String failureReason;

    private Transfer() {
    }

    public static Transfer request(String id, String sourceAccountId, String targetAccountId, BigDecimal amount,
                                   Duration timeout) {
        if (sourceAccountId.equals(targetAccountId)) {
            throw new DomainException("Conta de origem e destino devem ser diferentes");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException("Valor deve ser positivo");
        }
        Instant now = Instant.now();
        Transfer transfer = new Transfer();
        transfer.raise(new TransferRequested(id, sourceAccountId, targetAccountId, amount, now.plus(timeout), now));
        return transfer;
    }

    public static Transfer restore(List<TransferEvent> history) {
        Transfer transfer = new Transfer();
        transfer.replay(history);
        return transfer;
    }

    public boolean startCredit() {
        if (status == TransferStatus.CREDITING) {
            return true;
        }
        if (status != TransferStatus.REQUESTED) {
            return false;
        }
        raise(new TransferCreditStarted(id, Instant.now()));
        return true;
    }

    public void complete() {
        if (status == TransferStatus.COMPLETED) {
            return;
        }
        if (status != TransferStatus.CREDITING) {
            throw new DomainException("Transferencia " + id + " nao pode ser concluida no status " + status);
        }
        raise(new TransferCompleted(id, Instant.now()));
    }

    public void fail(String reason) {
        if (status == TransferStatus.FAILED) {
            return;
        }
        if (status == TransferStatus.COMPLETED) {
            throw new DomainException("Transferencia " + id + " ja foi concluida");
        }
        raise(new TransferFailed(id, reason, Instant.now()));
    }

    public void expire(Instant now) {
        if (status != TransferStatus.REQUESTED || expiresAt == null || now.isBefore(expiresAt)) {
            return;
        }
        raise(new TransferFailed(id, "Tempo esgotado: transferencia nao concluida ate " + expiresAt, now));
    }

    public boolean isPending() {
        return status == TransferStatus.REQUESTED || status == TransferStatus.CREDITING;
    }

    public String id() {
        return id;
    }

    public String sourceAccountId() {
        return sourceAccountId;
    }

    public String targetAccountId() {
        return targetAccountId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public TransferStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    @Override
    protected void apply(TransferEvent event) {
        switch (event) {
            case TransferRequested e -> {
                id = e.aggregateId();
                sourceAccountId = e.sourceAccountId();
                targetAccountId = e.targetAccountId();
                amount = e.amount();
                expiresAt = e.expiresAt();
                status = TransferStatus.REQUESTED;
            }
            case TransferCreditStarted e -> status = TransferStatus.CREDITING;
            case TransferCompleted e -> status = TransferStatus.COMPLETED;
            case TransferFailed e -> {
                status = TransferStatus.FAILED;
                failureReason = e.reason();
            }
        }
    }
}
