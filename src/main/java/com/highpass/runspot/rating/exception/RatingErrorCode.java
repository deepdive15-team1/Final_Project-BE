package com.highpass.runspot.rating.exception;

import com.highpass.runspot.common.exception.BaseExceptionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum RatingErrorCode implements BaseExceptionType {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    SESSION_NOT_FINISHED(HttpStatus.BAD_REQUEST, "종료된 세션만 평가할 수 있습니다."),
    RATER_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "해당 세션에 출석한 참여자만 평가할 수 있습니다."),
    ALREADY_RATED_HOST(HttpStatus.CONFLICT, "이미 호스트 평가를 완료했습니다."),
    ALREADY_RATED_MEMBER(HttpStatus.CONFLICT, "이미 평가한 멤버가 포함되어 있습니다."),
    SELF_RATING_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신은 평가할 수 없습니다."),
    HOST_NOT_MEMBER_TARGET(HttpStatus.BAD_REQUEST, "호스트는 멤버 평가 대상이 아닙니다. 호스트 평가 API를 이용해주세요."),
    TARGET_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "평가 대상이 해당 세션의 출석 참여자가 아닙니다."),
    TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

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
