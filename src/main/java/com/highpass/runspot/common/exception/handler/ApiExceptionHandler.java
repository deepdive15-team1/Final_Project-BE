package com.highpass.runspot.common.exception.handler;

import com.highpass.runspot.common.exception.BaseException;
import com.highpass.runspot.common.exception.dto.ErrorResponse;
import io.sentry.Sentry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(final BaseException e) {
        log.error("BaseException: {}", e.getExceptionType().getMessage());
        return ErrorResponse.toResponseEntity(e.getExceptionType());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(final IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage());
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidation(final BindException e) {
        final String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("ValidationException: {}", errorMessage);
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(final IllegalStateException e) {
        log.error("IllegalStateException: {}", e.getMessage());

        // 로그인 필요 메시지는 401로 내려주기
        if ("로그인이 필요합니다.".equals(e.getMessage())) {
            return ErrorResponse.of(HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        // 그 외 IllegalStateException은 상황에 따라 400 또는 500 (일단 400 권장)
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 위에서 예상하지 못한 예외는 500으로 응답하고 Sentry로 즉시 보고한다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(final Exception e) {
        log.error("Unexpected exception", e);
        Sentry.captureException(e);
        return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }
}