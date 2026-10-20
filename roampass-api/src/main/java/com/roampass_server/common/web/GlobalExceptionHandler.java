package com.roampass_server.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.roampass_server.common.domain.DomainException;
import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;

import lombok.extern.slf4j.Slf4j;

/**
 * Maps exceptions to RFC 9457 problem details with an extra {@code code} property.
 * Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions
 * (404 no handler, 405, malformed body, ...) keep their proper status instead of falling into the 500 handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(MissingAuthHeaderException.class)
    ResponseEntity<ProblemDetail> handleMissingAuthHeader(MissingAuthHeaderException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(InvalidValueException.class)
    ResponseEntity<ProblemDetail> handleInvalidValue(InvalidValueException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ResponseEntity<ProblemDetail> handleIllegalStateTransition(IllegalStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, e.getCode(), e.getMessage());
    }

    // Fallback for domain exceptions that have no dedicated mapping yet.
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
