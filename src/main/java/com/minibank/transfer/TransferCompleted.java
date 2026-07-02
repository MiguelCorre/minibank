package com.minibank.transfer;

import java.math.BigDecimal;
import java.util.UUID;

/** Domain event raised in the same transaction as the transfer itself. */
public record TransferCompleted(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency) {
}
