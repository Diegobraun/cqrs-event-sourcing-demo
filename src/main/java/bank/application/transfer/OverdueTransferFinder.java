package bank.application.transfer;

import java.time.Instant;
import java.util.List;

public interface OverdueTransferFinder {
    List<String> findOverdue(Instant now);
}
