package com.minibank.ledger;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.auth.CurrentUserService;
import com.minibank.common.error.InvalidTransferException;
import com.minibank.ledger.StatementService.Statement;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/accounts/{accountId}/statement")
@Tag(name = "Statements", description = "Account statements as CSV or PDF")
class StatementController {

    private final StatementService statementService;
    private final CurrentUserService currentUser;

    StatementController(StatementService statementService, CurrentUserService currentUser) {
        this.statementService = statementService;
        this.currentUser = currentUser;
    }

    @GetMapping
    ResponseEntity<byte[]> statement(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "csv") String format) {
        Statement statement = statementService.statement(
                accountId, currentUser.requireCurrentUserId(), from, to);
        String filename = "statement-%s-%s-%s.%s"
                .formatted(statement.account().getAccountNumber(), from, to, format);
        return switch (format) {
            case "csv" -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(new MediaType("text", "csv"))
                    .body(statementService.toCsv(statement));
            case "pdf" -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(statementService.toPdf(statement));
            default -> throw new IllegalArgumentException("format must be csv or pdf");
        };
    }
}
