package com.minibank.common.error;

public final class EmailAlreadyUsedException extends DomainException {

    public EmailAlreadyUsedException(String email) {
        super("Email %s is already registered".formatted(email));
    }
}
