package com.minibank.common.error;

import java.math.BigDecimal;
import java.util.UUID;

public final class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(UUID accountId, BigDecimal balance, BigDecimal requested) {
        super("Account %s has balance %s, cannot debit %s".formatted(accountId, balance, requested));
    }
}
