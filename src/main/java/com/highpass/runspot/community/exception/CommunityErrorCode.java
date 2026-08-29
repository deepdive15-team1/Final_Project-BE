package com.highpass.runspot.community.exception;

import com.highpass.runspot.common.exception.BaseExceptionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CommunityErrorCode implements BaseExceptionType {
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    NOT_POST_OWNER(HttpStatus.FORBIDDEN, "게시글 작성자만 수정하거나 삭제할 수 있습니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "이미지는 최대 3장까지 등록할 수 있습니다."),
    INVALID_COURSE_POST(HttpStatus.BAD_REQUEST, "코스 게시글에는 러닝 기록이 필요합니다."),
    INVALID_GENERAL_POST(HttpStatus.BAD_REQUEST, "일반 게시글에는 러닝 기록을 연결할 수 없습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서 형식이 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

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
