package com.minibank.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/fx/rates")
@Tag(name = "FX", description = "Exchange rates used for cross-currency transfers")
class FxController {

    record RateResponse(String baseCurrency, String quoteCurrency, BigDecimal rate, Instant updatedAt) {
    }

    private final ExchangeRateRepository rates;

    FxController(ExchangeRateRepository rates) {
        this.rates = rates;
    }

    @GetMapping
    List<RateResponse> list() {
        return rates.findAll().stream()
                .map(rate -> new RateResponse(
                        rate.getBaseCurrency(), rate.getQuoteCurrency(), rate.getRate(), rate.getUpdatedAt()))
                .toList();
    }
}
