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
import com.minibank.common.error.DailyTransferLimitExceededException;
import com.minibank.common.error.InvalidTransferException;
import com.minibank.common.error.TransferNotFoundException;
import com.minibank.fx.FxService;
import com.minibank.ledger.LedgerEntry;
import com.minibank.ledger.LedgerRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final FxService fx;
    private final ApplicationEventPublisher events;
    private final MeterRegistry metrics;
    private final BigDecimal dailyLimit;

    public TransferService(TransferRepository transfers, AccountRepository accounts, LedgerRepository ledger,
                           FxService fx, ApplicationEventPublisher events, MeterRegistry metrics,
                           @Value("${minibank.limits.daily-transfer}") BigDecimal dailyLimit) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.ledger = ledger;
        this.fx = fx;
        this.events = events;
        this.metrics = metrics;
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
        // keys are scoped per initiating customer: someone else's key can
        // never return (or block on) their transfer
        var existing = transfers.findByInitiatedByAndIdempotencyKey(callerId, idempotencyKey);
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

        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal spentToday = transfers.sumOutgoingSince(fromId, startOfDay);
        if (spentToday.add(amount).compareTo(dailyLimit) > 0) {
            throw new DailyTransferLimitExceededException(dailyLimit);
        }

        // cross-currency transfers convert at the stored rate (422 if no rate exists)
        FxService.Quote quote = fx.quote(from.getCurrency(), to.getCurrency(), amount);

        from.debit(amount);
        to.credit(quote.convertedAmount());

        Transfer transfer = transfers.save(Transfer.create(
                callerId, idempotencyKey, fromId, toId, amount, from.getCurrency(),
                quote.convertedAmount(), to.getCurrency(), quote.rate(), description));
        ledger.save(LedgerEntry.debit(from.getId(), transfer.getId(), amount, from.getBalance()));
        ledger.save(LedgerEntry.credit(to.getId(), transfer.getId(), quote.convertedAmount(), to.getBalance()));
        events.publishEvent(new TransferCompleted(
                transfer.getId(), fromId, toId, amount, from.getCurrency(),
                quote.convertedAmount(), to.getCurrency()));
        Counter.builder("minibank.transfers.completed")
                .tag("currency", from.getCurrency())
                .tag("cross_currency", String.valueOf(!from.getCurrency().equals(to.getCurrency())))
                .register(metrics)
                .increment();
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
