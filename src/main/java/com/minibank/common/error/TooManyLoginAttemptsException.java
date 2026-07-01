package com.minibank.common.error;

public final class TooManyLoginAttemptsException extends DomainException {

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Too many failed attempts; try again in %d seconds".formatted(retryAfterSeconds));
    }
}
