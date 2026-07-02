package com.minibank.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.common.error.AccountNotFoundException;
import com.minibank.ledger.LedgerEntry;
import com.minibank.ledger.LedgerRepository;

/**
 * Account operations are scoped to the owning customer: another customer's
 * account behaves exactly like a missing one (404, no existence leak).
 */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    public AccountService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public Account open(UUID ownerId, String holderName, String currency) {
        return accounts.save(Account.open(ownerId, holderName, currency.toUpperCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public Account get(UUID id, UUID ownerId) {
        return accounts.findById(id)
                .filter(account -> account.isOwnedBy(ownerId))
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Account> list(UUID ownerId) {
        return accounts.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional
    public Account deposit(UUID id, BigDecimal amount, UUID ownerId) {
        Account account = accounts.findByIdForUpdate(id)
                .filter(found -> found.isOwnedBy(ownerId))
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.credit(amount);
        ledger.save(LedgerEntry.credit(account.getId(), null, amount, account.getBalance()));
        return account;
    }
}
