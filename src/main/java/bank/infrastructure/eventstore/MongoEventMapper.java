package bank.infrastructure.eventstore;

import bank.application.eventstore.EventEnvelope;
import bank.domain.account.AccountClosed;
import bank.domain.account.AccountOpened;
import bank.domain.account.MoneyDeposited;
import bank.domain.account.MoneyWithdrawn;
import bank.domain.account.TransferCredited;
import bank.domain.account.TransferDebitReversed;
import bank.domain.account.TransferDebited;
import bank.domain.shared.DomainEvent;
import bank.domain.transfer.TransferCompleted;
import bank.domain.transfer.TransferFailed;
import bank.domain.transfer.TransferRequested;
import org.bson.Document;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class MongoEventMapper {
    private static final Map<String, Class<? extends DomainEvent>> TYPES = Map.ofEntries(
            Map.entry("AccountOpened", AccountOpened.class),
            Map.entry("MoneyDeposited", MoneyDeposited.class),
            Map.entry("MoneyWithdrawn", MoneyWithdrawn.class),
            Map.entry("AccountClosed", AccountClosed.class),
            Map.entry("TransferDebited", TransferDebited.class),
            Map.entry("TransferCredited", TransferCredited.class),
            Map.entry("TransferDebitReversed", TransferDebitReversed.class),
            Map.entry("TransferRequested", TransferRequested.class),
            Map.entry("TransferCompleted", TransferCompleted.class),
            Map.entry("TransferFailed", TransferFailed.class));

    private final MongoConverter converter;

    public MongoEventMapper(MongoConverter converter) {
        this.converter = converter;
    }

    public Document toDocument(long position, String streamId, long version, DomainEvent event) {
        Document payload = new Document();
        converter.write(event, payload);
        payload.remove("_class");
        return new Document("position", position)
                .append("streamId", streamId)
                .append("version", version)
                .append("type", typeOf(event))
                .append("payload", payload)
                .append("occurredAt", Date.from(event.occurredAt()))
                .append("recordedAt", Date.from(Instant.now()));
    }

    public EventEnvelope toEnvelope(Document document) {
        return new EventEnvelope(
                document.getLong("position"),
                document.getString("streamId"),
                document.getLong("version"),
                document.getDate("recordedAt").toInstant(),
                toEvent(document.getString("type"), document.get("payload", Document.class)));
    }

    private DomainEvent toEvent(String type, Document payload) {
        Class<? extends DomainEvent> eventClass = TYPES.get(type);
        if (eventClass == null) {
            throw new IllegalArgumentException("Tipo de evento desconhecido: " + type);
        }
        return converter.read(eventClass, payload);
    }

    private String typeOf(DomainEvent event) {
        String type = event.getClass().getSimpleName();
        if (!TYPES.containsKey(type)) {
            throw new IllegalArgumentException("Evento nao registrado: " + type);
        }
        return type;
    }
}
