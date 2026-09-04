package com.highpass.runspot.notification.push.gateway.firebase;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.highpass.runspot.notification.push.gateway.PushSendResult;
import org.springframework.stereotype.Component;

@Component
final class FirebaseErrorClassifier {

    PushSendResult classify(long notificationId, FirebaseMessagingException exception) {
        return exception == null
                ? classifyErrorCode(notificationId, null)
                : classifyErrorCode(notificationId, exception.getMessagingErrorCode());
    }

    PushSendResult classifyErrorCode(long notificationId, MessagingErrorCode errorCode) {
        if (errorCode == null) {
            return PushSendResult.retryable(notificationId, null);
        }
        return switch (errorCode) {
            case UNREGISTERED -> PushSendResult.terminal(notificationId, errorCode.name(), true);
            case INVALID_ARGUMENT, SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR ->
                    PushSendResult.terminal(notificationId, errorCode.name(), false);
            case QUOTA_EXCEEDED, UNAVAILABLE, INTERNAL -> PushSendResult.retryable(notificationId, errorCode.name());
        };
    }

    PushSendResult batchFailure(long notificationId) {
        return PushSendResult.retryable(notificationId, "BATCH_FAILURE");
    }
}
