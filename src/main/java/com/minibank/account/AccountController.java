package com.minibank.account;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.DepositRequest;
import com.minibank.account.dto.OpenAccountRequest;
import com.minibank.auth.CurrentUserService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Open accounts, deposit funds and follow balances")
class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUser;

    AccountController(AccountService accountService, CurrentUserService currentUser) {
        this.accountService = accountService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse open(@Valid @RequestBody OpenAccountRequest request) {
        return AccountResponse.from(
                accountService.open(currentUser.requireCurrentUserId(), request.holderName(), request.currency()));
    }

    @GetMapping
    List<AccountResponse> list() {
        return accountService.list(currentUser.requireCurrentUserId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id, currentUser.requireCurrentUserId()));
    }

    @PostMapping("/{id}/deposits")
    AccountResponse deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        return AccountResponse.from(
                accountService.deposit(id, request.amount(), currentUser.requireCurrentUserId()));
    }
}
