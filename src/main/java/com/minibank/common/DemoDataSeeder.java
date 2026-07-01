package com.minibank.common;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.minibank.account.AccountRepository;
import com.minibank.account.AccountService;

@Component
@Profile("!test")
class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AccountService accountService;
    private final AccountRepository accounts;

    DemoDataSeeder(AccountService accountService, AccountRepository accounts) {
        this.accountService = accountService;
        this.accounts = accounts;
    }

    @Override
    public void run(String... args) {
        if (accounts.count() > 0) {
            return; // database already seeded (persistent PostgreSQL)
        }
        var alice = accountService.open("Alice Martins", "EUR");
        accountService.deposit(alice.getId(), new BigDecimal("1000.00"));
        var bruno = accountService.open("Bruno Costa", "EUR");
        accountService.deposit(bruno.getId(), new BigDecimal("250.00"));
        log.info("Seeded demo accounts: alice={} bruno={}", alice.getId(), bruno.getId());
    }
}
