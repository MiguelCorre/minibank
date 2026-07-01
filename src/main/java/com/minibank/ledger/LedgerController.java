package com.minibank.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.account.AccountService;

@RestController
@RequestMapping("/api/accounts/{accountId}/ledger")
class LedgerController {

    private final LedgerRepository ledger;
    private final AccountService accountService;

    LedgerController(LedgerRepository ledger, AccountService accountService) {
        this.ledger = ledger;
        this.accountService = accountService;
    }

    @GetMapping
    List<LedgerEntryResponse> list(@PathVariable UUID accountId) {
        accountService.get(accountId); // 404 if the account does not exist
        return ledger.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }
}
