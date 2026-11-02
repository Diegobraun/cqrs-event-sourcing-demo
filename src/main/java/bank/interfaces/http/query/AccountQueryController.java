package bank.interfaces.http.query;

import bank.application.query.AccountAuditService;
import bank.application.query.AccountQueryService;
import bank.application.query.AccountSummary;
import bank.application.query.AuditEntry;
import bank.application.query.StatementLine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AccountQueryController {
    private final AccountQueryService queries;
    private final AccountAuditService audit;

    public AccountQueryController(AccountQueryService queries, AccountAuditService audit) {
        this.queries = queries;
        this.audit = audit;
    }

    @GetMapping("/accounts/{id}")
    public AccountSummary summary(@PathVariable String id, @RequestParam(defaultValue = "0") long minVersion) {
        return queries.summary(id, minVersion);
    }

    @GetMapping("/accounts/{id}/statement")
    public List<StatementLine> statement(@PathVariable String id) {
        return queries.statement(id);
    }

    @GetMapping("/accounts/{id}/events")
    public List<AuditEntry> events(@PathVariable String id) {
        return audit.history(id);
    }

    @GetMapping("/rankings/balance")
    public List<AccountSummary> ranking(@RequestParam(defaultValue = "10") int limit) {
        return queries.topByBalance(limit);
    }
}
