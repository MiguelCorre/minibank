package com.minibank.account;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts")
class AccountController {

    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse open(@Valid @RequestBody OpenAccountRequest request) {
        return AccountResponse.from(accountService.open(request.holderName(), request.currency()));
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id));
    }

    @PostMapping("/{id}/deposits")
    AccountResponse deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        return AccountResponse.from(accountService.deposit(id, request.amount()));
    }
}
