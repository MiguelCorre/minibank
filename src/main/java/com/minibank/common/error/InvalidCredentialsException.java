package com.minibank.common.error;

public final class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
