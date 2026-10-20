package com.roampass.mock.supplier.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.roampass.mock.supplier.issuance.MockSupplierException;

/**
 * 에러 응답을 roampass-api와 같은 형식(ProblemDetail + code)으로 통일
 */
@RestControllerAdvice
public class MockSupplierExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(MockSupplierException.class)
    ResponseEntity<ProblemDetail> handleMockSupplier(MockSupplierException e) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        body.setProperty("code", e.getCode());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd : ProblemDetail.forStatus(statusCode);
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", statusCode.value() == 400 ? "INVALID_REQUEST"
                    : status != null ? status.name() : "HTTP_" + statusCode.value());
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }
}
