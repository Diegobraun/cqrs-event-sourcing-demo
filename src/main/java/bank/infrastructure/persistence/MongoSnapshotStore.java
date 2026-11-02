package bank.infrastructure.persistence;

import bank.domain.account.AccountSnapshot;
import bank.domain.account.AccountStatus;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MongoSnapshotStore implements SnapshotStore {
    private static final String SNAPSHOTS = "snapshots";

    private final MongoTemplate mongo;

    public MongoSnapshotStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(AccountSnapshot snapshot) {
        Document document = new Document("_id", snapshot.accountId())
                .append("owner", snapshot.owner())
                .append("balance", new Decimal128(snapshot.balance()))
                .append("status", snapshot.status().name())
                .append("transferOperations", List.copyOf(snapshot.transferOperations()))
                .append("version", snapshot.version());
        snapshots().replaceOne(Filters.eq("_id", snapshot.accountId()), document, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<AccountSnapshot> find(String accountId) {
        return Optional.ofNullable(snapshots().find(Filters.eq("_id", accountId)).first())
                .map(document -> new AccountSnapshot(
                        document.getString("_id"),
                        document.getString("owner"),
                        document.get("balance", Decimal128.class).bigDecimalValue(),
                        AccountStatus.valueOf(document.getString("status")),
                        Set.copyOf(document.getList("transferOperations", String.class, List.of())),
                        document.getLong("version")));
    }

    private MongoCollection<Document> snapshots() {
        return mongo.getCollection(SNAPSHOTS);
    }
}
