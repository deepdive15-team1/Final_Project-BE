package com.highpass.runspot.notification.push.gateway;

import java.util.Objects;

public record PushSendResult(long notificationId, PushFailureKind failureKind, String errorCode, boolean deleteToken) {

    public PushSendResult {
        if (notificationId <= 0) {
            throw new IllegalArgumentException("알림 ID는 양수여야 합니다.");
        }
        Objects.requireNonNull(failureKind, "failureKind must not be null");
        if (failureKind == PushFailureKind.SUCCESS && (errorCode != null || deleteToken)) {
            throw new IllegalArgumentException("성공 결과에는 오류 코드나 토큰 삭제 요청이 있을 수 없습니다.");
        }
        if (deleteToken && failureKind != PushFailureKind.TERMINAL) {
            throw new IllegalArgumentException("토큰 삭제는 종료 실패에서만 요청할 수 있습니다.");
        }
    }

    public static PushSendResult success(long notificationId) {
        return new PushSendResult(notificationId, PushFailureKind.SUCCESS, null, false);
    }

    public static PushSendResult terminal(long notificationId, String errorCode, boolean deleteToken) {
        return new PushSendResult(notificationId, PushFailureKind.TERMINAL, Objects.requireNonNull(errorCode), deleteToken);
    }

    public static PushSendResult retryable(long notificationId, String errorCode) {
        return new PushSendResult(notificationId, PushFailureKind.RETRYABLE, errorCode, false);
    }
}
