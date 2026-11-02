package bank;

import bank.application.command.AccountCommandHandler;
import bank.application.command.AccountRepository;
import bank.application.command.DebitForTransfer;
import bank.application.command.Deposit;
import bank.application.command.OpenAccount;
import bank.application.eventstore.AccountStream;
import bank.application.eventstore.EventEnvelope;
import bank.application.eventstore.EventStore;
import bank.application.query.TransferQueryService;
import bank.application.query.TransferView;
import bank.application.transfer.RequestTransfer;
import bank.application.transfer.TransferCommandHandler;
import bank.application.transfer.TransferRepository;
import bank.application.transfer.TransferTimeoutService;
import bank.domain.transfer.Transfer;
import bank.domain.transfer.TransferStatus;
import bank.infrastructure.projection.ProjectionRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferTimeoutTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String SAGA = "transfer-saga";

    @Autowired
    private AccountCommandHandler accounts;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferCommandHandler transfers;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransferTimeoutService timeouts;

    @Autowired
    private TransferQueryService transferQueries;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ProjectionRunner subscribers;

    @BeforeEach
    void pauseSaga() {
        subscribers.pause(SAGA);
    }

    @AfterEach
    void resumeSaga() {
        subscribers.resume(SAGA);
        subscribers.awaitCatchUp(TIMEOUT);
    }

    @Test
    void transferNotYetDueIsLeftAlone() {
        openWithBalance("TO-A", "100");
        openWithBalance("TO-B", "0");
        transfers.request(new RequestTransfer("TT-1", "TO-A", "TO-B", money("10")));
        subscribers.awaitCatchUp(TIMEOUT);

        timeouts.expireOverdue(Instant.now());

        assertThat(transferRepository.load("TT-1").orElseThrow().status()).isEqualTo(TransferStatus.REQUESTED);
    }

    @Test
    void stuckTransferBeforeDebitFailsAndMovesNoMoney() {
        openWithBalance("TO-C", "100");
        openWithBalance("TO-D", "0");
        transfers.request(new RequestTransfer("TT-2", "TO-C", "TO-D", money("40")));
        subscribers.awaitCatchUp(TIMEOUT);

        int expired = timeouts.expireOverdue(afterDeadline());

        assertThat(expired).isGreaterThanOrEqualTo(1);
        Transfer transfer = transferRepository.load("TT-2").orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.failureReason()).startsWith("Tempo esgotado");

        subscribers.resume(SAGA);
        subscribers.awaitCatchUp(TIMEOUT);

        assertThat(balanceOf("TO-C")).isEqualByComparingTo("100");
        assertThat(balanceOf("TO-D")).isEqualByComparingTo("0");
        assertThat(eventTypes(AccountStream.id("TO-C"))).doesNotContain("TransferDebited");
        TransferView view = transferQueries.find("TT-2");
        assertThat(view.status()).isEqualTo("FAILED");
        assertThat(view.refunded()).isFalse();
    }

    @Test
    void stuckTransferAfterDebitIsRefunded() {
        openWithBalance("TO-E", "100");
        openWithBalance("TO-F", "0");
        transfers.request(new RequestTransfer("TT-3", "TO-E", "TO-F", money("70")));
        accounts.handle(new DebitForTransfer("TO-E", "TT-3", money("70")));
        subscribers.awaitCatchUp(TIMEOUT);
        assertThat(balanceOf("TO-E")).isEqualByComparingTo("30");

        timeouts.expireOverdue(afterDeadline());
        subscribers.resume(SAGA);
        subscribers.awaitCatchUp(TIMEOUT);

        assertThat(transferRepository.load("TT-3").orElseThrow().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(balanceOf("TO-E")).isEqualByComparingTo("100");
        assertThat(balanceOf("TO-F")).isEqualByComparingTo("0");
        assertThat(eventTypes(AccountStream.id("TO-E")))
                .containsSubsequence("TransferDebited", "TransferDebitReversed");
        assertThat(transferQueries.find("TT-3").refunded()).isTrue();
    }

    @Test
    void timeoutDoesNotCancelTransferPastPointOfNoReturn() {
        openWithBalance("TO-G", "100");
        openWithBalance("TO-H", "0");
        transfers.request(new RequestTransfer("TT-4", "TO-G", "TO-H", money("25")));
        accounts.handle(new DebitForTransfer("TO-G", "TT-4", money("25")));
        transfers.startCredit("TT-4");

        boolean expired = transfers.expire("TT-4", afterDeadline());

        assertThat(expired).isFalse();
        assertThat(transferRepository.load("TT-4").orElseThrow().status()).isEqualTo(TransferStatus.CREDITING);

        subscribers.resume(SAGA);
        subscribers.awaitCatchUp(TIMEOUT);

        assertThat(transferRepository.load("TT-4").orElseThrow().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(balanceOf("TO-G")).isEqualByComparingTo("75");
        assertThat(balanceOf("TO-H")).isEqualByComparingTo("25");
    }

    private static Instant afterDeadline() {
        return Instant.now().plus(Duration.ofHours(1));
    }

    private void openWithBalance(String accountId, String balance) {
        accounts.handle(new OpenAccount(accountId, "Titular " + accountId));
        if (new BigDecimal(balance).signum() > 0) {
            accounts.handle(new Deposit(accountId, money(balance)));
        }
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

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
