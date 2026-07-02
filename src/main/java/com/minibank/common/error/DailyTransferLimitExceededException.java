package com.minibank.common.error;

import java.math.BigDecimal;

public final class DailyTransferLimitExceededException extends DomainException {

    public DailyTransferLimitExceededException(BigDecimal limit) {
        super("Daily transfer limit of %s exceeded for this account".formatted(limit));
    }
}
