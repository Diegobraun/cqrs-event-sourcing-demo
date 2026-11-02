package bank.application.query.projection;

import bank.application.eventstore.EventEnvelope;
import bank.application.query.StatementLine;
import bank.application.query.StatementStore;
import bank.domain.account.AccountClosed;
import bank.domain.account.AccountOpened;
import bank.domain.account.MoneyDeposited;
import bank.domain.account.MoneyWithdrawn;
import bank.domain.account.TransferCredited;
import bank.domain.account.TransferDebitReversed;
import bank.domain.account.TransferDebited;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StatementProjection implements Projection {
    private final StatementStore store;

    public StatementProjection(StatementStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "statement";
    }

    @Override
    public void handle(EventEnvelope envelope) {
        switch (envelope.event()) {
            case AccountOpened e -> addLine(e.aggregateId(), envelope.version(), e.occurredAt(), "Abertura", BigDecimal.ZERO);
            case MoneyDeposited e -> addLine(e.aggregateId(), envelope.version(), e.occurredAt(), "Deposito", e.amount());
            case MoneyWithdrawn e -> addLine(e.aggregateId(), envelope.version(), e.occurredAt(), "Saque", e.amount().negate());
            case AccountClosed e -> addLine(e.aggregateId(), envelope.version(), e.occurredAt(), "Encerramento", BigDecimal.ZERO);
            case TransferDebited e -> addLine(e.aggregateId(), envelope.version(), e.occurredAt(),
                    "Transferencia enviada " + e.transferId(), e.amount().negate());
            case TransferCredited e -> addLine(e.aggregateId(), envelope.version(), e.occurredAt(),
                    "Transferencia recebida " + e.transferId(), e.amount());
            case TransferDebitReversed e -> addLine(e.aggregateId(), envelope.version(), e.occurredAt(),
                    "Estorno " + e.transferId(), e.amount());
            default -> {
            }
        }
    }

    @Override
    public void reset() {
        store.clear();
    }

    private void addLine(String accountId, long version, Instant when, String description, BigDecimal amount) {
        List<StatementLine> lines = store.find(accountId);
        if (!lines.isEmpty() && lines.getLast().version() >= version) {
            return;
        }
        BigDecimal previous = lines.isEmpty() ? BigDecimal.ZERO : lines.getLast().balanceAfter();
        store.append(accountId, new StatementLine(version, when, description, amount, previous.add(amount)));
    }
}
