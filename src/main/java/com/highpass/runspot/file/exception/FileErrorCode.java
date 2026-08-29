package com.highpass.runspot.file.exception;

import com.highpass.runspot.common.exception.BaseExceptionType;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum FileErrorCode implements BaseExceptionType {
    INVALID_FILE_COUNT(HttpStatus.BAD_REQUEST, "파일은 한 번에 최대 3개까지 요청할 수 있습니다."),
    INVALID_FILE_SIZE(HttpStatus.BAD_REQUEST, "파일 크기는 5MB 이하여야 합니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "JPEG, PNG, WEBP 이미지만 업로드할 수 있습니다."),
    INVALID_FILE_KEY(HttpStatus.BAD_REQUEST, "발급받지 않았거나 다른 사용자의 이미지 키입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
    private final HttpStatus status;
    private final String message;

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
