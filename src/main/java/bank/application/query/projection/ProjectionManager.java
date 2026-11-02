package bank.application.query.projection;

import java.util.List;

public interface ProjectionManager {
    List<ProjectionStatus> status();

    void rebuild(String projectionName);

    void pause(String subscriberName);

    void resume(String subscriberName);
}
