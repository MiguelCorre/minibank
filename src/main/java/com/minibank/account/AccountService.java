package com.minibank.account;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.common.error.AccountNotFoundException;
import com.minibank.ledger.LedgerEntry;
import com.minibank.ledger.LedgerRepository;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    public AccountService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public Account open(String holderName, String currency) {
        return accounts.save(Account.open(holderName, currency.toUpperCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional
    public Account deposit(UUID id, BigDecimal amount) {
        Account account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.credit(amount);
        ledger.save(LedgerEntry.credit(account.getId(), null, amount, account.getBalance()));
        return account;
    }
}
