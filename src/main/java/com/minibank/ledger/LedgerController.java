package com.minibank.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.account.AccountService;
import com.minibank.auth.CurrentUserService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/accounts/{accountId}/ledger")
@Tag(name = "Ledger", description = "Double-entry movement history per account")
class LedgerController {

    private final LedgerRepository ledger;
    private final AccountService accountService;
    private final CurrentUserService currentUser;

    LedgerController(LedgerRepository ledger, AccountService accountService, CurrentUserService currentUser) {
        this.ledger = ledger;
        this.accountService = accountService;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<LedgerEntryResponse> list(@PathVariable UUID accountId) {
        accountService.get(accountId, currentUser.requireCurrentUserId()); // 404 unless caller owns it
        return ledger.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }
}
