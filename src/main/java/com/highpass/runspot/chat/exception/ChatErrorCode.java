package com.highpass.runspot.chat.exception;

import com.highpass.runspot.common.exception.BaseExceptionType;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum ChatErrorCode implements BaseExceptionType {
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "채팅방 멤버만 접근할 수 있습니다."),
    NOT_ROOM_HOST(HttpStatus.FORBIDDEN, "채팅방 호스트만 수행할 수 있습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    HOST_DIRECT_ROOM_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "호스트는 자신에게 1:1 문의방을 만들 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CHAT_ROOM_CLOSED(HttpStatus.GONE, "종료된 채팅방에는 메시지를 보낼 수 없습니다."),
    INVALID_CHAT_MESSAGE(HttpStatus.BAD_REQUEST, "메시지 유형에 맞는 내용을 입력해주세요.");

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
