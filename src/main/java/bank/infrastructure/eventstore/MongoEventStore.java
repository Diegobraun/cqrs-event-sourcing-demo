package bank.infrastructure.eventstore;

import bank.application.eventstore.ConcurrencyException;
import bank.application.eventstore.EventEnvelope;
import bank.application.eventstore.EventStore;
import bank.domain.shared.DomainEvent;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MongoEventStore implements EventStore {
    public static final String EVENTS = "events";
    private static final String COUNTERS = "counters";

    private final MongoTemplate mongo;
    private final MongoEventMapper mapper;

    public MongoEventStore(MongoTemplate mongo, MongoEventMapper mapper) {
        this.mongo = mongo;
        this.mapper = mapper;
    }

    @PostConstruct
    void createIndexes() {
        events().createIndex(Indexes.ascending("streamId", "version"),
                new IndexOptions().unique(true).name("uk_stream_version"));
        events().createIndex(Indexes.ascending("position"),
                new IndexOptions().unique(true).name("uk_position"));
    }

    @Override
    public void append(String streamId, long expectedVersion, List<? extends DomainEvent> events) {
        long currentVersion = currentVersion(streamId);
        if (currentVersion != expectedVersion) {
            throw new ConcurrencyException(streamId, expectedVersion, currentVersion);
        }
        List<Document> documents = new ArrayList<>();
        long version = expectedVersion;
        for (DomainEvent event : events) {
            documents.add(mapper.toDocument(nextPosition(), streamId, ++version, event));
        }
        try {
            events().insertMany(documents);
        } catch (MongoBulkWriteException e) {
            boolean duplicateVersion = e.getWriteErrors().stream()
                    .anyMatch(error -> ErrorCategory.fromErrorCode(error.getCode()) == ErrorCategory.DUPLICATE_KEY);
            if (duplicateVersion) {
                throw new ConcurrencyException(streamId, expectedVersion, currentVersion(streamId));
            }
            throw e;
        }
    }

    @Override
    public List<EventEnvelope> readStream(String streamId, long afterVersion) {
        return events()
                .find(Filters.and(Filters.eq("streamId", streamId), Filters.gt("version", afterVersion)))
                .sort(Sorts.ascending("version"))
                .map(mapper::toEnvelope)
                .into(new ArrayList<>());
    }

    @Override
    public List<EventEnvelope> readAll(long afterPosition, int limit) {
        return events()
                .find(Filters.gt("position", afterPosition))
                .sort(Sorts.ascending("position"))
                .limit(limit)
                .map(mapper::toEnvelope)
                .into(new ArrayList<>());
    }

    @Override
    public long lastPosition() {
        Document last = events().find().sort(Sorts.descending("position")).limit(1).first();
        return last == null ? 0 : last.getLong("position");
    }

    private long currentVersion(String streamId) {
        Document last = events().find(Filters.eq("streamId", streamId))
                .sort(Sorts.descending("version"))
                .limit(1)
                .first();
        return last == null ? 0 : last.getLong("version");
    }

    private long nextPosition() {
        Document counter = mongo.getCollection(COUNTERS).findOneAndUpdate(
                Filters.eq("_id", EVENTS),
                Updates.inc("seq", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return counter.getLong("seq");
    }

    private MongoCollection<Document> events() {
        return mongo.getCollection(EVENTS);
    }
}
