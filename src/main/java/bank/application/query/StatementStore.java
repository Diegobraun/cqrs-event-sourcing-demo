package bank.application.query;

import java.util.List;

public interface StatementStore {
    void append(String accountId, StatementLine line);

    List<StatementLine> find(String accountId);

    void clear();
}
