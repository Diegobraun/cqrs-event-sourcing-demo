package bank.application.query;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AccountQueryService {
    private final AccountSummaryStore summaries;
    private final StatementStore statements;

    public AccountQueryService(AccountSummaryStore summaries, StatementStore statements) {
        this.summaries = summaries;
        this.statements = statements;
    }

    public Optional<AccountSummary> summary(String accountId) {
        return summaries.find(accountId);
    }

    public AccountSummary summary(String accountId, long minVersion) {
        Optional<AccountSummary> summary = summaries.find(accountId);
        long currentVersion = summary.map(AccountSummary::lastVersion).orElse(0L);
        if (minVersion > currentVersion) {
            throw new ReadModelBehindException(accountId, currentVersion, minVersion);
        }
        return summary.orElseThrow(() -> new AccountViewNotFoundException(accountId));
    }

    public List<StatementLine> statement(String accountId) {
        return statements.find(accountId);
    }

    public List<AccountSummary> topByBalance(int limit) {
        return summaries.findAll().stream()
                .filter(summary -> summary.status().equals("OPEN"))
                .sorted(Comparator.comparing(AccountSummary::balance).reversed())
                .limit(limit)
                .toList();
    }
}
