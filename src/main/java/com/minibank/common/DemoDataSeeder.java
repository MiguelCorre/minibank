package com.minibank.common;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.minibank.account.AccountRepository;
import com.minibank.account.AccountService;
import com.minibank.auth.AuthService;
import com.minibank.auth.UserRepository;

@Component
@Profile("!test")
class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AccountService accountService;
    private final AccountRepository accounts;
    private final AuthService authService;
    private final UserRepository users;

    DemoDataSeeder(AccountService accountService, AccountRepository accounts,
                   AuthService authService, UserRepository users) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.authService = authService;
        this.users = users;
    }

    @Override
    public void run(String... args) {
        var demo = users.findByEmail("demo@minibank.dev")
                .orElseGet(() -> {
                    log.info("Seeding demo user: demo@minibank.dev / demo1234");
                    return authService.register("demo@minibank.dev", "demo1234", "Demo User");
                });
        if (accounts.count() > 0) {
            return; // database already seeded (persistent PostgreSQL)
        }
        var alice = accountService.open(demo.getId(), "Alice Martins", "EUR");
        accountService.deposit(alice.getId(), new BigDecimal("1000.00"), demo.getId());
        var bruno = accountService.open(demo.getId(), "Bruno Costa", "EUR");
        accountService.deposit(bruno.getId(), new BigDecimal("250.00"), demo.getId());
        log.info("Seeded demo accounts: alice={} bruno={}", alice.getId(), bruno.getId());
    }
}
