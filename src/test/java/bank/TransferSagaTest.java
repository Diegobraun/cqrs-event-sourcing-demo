package bank;

import bank.application.command.AccountCommandHandler;
import bank.application.command.AccountRepository;
import bank.application.command.CloseAccount;
import bank.application.command.DebitForTransfer;
import bank.application.command.Deposit;
import bank.application.command.OpenAccount;
import bank.application.eventstore.AccountStream;
import bank.application.eventstore.EventEnvelope;
import bank.application.eventstore.EventStore;
import bank.application.eventstore.TransferStream;
import bank.application.query.AccountQueryService;
import bank.application.query.StatementLine;
import bank.application.query.TransferQueryService;
import bank.application.query.TransferView;
import bank.application.transfer.RequestTransfer;
import bank.application.transfer.TransferCommandHandler;
import bank.application.transfer.TransferRepository;
import bank.application.transfer.TransferSaga;
import bank.domain.shared.DomainException;
import bank.domain.transfer.Transfer;
import bank.domain.transfer.TransferStatus;
import bank.infrastructure.projection.ProjectionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferSagaTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private AccountCommandHandler accounts;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferCommandHandler transfers;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransferSaga saga;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ProjectionRunner subscribers;

    @Autowired
    private AccountQueryService accountQueries;

    @Autowired
    private TransferQueryService transferQueries;

    @Test
    void successfulTransferMovesMoneyBetweenAccounts() {
        openWithBalance("T-A", "1000");
        openWithBalance("T-B", "0");

        transfers.request(new RequestTransfer("TR-1", "T-A", "T-B", money("300")));

        Transfer transfer = awaitFinished("TR-1");
        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(balanceOf("T-A")).isEqualByComparingTo("700");
        assertThat(balanceOf("T-B")).isEqualByComparingTo("300");

        assertThat(transferQueries.find("TR-1").status()).isEqualTo("COMPLETED");
        assertThat(accountQueries.summary("T-A").orElseThrow().balance()).isEqualByComparingTo("700");
        assertThat(accountQueries.summary("T-B").orElseThrow().balance()).isEqualByComparingTo("300");
    }

    @Test
    void insufficientFundsFailsWithoutMovingMoney() {
        openWithBalance("T-C", "100");
        openWithBalance("T-D", "0");

        transfers.request(new RequestTransfer("TR-2", "T-C", "T-D", money("500")));

        Transfer transfer = awaitFinished("TR-2");
        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.failureReason()).contains("Debito recusado", "Saldo insuficiente");
        assertThat(balanceOf("T-C")).isEqualByComparingTo("100");
        assertThat(eventTypes(AccountStream.id("T-C"))).doesNotContain("TransferDebited");
    }

    @Test
    void rejectedCreditIsCompensatedByReversingTheDebit() {
        openWithBalance("T-E", "1000");
        accounts.handle(new OpenAccount("T-F", "Destino fechado"));
        accounts.handle(new CloseAccount("T-F"));

        transfers.request(new RequestTransfer("TR-3", "T-E", "T-F", money("400")));

        Transfer transfer = awaitFinished("TR-3");
        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.failureReason()).contains("Credito recusado");
        assertThat(balanceOf("T-E")).isEqualByComparingTo("1000");
        assertThat(eventTypes(AccountStream.id("T-E")))
                .containsSubsequence("TransferDebited", "TransferDebitReversed");

        TransferView view = transferQueries.find("TR-3");
        assertThat(view.status()).isEqualTo("FAILED");
        assertThat(view.refunded()).isTrue();
        List<StatementLine> statement = accountQueries.statement("T-E");
        assertThat(statement.getLast().description()).startsWith("Estorno");
        assertThat(statement.getLast().balanceAfter()).isEqualByComparingTo("1000");
    }

    @Test
    void redeliveredEventsAndRepeatedCommandsAreIdempotent() {
        openWithBalance("T-G", "500");
        openWithBalance("T-H", "0");
        transfers.request(new RequestTransfer("TR-4", "T-G", "T-H", money("200")));
        awaitFinished("TR-4");
        int sourceEvents = eventTypes(AccountStream.id("T-G")).size();
        int targetEvents = eventTypes(AccountStream.id("T-H")).size();

        eventStore.readStream(TransferStream.id("TR-4"), 0).forEach(saga::handle);
        eventStore.readStream(AccountStream.id("T-G"), 0).forEach(saga::handle);
        accounts.handle(new DebitForTransfer("T-G", "TR-4", money("200")));

        assertThat(eventTypes(AccountStream.id("T-G"))).hasSize(sourceEvents);
        assertThat(eventTypes(AccountStream.id("T-H"))).hasSize(targetEvents);
        assertThat(balanceOf("T-G")).isEqualByComparingTo("300");
        assertThat(balanceOf("T-H")).isEqualByComparingTo("200");
    }

    @Test
    void transferToSameAccountIsRejectedUpFront() {
        assertThatThrownBy(() -> transfers.request(new RequestTransfer("TR-5", "T-A", "T-A", money("10"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("diferentes");
        assertThat(transferRepository.load("TR-5")).isEmpty();
    }

    private void openWithBalance(String accountId, String balance) {
        accounts.handle(new OpenAccount(accountId, "Titular " + accountId));
        if (new BigDecimal(balance).signum() > 0) {
            accounts.handle(new Deposit(accountId, money(balance)));
        }
    }

    private Transfer awaitFinished(String transferId) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Transfer transfer = transferRepository.load(transferId).orElseThrow();
            if (!transfer.isPending()) {
                subscribers.awaitCatchUp(TIMEOUT);
                return transfer;
            }
            sleep();
        }
        throw new AssertionError("Transferencia " + transferId + " nao terminou em " + TIMEOUT);
    }

    private BigDecimal balanceOf(String accountId) {
        return accountRepository.load(accountId).orElseThrow().balance();
    }

    private List<String> eventTypes(String streamId) {
        return eventStore.readStream(streamId, 0).stream()
                .map(EventEnvelope::event)
                .map(event -> event.getClass().getSimpleName())
                .toList();
    }

    private static void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
