package bank.application.transfer;

import bank.application.eventstore.ConcurrencyException;

import java.time.Instant;

public final class TransferTimeoutService {
    private final OverdueTransferFinder finder;
    private final TransferCommandHandler transfers;

    public TransferTimeoutService(OverdueTransferFinder finder, TransferCommandHandler transfers) {
        this.finder = finder;
        this.transfers = transfers;
    }

    public int expireOverdue(Instant now) {
        int expired = 0;
        for (String transferId : finder.findOverdue(now)) {
            try {
                if (transfers.expire(transferId, now)) {
                    expired++;
                }
            } catch (ConcurrencyException e) {
                continue;
            }
        }
        return expired;
    }
}
