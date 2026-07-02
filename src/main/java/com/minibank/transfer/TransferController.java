package com.minibank.transfer;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.auth.CurrentUserService;
import com.minibank.transfer.dto.TransferRequest;
import com.minibank.transfer.dto.TransferResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "Idempotent money movements between accounts")
class TransferController {

    private final TransferService transferService;
    private final CurrentUserService currentUser;

    TransferController(TransferService transferService, CurrentUserService currentUser) {
        this.transferService = transferService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransferResponse transfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                              @Valid @RequestBody TransferRequest request) {
        var transfer = transferService.transfer(
                currentUser.requireCurrentUserId(),
                idempotencyKey,
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.description());
        return TransferResponse.from(transfer);
    }

    @GetMapping("/{id}")
    TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(transferService.get(id, currentUser.requireCurrentUserId()));
    }
}
