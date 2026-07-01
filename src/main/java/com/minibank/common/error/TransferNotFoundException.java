package com.minibank.common.error;

import java.util.UUID;

public final class TransferNotFoundException extends DomainException {

    public TransferNotFoundException(UUID transferId) {
        super("Transfer %s not found".formatted(transferId));
    }
}
