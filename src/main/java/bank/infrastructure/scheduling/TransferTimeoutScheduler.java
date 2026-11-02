package bank.infrastructure.scheduling;

import bank.application.transfer.TransferTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TransferTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(TransferTimeoutScheduler.class);

    private final TransferTimeoutService timeouts;

    public TransferTimeoutScheduler(TransferTimeoutService timeouts) {
        this.timeouts = timeouts;
    }

    @Scheduled(fixedDelayString = "${bank.transfer-timeout-check-interval}")
    public void expireOverdueTransfers() {
        int expired = timeouts.expireOverdue(Instant.now());
        if (expired > 0) {
            log.info("{} transferencia(s) expirada(s) por timeout", expired);
        }
    }
}
