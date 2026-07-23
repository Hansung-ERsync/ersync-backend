package com.hansungteam.ersync.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 컨트롤러에서 발생한 예외를 프로젝트 표준 ErrorResponse로 변환합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode,
            List<FieldErrorResponse> fieldErrors
    ) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode, fieldErrors));
    }

    private ResponseEntity<ErrorResponse> toLoggedResponse(
            ApiErrorEvent event,
            ErrorCode errorCode,
            HttpServletRequest request,
            Exception ex,
            List<FieldErrorResponse> fieldErrors
    ) {
        ApiErrorLogSupport.log(log, event, errorCode, request, ex);
        return toResponse(errorCode, fieldErrors);
    }

    private ResponseEntity<ErrorResponse> toLoggedResponse(
            ApiErrorEvent event,
            ErrorCode errorCode,
            HttpServletRequest request,
            Exception ex
    ) {
        return toLoggedResponse(event, errorCode, request, ex, List.of());
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(
            CustomException ex,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ex.getErrorCode();
        ApiErrorEvent event = errorCode.getStatus().is5xxServerError()
                ? ApiErrorEvent.SYSTEM_ERROR
                : ApiErrorEvent.BUSINESS_ERROR;
        return toLoggedResponse(event, errorCode, request, ex);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException ex,
            HttpServletRequest request
    ) {
        return toLoggedResponse(
                ApiErrorEvent.VALIDATION_ERROR,
                ErrorCode.COMMON_REQUEST_VALIDATION_FAILED,
                request,
                ex,
                fieldErrorsFrom(ex.getBindingResult())
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()
                ))
                .toList();

        return toLoggedResponse(
                ApiErrorEvent.VALIDATION_ERROR,
                ErrorCode.COMMON_REQUEST_VALIDATION_FAILED,
                request,
                ex,
                fieldErrors
        );
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        return toLoggedResponse(
                ApiErrorEvent.VALIDATION_ERROR,
                ErrorCode.COMMON_REQUEST_VALIDATION_FAILED,
                request,
                ex
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return toLoggedResponse(
                ApiErrorEvent.AUTH_ERROR,
                ErrorCode.AUTH_ROLE_REQUIRED,
                request,
                ex
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return toLoggedResponse(
                ApiErrorEvent.BUSINESS_ERROR,
                ErrorCode.COMMON_HTTP_METHOD_NOT_ALLOWED,
                request,
                ex
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return toLoggedResponse(
                ApiErrorEvent.BUSINESS_ERROR,
                ErrorCode.COMMON_RESOURCE_NOT_FOUND,
                request,
                ex
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        return toLoggedResponse(
                ApiErrorEvent.SYSTEM_ERROR,
                ErrorCode.COMMON_INTERNAL_SERVER_ERROR,
                request,
                ex
        );
    }

    private List<FieldErrorResponse> fieldErrorsFrom(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fieldError -> new FieldErrorResponse(
                        fieldError.getField(),
                        fieldError.getCode() == null ? "INVALID" : fieldError.getCode(),
                        fieldError.getDefaultMessage() == null
                                ? "올바른 값을 입력해 주세요."
                                : fieldError.getDefaultMessage()
                ))
                .toList();
    }
}
