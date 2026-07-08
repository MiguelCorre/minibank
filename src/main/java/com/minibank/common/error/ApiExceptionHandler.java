package com.minibank.common.error;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into RFC 7807 problem-detail responses.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail onDomainException(DomainException ex) {
        var status = switch (ex) {
            case AccountNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case TransferNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case InsufficientFundsException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UnsupportedCurrencyPairException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case DailyTransferLimitExceededException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case InvalidTransferException ignored -> HttpStatus.BAD_REQUEST;
            case EmailAlreadyUsedException ignored -> HttpStatus.CONFLICT;
            case InvalidCredentialsException ignored -> HttpStatus.UNAUTHORIZED;
            case InvalidRefreshTokenException ignored -> HttpStatus.UNAUTHORIZED;
            case TooManyLoginAttemptsException ignored -> HttpStatus.TOO_MANY_REQUESTS;
        };
        return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail onMissingHeader(MissingRequestHeaderException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Required header '%s' is missing".formatted(ex.getHeaderName()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onDataConflict(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Request conflicts with existing data; if this was a retry, it is safe to repeat it");
    }
}
