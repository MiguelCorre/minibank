package com.minibank.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.account.Account;
import com.minibank.account.AccountRepository;
import com.minibank.common.error.AccountNotFoundException;
import com.minibank.common.error.CurrencyMismatchException;
import com.minibank.common.error.InvalidTransferException;
import com.minibank.common.error.TransferNotFoundException;
import com.minibank.ledger.LedgerEntry;
import com.minibank.ledger.LedgerRepository;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    public TransferService(TransferRepository transfers, AccountRepository accounts, LedgerRepository ledger) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /**
     * Executes a transfer exactly once per idempotency key. A replay with a
     * key already processed returns the original transfer without touching
     * balances; a concurrent duplicate is rejected by the unique constraint
     * on the key and surfaces as 409.
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, UUID fromId, UUID toId,
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

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }

        from.debit(amount);
        to.credit(amount);

        Transfer transfer = transfers.save(
                Transfer.create(idempotencyKey, fromId, toId, amount, from.getCurrency(), description));
        ledger.save(LedgerEntry.debit(from.getId(), transfer.getId(), amount, from.getBalance()));
        ledger.save(LedgerEntry.credit(to.getId(), transfer.getId(), amount, to.getBalance()));
        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transfers.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    private Account lockAccount(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
