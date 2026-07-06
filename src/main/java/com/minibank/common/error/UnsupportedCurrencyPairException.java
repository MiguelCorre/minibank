package com.minibank.common.error;

public final class UnsupportedCurrencyPairException extends DomainException {

    public UnsupportedCurrencyPairException(String fromCurrency, String toCurrency) {
        super("No exchange rate available for %s/%s".formatted(fromCurrency, toCurrency));
    }
}
