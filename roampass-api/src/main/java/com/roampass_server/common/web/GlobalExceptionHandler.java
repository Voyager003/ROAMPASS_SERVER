package com.roampass_server.common.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.roampass_server.callback.application.InvalidSignatureException;
import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
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

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getCode(), e.getMessage());
    }

    // 사전 확인을 동시에 통과한 요청을 DB 유니크 제약이 막은 경우(예: 같은 ICCID 동시 등록), 트랜잭션은 롤백됨
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("event=data_integrity_violation cause=\"{}\"", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data");
    }

    @ExceptionHandler(InvalidSignatureException.class)
    ResponseEntity<ProblemDetail> handleInvalidSignature(InvalidSignatureException e) {
        return problem(HttpStatus.UNAUTHORIZED, InvalidSignatureException.CODE, e.getMessage());
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
        log.error("event=unhandled_exception type={}", e.getClass().getSimpleName(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    // Spring MVC 자체 예외(검증 실패, 본문 파싱 실패, 타입 불일치, 404, 405 등)에도 code 부여
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd : ProblemDetail.forStatus(statusCode);
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", frameworkCode(statusCode));
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static String frameworkCode(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return "INVALID_REQUEST";
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }

    /**
     * 업무 규칙으로 거절한 요청(4xx)은 코드와 함께 INFO로 남김. 요청 로그(http_request)는 상태 코드만 있어서
     * 왜 거절됐는지(예: ACTION_CONFLICT, PAYMENT_IN_PROGRESS)는 이 줄로 알 수 있음. 5xx는 위 핸들러가 ERROR로 남김
     */
    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        if (status.is4xxClientError()) {
            log.info("event=request_rejected status={} code={} detail=\"{}\"", status.value(), code, detail);
        }
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
