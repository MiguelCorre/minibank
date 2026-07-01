package com.minibank.common.error;

/**
 * Sealed root of all business-rule violations. The API layer maps each
 * permitted subtype to an HTTP status with an exhaustive pattern-matching
 * switch, so adding a new subtype fails compilation until it is mapped.
 */
public abstract sealed class DomainException extends RuntimeException
        permits AccountNotFoundException, TransferNotFoundException, InsufficientFundsException,
                CurrencyMismatchException, InvalidTransferException,
                EmailAlreadyUsedException, InvalidCredentialsException {

    protected DomainException(String message) {
        super(message);
    }
}
