package bank.interfaces.http.transfer;

import bank.application.query.TransferQueryService;
import bank.application.query.TransferView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferQueryController {
    private final TransferQueryService queries;

    public TransferQueryController(TransferQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/transfers/{id}")
    public TransferView find(@PathVariable String id) {
        return queries.find(id);
    }
}
