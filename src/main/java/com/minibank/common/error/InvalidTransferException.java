package com.minibank.common.error;

public final class InvalidTransferException extends DomainException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
