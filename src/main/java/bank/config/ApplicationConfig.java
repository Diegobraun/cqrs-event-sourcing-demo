package bank.config;

import bank.application.command.AccountCommandHandler;
import bank.application.command.AccountRepository;
import bank.application.eventstore.EventStore;
import bank.application.eventstore.EventSubscriber;
import bank.application.query.AccountAuditService;
import bank.application.query.AccountQueryService;
import bank.application.query.AccountSummaryStore;
import bank.application.query.StatementStore;
import bank.application.query.TransferQueryService;
import bank.application.query.TransferViewStore;
import bank.application.query.projection.AccountSummaryProjection;
import bank.application.query.projection.CheckpointStore;
import bank.application.query.projection.StatementProjection;
import bank.application.query.projection.TransferProjection;
import bank.application.transfer.OverdueTransferFinder;
import bank.application.transfer.TransferCommandHandler;
import bank.application.transfer.TransferRepository;
import bank.application.transfer.TransferSaga;
import bank.application.transfer.TransferTimeoutService;
import bank.infrastructure.eventstore.MongoEventMapper;
import bank.infrastructure.persistence.EventSourcedAccountRepository;
import bank.infrastructure.persistence.EventSourcedTransferRepository;
import bank.infrastructure.persistence.SnapshotStore;
import bank.infrastructure.projection.ProjectionRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Configuration
@EnableConfigurationProperties(BankProperties.class)
@EnableScheduling
public class ApplicationConfig {

    @Bean
    AccountRepository accountRepository(EventStore eventStore, SnapshotStore snapshotStore,
                                        BankProperties properties) {
        return new EventSourcedAccountRepository(eventStore, snapshotStore, properties.snapshotEvery());
    }

    @Bean
    AccountCommandHandler accountCommandHandler(AccountRepository accountRepository) {
        return new AccountCommandHandler(accountRepository);
    }

    @Bean
    AccountSummaryProjection accountSummaryProjection(AccountSummaryStore store) {
        return new AccountSummaryProjection(store);
    }

    @Bean
    StatementProjection statementProjection(StatementStore store) {
        return new StatementProjection(store);
    }

    @Bean
    ProjectionRunner projectionRunner(MongoTemplate mongoTemplate, MongoEventMapper mapper, EventStore eventStore,
                                      List<EventSubscriber> subscribers, CheckpointStore checkpoints,
                                      TransactionTemplate transactionTemplate, BankProperties properties) {
        return new ProjectionRunner(mongoTemplate, mapper, eventStore, subscribers, checkpoints, transactionTemplate,
                properties.projectionPollInterval());
    }

    @Bean
    AccountQueryService accountQueryService(AccountSummaryStore summaryStore, StatementStore statementStore) {
        return new AccountQueryService(summaryStore, statementStore);
    }

    @Bean
    AccountAuditService accountAuditService(EventStore eventStore) {
        return new AccountAuditService(eventStore);
    }

    @Bean
    TransferRepository transferRepository(EventStore eventStore) {
        return new EventSourcedTransferRepository(eventStore);
    }

    @Bean
    TransferCommandHandler transferCommandHandler(TransferRepository transferRepository, BankProperties properties) {
        return new TransferCommandHandler(transferRepository, properties.transferTimeout());
    }

    @Bean
    TransferTimeoutService transferTimeoutService(OverdueTransferFinder finder,
                                                  TransferCommandHandler transferCommandHandler) {
        return new TransferTimeoutService(finder, transferCommandHandler);
    }

    @Bean
    TransferSaga transferSaga(TransferRepository transferRepository, TransferCommandHandler transferCommandHandler,
                              AccountCommandHandler accountCommandHandler) {
        return new TransferSaga(transferRepository, transferCommandHandler, accountCommandHandler);
    }

    @Bean
    TransferProjection transferProjection(TransferViewStore store) {
        return new TransferProjection(store);
    }

    @Bean
    TransferQueryService transferQueryService(TransferViewStore store) {
        return new TransferQueryService(store);
    }
}
