package bank;

import bank.application.command.AccountCommandHandler;
import bank.application.command.AccountRepository;
import bank.application.command.CloseAccount;
import bank.application.command.Deposit;
import bank.application.command.OpenAccount;
import bank.application.command.Withdraw;
import bank.application.eventstore.AccountStream;
import bank.application.eventstore.ConcurrencyException;
import bank.application.eventstore.EventStore;
import bank.application.query.AccountQueryService;
import bank.application.query.StatementLine;
import bank.domain.account.Account;
import bank.domain.account.AccountStatus;
import bank.domain.shared.DomainException;
import bank.infrastructure.persistence.SnapshotStore;
import bank.infrastructure.projection.ProjectionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventSourcingConceptsTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Autowired
    private AccountCommandHandler commands;

    @Autowired
    private AccountRepository repository;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private SnapshotStore snapshotStore;

    @Autowired
    private ProjectionRunner projections;

    @Autowired
    private AccountQueryService queries;

    @Test
    void rejectedCommandDoesNotProduceEvent() {
        commands.handle(new OpenAccount("C-1", "Diego"));
        commands.handle(new Deposit("C-1", money("100")));

        assertThatThrownBy(() -> commands.handle(new Withdraw("C-1", money("500"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Saldo insuficiente");

        assertThat(eventStore.readStream(AccountStream.id("C-1"), 0)).hasSize(2);
    }

    @Test
    void closedAccountRejectsNewMovements() {
        commands.handle(new OpenAccount("C-2", "Joao"));
        commands.handle(new CloseAccount("C-2"));

        assertThatThrownBy(() -> commands.handle(new Deposit("C-2", money("10"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("nao esta aberta");
    }

    @Test
    void readSideIsEventuallyConsistent() {
        commands.handle(new OpenAccount("C-3", "Maria"));
        projections.awaitCatchUp(TIMEOUT);

        projections.stop();
        try {
            commands.handle(new Deposit("C-3", money("200")));

            assertThat(repository.load("C-3").orElseThrow().balance()).isEqualByComparingTo("200");
            assertThat(queries.summary("C-3").orElseThrow().balance()).isEqualByComparingTo("0");
        } finally {
            projections.start();
        }

        projections.awaitCatchUp(TIMEOUT);
        assertThat(queries.summary("C-3").orElseThrow().balance()).isEqualByComparingTo("200");
    }

    @Test
    void snapshotAvoidsReplayingWholeHistory() {
        commands.handle(new OpenAccount("C-4", "Ana"));
        commands.handle(new Deposit("C-4", money("1000")));
        commands.handle(new Withdraw("C-4", money("300")));
        commands.handle(new Deposit("C-4", money("50")));
        commands.handle(new Deposit("C-4", money("200")));

        long snapshotVersion = snapshotStore.find("C-4").orElseThrow().version();

        assertThat(snapshotVersion).isEqualTo(3);
        assertThat(eventStore.readStream(AccountStream.id("C-4"), snapshotVersion)).hasSize(2);
        assertThat(repository.load("C-4").orElseThrow().balance()).isEqualByComparingTo("950");
    }

    @Test
    void timeTravelRebuildsAnyPastVersion() {
        commands.handle(new OpenAccount("C-5", "Bia"));
        commands.handle(new Deposit("C-5", money("1000")));
        commands.handle(new Withdraw("C-5", money("1000")));
        commands.handle(new CloseAccount("C-5"));

        Account atVersion2 = repository.loadAtVersion("C-5", 2).orElseThrow();
        Account atVersion4 = repository.loadAtVersion("C-5", 4).orElseThrow();

        assertThat(atVersion2.balance()).isEqualByComparingTo("1000");
        assertThat(atVersion2.status()).isEqualTo(AccountStatus.OPEN);
        assertThat(atVersion4.balance()).isEqualByComparingTo("0");
        assertThat(atVersion4.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void rebuildReplaysHistoryAndProducesSameReadModel() {
        commands.handle(new OpenAccount("C-6", "Caio"));
        commands.handle(new Deposit("C-6", money("500")));
        commands.handle(new Withdraw("C-6", money("120")));
        projections.awaitCatchUp(TIMEOUT);
        List<StatementLine> before = queries.statement("C-6");

        projections.rebuild("statement");
        projections.awaitCatchUp(TIMEOUT);

        assertThat(queries.statement("C-6")).isEqualTo(before);
    }

    @Test
    void optimisticConcurrencyRejectsStaleWrite() {
        commands.handle(new OpenAccount("C-7", "Davi"));
        commands.handle(new Deposit("C-7", money("2500")));

        Account seenByA = repository.load("C-7").orElseThrow();
        Account seenByB = repository.load("C-7").orElseThrow();
        seenByA.withdraw(money("2000"));
        seenByB.withdraw(money("2000"));

        repository.save(seenByA);

        assertThatThrownBy(() -> repository.save(seenByB))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("esperava versao 2, atual 3");
        assertThat(repository.load("C-7").orElseThrow().balance()).isEqualByComparingTo("500");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
