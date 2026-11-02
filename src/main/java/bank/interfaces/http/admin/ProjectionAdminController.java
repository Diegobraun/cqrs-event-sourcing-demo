package bank.interfaces.http.admin;

import bank.application.query.projection.ProjectionManager;
import bank.application.query.projection.ProjectionStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/projections")
public class ProjectionAdminController {
    private final ProjectionManager projections;

    public ProjectionAdminController(ProjectionManager projections) {
        this.projections = projections;
    }

    @GetMapping
    public List<ProjectionStatus> status() {
        return projections.status();
    }

    @PostMapping("/{name}/rebuild")
    public ResponseEntity<Void> rebuild(@PathVariable String name) {
        projections.rebuild(name);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{name}/pause")
    public ResponseEntity<Void> pause(@PathVariable String name) {
        projections.pause(name);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{name}/resume")
    public ResponseEntity<Void> resume(@PathVariable String name) {
        projections.resume(name);
        return ResponseEntity.accepted().build();
    }
}
