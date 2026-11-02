package bank.application.query.projection;

public class ProjectionNotFoundException extends RuntimeException {
    public ProjectionNotFoundException(String name) {
        super("Projecao " + name + " nao existe");
    }
}
