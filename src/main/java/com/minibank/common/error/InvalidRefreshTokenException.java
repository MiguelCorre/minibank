package com.minibank.common.error;

public final class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
