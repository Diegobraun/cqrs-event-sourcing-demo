package bank.infrastructure.projection;

import bank.application.query.projection.Checkpoint;
import bank.application.query.projection.CheckpointStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCheckpointStore implements CheckpointStore {
    private final JdbcClient jdbc;

    public JdbcCheckpointStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Checkpoint load(String projectionName) {
        return jdbc.sql("SELECT position, stream_cursor FROM projection_checkpoints WHERE projection_name = :name")
                .param("name", projectionName)
                .query((rs, rowNum) -> new Checkpoint(rs.getLong("position"), rs.getString("stream_cursor")))
                .optional()
                .orElse(Checkpoint.initial());
    }

    @Override
    public void save(String projectionName, Checkpoint checkpoint) {
        jdbc.sql("""
                        INSERT INTO projection_checkpoints (projection_name, position, stream_cursor)
                        VALUES (:name, :position, :cursor)
                        ON CONFLICT (projection_name) DO UPDATE
                        SET position = EXCLUDED.position, stream_cursor = EXCLUDED.stream_cursor
                        """)
                .param("name", projectionName)
                .param("position", checkpoint.position())
                .param("cursor", checkpoint.streamCursor())
                .update();
    }
}
