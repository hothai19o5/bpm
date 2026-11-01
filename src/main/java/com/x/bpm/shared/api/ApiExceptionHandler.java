package com.x.bpm.shared.api;

import com.x.bpm.shared.adapter.web.CorrelationIdFilter;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.shared.error.ValidationIssue;
import jakarta.persistence.OptimisticLockException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Chuẩn hóa lỗi MVC; không trả thông báo gốc của SQL/engine cho client. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiResponseFactory responses;

    public ApiExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplication(ApplicationException exception, WebRequest request) {
        return buildError(exception.code(), exception.getMessage(), exception.details(),
                new HttpHeaders(), ApiErrorMapping.statusFor(exception.code()), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var servletRequest = (ServletWebRequest) request;
        if (servletRequest.getResponse() != null && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        var issues = new ArrayList<ValidationIssue>();
        var responseStatus = status;
        if (exception instanceof MethodArgumentNotValidException invalid) {
            responseStatus = HttpStatus.UNPROCESSABLE_CONTENT;
            invalid.getBindingResult().getAllErrors().forEach(error -> issues.add(new ValidationIssue(
                    error instanceof FieldError fieldError ? fieldError.getField() : null,
                    validationMessage(error.getDefaultMessage()), null)));
        } else if (exception instanceof HandlerMethodValidationException invalid
                && !invalid.isForReturnValue()) {
            responseStatus = HttpStatus.UNPROCESSABLE_CONTENT;
            invalid.getParameterValidationResults().forEach(result -> {
                if (result instanceof ParameterErrors errors) {
                    errors.getAllErrors().forEach(error -> issues.add(new ValidationIssue(
                            error instanceof FieldError fieldError ? fieldError.getField() : null,
                            validationMessage(error.getDefaultMessage()), null)));
                } else {
                    var parameter = result.getMethodParameter();
                    var field = parameter.getParameterName() != null ? parameter.getParameterName()
                            : "arg" + parameter.getParameterIndex();
                    result.getResolvableErrors().forEach(error -> issues.add(new ValidationIssue(
                            field, validationMessage(error.getDefaultMessage()), null)));
                }
            });
            invalid.getCrossParameterValidationResults().forEach(error -> issues.add(
                    new ValidationIssue(null, validationMessage(error.getDefaultMessage()), null)));
        }
        if (responseStatus.is5xxServerError()) {
            logServerError(exception, request);
        }
        return buildError(ApiErrorMapping.codeFor(responseStatus), ApiErrorMapping.messageFor(responseStatus),
                issues, headers, responseStatus, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException exception, WebRequest request) {
        var headers = new HttpHeaders();
        headers.set("WWW-Authenticate", "Bearer");
        return buildError(ErrorCode.UNAUTHENTICATED, ApiErrorMapping.messageFor(HttpStatus.UNAUTHORIZED),
                List.of(), headers, HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception, WebRequest request) {
        return buildError(ErrorCode.FORBIDDEN, ApiErrorMapping.messageFor(HttpStatus.FORBIDDEN),
                List.of(), new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Object> handleRevisionConflict(Exception exception, WebRequest request) {
        return buildError(ErrorCode.REVISION_CONFLICT,
                "Dữ liệu đã thay đổi. Vui lòng tải lại trước khi lưu.", List.of(),
                new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        var status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (exception instanceof ErrorResponse error && error.getStatusCode().isError()) {
            var errorStatus = error.getStatusCode();
            if (errorStatus.is5xxServerError()) {
                logServerError(exception, request);
            }
            return buildError(ApiErrorMapping.codeFor(errorStatus), ApiErrorMapping.messageFor(errorStatus),
                    List.of(), error.getHeaders(), errorStatus, request);
        }
        var annotation = AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
        if (annotation != null && annotation.code().isError()) {
            status = annotation.code();
        }
        if (status.is5xxServerError()) {
            logServerError(exception, request);
        }
        return buildError(ApiErrorMapping.codeFor(status), ApiErrorMapping.messageFor(status),
                List.of(), new HttpHeaders(), status, request);
    }

    private ResponseEntity<Object> buildError(ErrorCode code, String safeMessage,
            List<ValidationIssue> details, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var servletRequest = ((ServletWebRequest) request).getRequest();
        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setCacheControl("no-store");
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(responses.error(code, safeMessage, details, status, servletRequest),
                responseHeaders, status);
    }

    private String validationMessage(String message) {
        return message == null || message.isBlank() ? "Giá trị không hợp lệ." : message;
    }

    private void logServerError(Exception exception, WebRequest request) {
        var httpRequest = ((ServletWebRequest) request).getRequest();
        log.error("API error correlationId={}", CorrelationIdFilter.getOrCreate(httpRequest), exception);
    }
}
