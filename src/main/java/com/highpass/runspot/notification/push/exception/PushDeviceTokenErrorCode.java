package com.highpass.runspot.notification.push.exception;

import com.highpass.runspot.common.exception.BaseExceptionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum PushDeviceTokenErrorCode implements BaseExceptionType {

    TOKEN_REGISTRATION_CONFLICT(HttpStatus.CONFLICT, "푸시 토큰 등록이 동시에 처리되었습니다. 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
