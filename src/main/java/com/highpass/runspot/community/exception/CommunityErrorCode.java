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
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요한 게시글입니다."),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "좋아요 내역을 찾을 수 없습니다."),
    ALREADY_SCRAPPED(HttpStatus.CONFLICT, "이미 저장한 게시글입니다."),
    SCRAP_NOT_FOUND(HttpStatus.NOT_FOUND, "저장 내역을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    NOT_COMMENT_OWNER(HttpStatus.FORBIDDEN, "댓글 작성자만 삭제할 수 있습니다."),
    NESTED_REPLY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "대댓글에는 답글을 작성할 수 없습니다."),
    PARENT_COMMENT_MISMATCH(HttpStatus.BAD_REQUEST, "같은 게시글의 댓글에만 답글을 작성할 수 있습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상입니다.");

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
