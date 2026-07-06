package com.minibank.transfer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.minibank.transfer.Transfer;

public record TransferResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        BigDecimal convertedAmount,
        String targetCurrency,
        BigDecimal exchangeRate,
        String description,
        Instant createdAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getConvertedAmount(),
                transfer.getTargetCurrency(),
                transfer.getExchangeRate(),
                transfer.getDescription(),
                transfer.getCreatedAt());
    }
}
