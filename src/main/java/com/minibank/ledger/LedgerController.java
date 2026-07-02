package com.minibank.ledger;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.account.AccountService;
import com.minibank.auth.CurrentUserService;
import com.minibank.common.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/accounts/{accountId}/ledger")
@Tag(name = "Ledger", description = "Double-entry movement history per account")
class LedgerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LedgerRepository ledger;
    private final AccountService accountService;
    private final CurrentUserService currentUser;

    LedgerController(LedgerRepository ledger, AccountService accountService, CurrentUserService currentUser) {
        this.ledger = ledger;
        this.accountService = accountService;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<LedgerEntryResponse> list(@PathVariable UUID accountId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        accountService.get(accountId, currentUser.requireCurrentUserId()); // 404 unless caller owns it
        var pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(
                ledger.findByAccountIdOrderByCreatedAtDesc(accountId, pageRequest)
                        .map(LedgerEntryResponse::from));
    }
}
