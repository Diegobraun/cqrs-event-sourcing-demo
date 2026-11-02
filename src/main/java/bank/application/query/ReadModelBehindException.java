package bank.application.query;

public class ReadModelBehindException extends RuntimeException {
    public ReadModelBehindException(String accountId, long currentVersion, long requestedVersion) {
        super("Read model de " + accountId + " na versao " + currentVersion + ", solicitada " + requestedVersion);
    }
}
