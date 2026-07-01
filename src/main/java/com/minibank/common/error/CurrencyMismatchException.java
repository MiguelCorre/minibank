package com.minibank.common.error;

public final class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(String fromCurrency, String toCurrency) {
        super("Cannot transfer between %s and %s accounts".formatted(fromCurrency, toCurrency));
    }
}
