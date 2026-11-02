package bank.application.query.projection;

import java.util.List;

public interface ProjectionManager {
    List<ProjectionStatus> status();

    void rebuild(String projectionName);
}
