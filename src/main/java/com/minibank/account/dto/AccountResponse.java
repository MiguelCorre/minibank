package com.minibank.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.minibank.account.Account;

public record AccountResponse(
        UUID id,
        String accountNumber,
        String holderName,
        String currency,
        BigDecimal balance,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getHolderName(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt());
    }
}
