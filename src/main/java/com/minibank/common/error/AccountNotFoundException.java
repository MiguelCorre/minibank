package com.minibank.common.error;

import java.util.UUID;

public final class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(UUID accountId) {
        super("Account %s not found".formatted(accountId));
    }
}
