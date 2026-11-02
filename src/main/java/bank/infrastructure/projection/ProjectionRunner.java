package bank.infrastructure.projection;

import bank.application.eventstore.EventEnvelope;
import bank.application.eventstore.EventStore;
import bank.application.eventstore.EventSubscriber;
import bank.application.query.projection.Checkpoint;
import bank.application.query.projection.CheckpointStore;
import bank.application.query.projection.Projection;
import bank.application.query.projection.ProjectionManager;
import bank.application.query.projection.ProjectionNotFoundException;
import bank.application.query.projection.ProjectionStatus;
import bank.infrastructure.eventstore.MongoEventMapper;
import bank.infrastructure.eventstore.MongoEventStore;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ProjectionRunner implements SmartLifecycle, ProjectionManager {
    private static final Logger log = LoggerFactory.getLogger(ProjectionRunner.class);
    private static final int REPLAY_BATCH_SIZE = 100;

    private final MongoTemplate mongo;
    private final MongoEventMapper mapper;
    private final EventStore eventStore;
    private final List<EventSubscriber> subscribers;
    private final CheckpointStore checkpoints;
    private final TransactionTemplate transaction;
    private final Duration pollInterval;
    private final Map<String, Worker> workers = new LinkedHashMap<>();
    private final Set<String> paused = new HashSet<>();
    private boolean running;

    public ProjectionRunner(MongoTemplate mongo, MongoEventMapper mapper, EventStore eventStore,
                            List<EventSubscriber> subscribers, CheckpointStore checkpoints,
                            TransactionTemplate transaction, Duration pollInterval) {
        this.mongo = mongo;
        this.mapper = mapper;
        this.eventStore = eventStore;
        this.subscribers = List.copyOf(subscribers);
        this.checkpoints = checkpoints;
        this.transaction = transaction;
        this.pollInterval = pollInterval;
    }

    @Override
    public synchronized void start() {
        running = true;
        subscribers.stream()
                .filter(subscriber -> !paused.contains(subscriber.name()))
                .forEach(this::startWorker);
    }

    @Override
    public synchronized void stop() {
        running = false;
        workers.values().forEach(Worker::stop);
        workers.clear();
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void pause(String subscriberName) {
        EventSubscriber subscriber = subscriber(subscriberName);
        paused.add(subscriber.name());
        Worker worker = workers.remove(subscriber.name());
        if (worker != null) {
            worker.stop();
        }
    }

    @Override
    public synchronized void resume(String subscriberName) {
        EventSubscriber subscriber = subscriber(subscriberName);
        paused.remove(subscriber.name());
        if (running) {
            startWorker(subscriber);
        }
    }

    @Override
    public List<ProjectionStatus> status() {
        long last = eventStore.lastPosition();
        return subscribers.stream()
                .map(subscriber -> {
                    long position = checkpoints.load(subscriber.name()).position();
                    return new ProjectionStatus(subscriber.name(), position, last - position, isActive(subscriber));
                })
                .toList();
    }

    @Override
    public synchronized void rebuild(String projectionName) {
        Projection projection = subscribers.stream()
                .filter(candidate -> candidate instanceof Projection && candidate.name().equals(projectionName))
                .map(Projection.class::cast)
                .findFirst()
                .orElseThrow(() -> new ProjectionNotFoundException(projectionName));
        boolean wasRunning = isRunning();
        stop();
        transaction.executeWithoutResult(status -> {
            projection.reset();
            checkpoints.save(projection.name(), Checkpoint.initial());
        });
        if (wasRunning) {
            start();
        }
    }

    public void awaitCatchUp(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (lag() > 0) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("Assinantes ativos nao alcancaram o event store em " + timeout);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public long lag() {
        return status().stream()
                .filter(ProjectionStatus::running)
                .mapToLong(ProjectionStatus::lag)
                .max()
                .orElse(0);
    }

    public long checkpoint(String projectionName) {
        return checkpoints.load(projectionName).position();
    }

    private void startWorker(EventSubscriber subscriber) {
        if (workers.containsKey(subscriber.name())) {
            return;
        }
        Worker worker = new Worker(subscriber);
        workers.put(subscriber.name(), worker);
        worker.thread.start();
    }

    private synchronized boolean isActive(EventSubscriber subscriber) {
        return workers.containsKey(subscriber.name());
    }

    private EventSubscriber subscriber(String name) {
        return subscribers.stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ProjectionNotFoundException(name));
    }

    private final class Worker {
        private final EventSubscriber subscriber;
        private final Thread thread;
        private volatile boolean running = true;

        Worker(EventSubscriber subscriber) {
            this.subscriber = subscriber;
            this.thread = Thread.ofPlatform().daemon().name("subscriber-" + subscriber.name()).unstarted(this::run);
        }

        void stop() {
            running = false;
            try {
                thread.join(pollInterval.multipliedBy(10).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void run() {
            try {
                Checkpoint checkpoint = checkpoints.load(subscriber.name());
                try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = openCursor(checkpoint)) {
                    if (checkpoint.streamCursor() == null) {
                        checkpoint = replayHistory(checkpoint, cursor);
                    }
                    while (running) {
                        ChangeStreamDocument<Document> change = cursor.tryNext();
                        if (change != null) {
                            checkpoint = applyWithRetry(checkpoint, mapper.toEnvelope(change.getFullDocument()),
                                    change.getResumeToken().toJson());
                        }
                    }
                }
            } catch (RuntimeException e) {
                if (running) {
                    log.error("Assinante {} parou por falha", subscriber.name(), e);
                }
            }
        }

        private MongoChangeStreamCursor<ChangeStreamDocument<Document>> openCursor(Checkpoint checkpoint) {
            var watch = mongo.getCollection(MongoEventStore.EVENTS)
                    .watch(List.of(Aggregates.match(Filters.eq("operationType", "insert"))))
                    .maxAwaitTime(pollInterval.toMillis(), TimeUnit.MILLISECONDS);
            if (checkpoint.streamCursor() != null) {
                watch = watch.resumeAfter(BsonDocument.parse(checkpoint.streamCursor()));
            }
            return watch.cursor();
        }

        private Checkpoint replayHistory(Checkpoint checkpoint,
                                         MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor) {
            BsonDocument streamStart = cursor.getResumeToken();
            String startToken = streamStart == null ? null : streamStart.toJson();
            List<EventEnvelope> batch = eventStore.readAll(checkpoint.position(), REPLAY_BATCH_SIZE);
            while (!batch.isEmpty() && running) {
                for (EventEnvelope envelope : batch) {
                    checkpoint = applyWithRetry(checkpoint, envelope, startToken);
                }
                batch = eventStore.readAll(checkpoint.position(), REPLAY_BATCH_SIZE);
            }
            Checkpoint replayed = new Checkpoint(checkpoint.position(), startToken);
            checkpoints.save(subscriber.name(), replayed);
            return replayed;
        }

        private Checkpoint applyWithRetry(Checkpoint current, EventEnvelope envelope, String streamCursor) {
            while (true) {
                try {
                    return apply(current, envelope, streamCursor);
                } catch (RuntimeException e) {
                    if (!running) {
                        throw e;
                    }
                    log.warn("Assinante {} falhou no evento #{}, nova tentativa: {}",
                            subscriber.name(), envelope.position(), e.getMessage());
                    pause();
                }
            }
        }

        private Checkpoint apply(Checkpoint current, EventEnvelope envelope, String streamCursor) {
            Checkpoint next = new Checkpoint(Math.max(current.position(), envelope.position()), streamCursor);
            transaction.executeWithoutResult(status -> {
                subscriber.handle(envelope);
                checkpoints.save(subscriber.name(), next);
            });
            return next;
        }

        private void pause() {
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }
}
