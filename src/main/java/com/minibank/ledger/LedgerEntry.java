package com.minibank.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Immutable double-entry record: every transfer writes one DEBIT and one
 * CREDIT entry; deposits write a single CREDIT with no transfer reference.
 */
@Entity
@Table(name = "ledger_entries", indexes = @Index(columnList = "accountId, createdAt"))
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA
    }

    private LedgerEntry(UUID accountId, UUID transferId, EntryType type,
                        BigDecimal amount, BigDecimal balanceAfter) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.transferId = transferId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }

    public static LedgerEntry debit(UUID accountId, UUID transferId, BigDecimal amount, BigDecimal balanceAfter) {
        return new LedgerEntry(accountId, transferId, EntryType.DEBIT, amount, balanceAfter);
    }

    public static LedgerEntry credit(UUID accountId, UUID transferId, BigDecimal amount, BigDecimal balanceAfter) {
        return new LedgerEntry(accountId, transferId, EntryType.CREDIT, amount, balanceAfter);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public EntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
