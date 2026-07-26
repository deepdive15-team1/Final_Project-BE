package com.highpass.runspot.session.exception;

import com.highpass.runspot.common.exception.BaseExceptionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum SessionErrorCode implements BaseExceptionType {

    INVALID_SEARCH_QUERY(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "참여 정보를 찾을 수 없습니다."),
    KICK_NOT_HOST(HttpStatus.FORBIDDEN, "호스트만 참여자를 내보낼 수 있습니다."),
    KICK_INVALID_STATUS(HttpStatus.BAD_REQUEST, "러닝이 시작된 세션에서만 참여자를 내보낼 수 있습니다."),
    PARTICIPANT_SESSION_MISMATCH(HttpStatus.BAD_REQUEST, "해당 세션의 참여자가 아닙니다.");

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
