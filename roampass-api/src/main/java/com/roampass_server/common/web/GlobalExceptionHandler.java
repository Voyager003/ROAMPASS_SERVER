package com.roampass_server.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.roampass_server.common.domain.exception.DomainException;
import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;

import lombok.extern.slf4j.Slf4j;

/**
 * 예외를 RFC 9457 problem detail로 변환하고 {@code code} 속성 추가
 * {@link ResponseEntityExceptionHandler}를 상속해서 Spring MVC 자체 예외
 * (핸들러 없음 404, 405, 잘못된 요청 본문 등)가 500 핸들러로 빠지지 않고 원래 상태 코드 유지
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

    // 전용 매핑이 아직 없는 도메인 예외의 기본 처리
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
