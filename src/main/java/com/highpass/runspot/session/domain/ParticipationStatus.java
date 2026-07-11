package com.highpass.runspot.session.domain;

public enum ParticipationStatus {
    REQUESTED, //대기중
    APPROVED, //승인
    REJECTED, //거절
    CANCELED, //취소
    KICKED //강퇴
}