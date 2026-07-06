package com.minibank.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.common.error.UnsupportedCurrencyPairException;

/**
 * Converts amounts between currencies using the stored rates. Falls back to
 * the inverse pair when only the opposite direction is stored. Monetary
 * rounding is HALF_EVEN (banker's rounding) to scale 2.
 */
@Service
public class FxService {

    public record Quote(BigDecimal rate, BigDecimal convertedAmount) {
    }

    private static final int RATE_SCALE = 6;

    private final ExchangeRateRepository rates;

    public FxService(ExchangeRateRepository rates) {
        this.rates = rates;
    }

    @Transactional(readOnly = true)
    public Quote quote(String fromCurrency, String toCurrency, BigDecimal amount) {
        if (fromCurrency.equals(toCurrency)) {
            return new Quote(BigDecimal.ONE, amount);
        }
        var direct = rates.findByBaseCurrencyAndQuoteCurrency(fromCurrency, toCurrency);
        if (direct.isPresent()) {
            BigDecimal rate = direct.get().getRate();
            return new Quote(rate, amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN));
        }
        var inverse = rates.findByBaseCurrencyAndQuoteCurrency(toCurrency, fromCurrency);
        if (inverse.isPresent()) {
            BigDecimal rate = BigDecimal.ONE.divide(inverse.get().getRate(), RATE_SCALE, RoundingMode.HALF_EVEN);
            return new Quote(rate, amount.divide(inverse.get().getRate(), 2, RoundingMode.HALF_EVEN));
        }
        throw new UnsupportedCurrencyPairException(fromCurrency, toCurrency);
    }
}
