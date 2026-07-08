package com.minibank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "transfers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"initiated_by", "idempotency_key"}))
public class Transfer {

    @Id
    private UUID id;

    /** Idempotency keys are scoped per initiating customer, not global. */
    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID initiatedBy;

    @Column(nullable = false)
    private UUID fromAccountId;

    @Column(nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal convertedAmount;

    @Column(nullable = false, length = 3)
    private String targetCurrency;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(length = 140)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
        // JPA
    }

    private Transfer(UUID initiatedBy, String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                     BigDecimal amount, String currency,
                     BigDecimal convertedAmount, String targetCurrency, BigDecimal exchangeRate,
                     String description) {
        this.id = UUID.randomUUID();
        this.initiatedBy = initiatedBy;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.convertedAmount = convertedAmount;
        this.targetCurrency = targetCurrency;
        this.exchangeRate = exchangeRate;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public static Transfer create(UUID initiatedBy, String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                                  BigDecimal amount, String currency,
                                  BigDecimal convertedAmount, String targetCurrency, BigDecimal exchangeRate,
                                  String description) {
        return new Transfer(initiatedBy, idempotencyKey, fromAccountId, toAccountId, amount, currency,
                convertedAmount, targetCurrency, exchangeRate, description);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
