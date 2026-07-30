package com.highpass.runspot.session.domain;

public enum SessionStatus {
    OPEN,
    CLOSED, //마감
    CANCELED, //취소
    IN_PROGRESS, //출석체크 완료 후 러닝 시작(진행중)
    FINISHED //러닝 종료
}
