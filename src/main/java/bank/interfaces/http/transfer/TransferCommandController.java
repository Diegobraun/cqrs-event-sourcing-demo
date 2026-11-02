package bank.interfaces.http.transfer;

import bank.application.transfer.RequestTransfer;
import bank.application.transfer.TransferCommandHandler;
import bank.application.transfer.TransferResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
public class TransferCommandController {
    private final TransferCommandHandler handler;

    public TransferCommandController(TransferCommandHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResult> request(@Valid @RequestBody TransferRequest request) {
        TransferResult result = handler.request(new RequestTransfer(request.transferId(), request.sourceAccountId(),
                request.targetAccountId(), request.amount()));
        URI location = UriComponentsBuilder.fromPath("/transfers/{id}").buildAndExpand(result.transferId()).toUri();
        return ResponseEntity.accepted().location(location).body(result);
    }
}
