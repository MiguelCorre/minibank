package com.minibank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.account.Account;
import com.minibank.account.AccountRepository;
import com.minibank.common.error.AccountNotFoundException;
import com.minibank.common.error.CurrencyMismatchException;
import com.minibank.common.error.DailyTransferLimitExceededException;
import com.minibank.common.error.InvalidTransferException;
import com.minibank.common.error.TransferNotFoundException;
import com.minibank.ledger.LedgerEntry;
import com.minibank.ledger.LedgerRepository;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final ApplicationEventPublisher events;
    private final BigDecimal dailyLimit;

    public TransferService(TransferRepository transfers, AccountRepository accounts, LedgerRepository ledger,
                           ApplicationEventPublisher events,
                           @Value("${minibank.limits.daily-transfer}") BigDecimal dailyLimit) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.ledger = ledger;
        this.events = events;
        this.dailyLimit = dailyLimit;
    }

    /**
     * Executes a transfer exactly once per idempotency key. The caller must
     * own the source account (a foreign account 404s like a missing one);
     * the destination may belong to any customer. Outgoing transfers are
     * capped per account per UTC day.
     */
    @Transactional
    public Transfer transfer(UUID callerId, String idempotencyKey, UUID fromId, UUID toId,
                             BigDecimal amount, String description) {
        var existing = transfers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (fromId.equals(toId)) {
            throw new InvalidTransferException("Source and destination accounts must differ");
        }

        // Lock both accounts in a stable order so two opposite transfers
        // running concurrently cannot deadlock.
        UUID firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID secondId = firstId.equals(fromId) ? toId : fromId;
        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);
        Account from = first.getId().equals(fromId) ? first : second;
        Account to = from == first ? second : first;

        if (!from.isOwnedBy(callerId)) {
            throw new AccountNotFoundException(fromId);
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }

        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal spentToday = transfers.sumOutgoingSince(fromId, startOfDay);
        if (spentToday.add(amount).compareTo(dailyLimit) > 0) {
            throw new DailyTransferLimitExceededException(dailyLimit);
        }

        from.debit(amount);
        to.credit(amount);

        Transfer transfer = transfers.save(
                Transfer.create(idempotencyKey, fromId, toId, amount, from.getCurrency(), description));
        ledger.save(LedgerEntry.debit(from.getId(), transfer.getId(), amount, from.getBalance()));
        ledger.save(LedgerEntry.credit(to.getId(), transfer.getId(), amount, to.getBalance()));
        events.publishEvent(new TransferCompleted(
                transfer.getId(), fromId, toId, amount, from.getCurrency()));
        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id, UUID callerId) {
        Transfer transfer = transfers.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
        boolean participant = isOwnedBy(transfer.getFromAccountId(), callerId)
                || isOwnedBy(transfer.getToAccountId(), callerId);
        if (!participant) {
            throw new TransferNotFoundException(id);
        }
        return transfer;
    }

    private boolean isOwnedBy(UUID accountId, UUID userId) {
        return accounts.findById(accountId)
                .map(account -> account.isOwnedBy(userId))
                .orElse(false);
    }

    private Account lockAccount(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
