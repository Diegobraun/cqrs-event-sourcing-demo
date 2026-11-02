package bank.interfaces.http.command;

import bank.application.command.AccountCommandHandler;
import bank.application.command.CloseAccount;
import bank.application.command.Command;
import bank.application.command.CommandResult;
import bank.application.command.Deposit;
import bank.application.command.OpenAccount;
import bank.application.command.Withdraw;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/accounts")
public class AccountCommandController {
    private final AccountCommandHandler handler;

    public AccountCommandController(AccountCommandHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<CommandResult> open(@Valid @RequestBody OpenAccountRequest request) {
        return dispatch(new OpenAccount(request.accountId(), request.owner()));
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<CommandResult> deposit(@PathVariable String id, @Valid @RequestBody MoneyRequest request) {
        return dispatch(new Deposit(id, request.amount()));
    }

    @PostMapping("/{id}/withdrawals")
    public ResponseEntity<CommandResult> withdraw(@PathVariable String id, @Valid @RequestBody MoneyRequest request) {
        return dispatch(new Withdraw(id, request.amount()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<CommandResult> close(@PathVariable String id) {
        return dispatch(new CloseAccount(id));
    }

    private ResponseEntity<CommandResult> dispatch(Command command) {
        CommandResult result = handler.handle(command);
        URI location = UriComponentsBuilder.fromPath("/accounts/{id}")
                .queryParam("minVersion", result.version())
                .buildAndExpand(result.accountId())
                .toUri();
        return ResponseEntity.accepted().location(location).body(result);
    }
}
