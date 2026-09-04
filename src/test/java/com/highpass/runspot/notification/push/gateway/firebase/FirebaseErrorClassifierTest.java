package com.highpass.runspot.notification.push.gateway.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.MessagingErrorCode;
import com.highpass.runspot.notification.push.gateway.PushFailureKind;
import com.highpass.runspot.notification.push.gateway.PushSendResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class FirebaseErrorClassifierTest {

    private static final long NOTIFICATION_ID = 41L;
    private final FirebaseErrorClassifier classifier = new FirebaseErrorClassifier();

    @ParameterizedTest
    @EnumSource(MessagingErrorCode.class)
    void classifiesEveryFirebaseErrorCodeWithTheExactTokenPolicy(MessagingErrorCode errorCode) {
        PushSendResult result = classifier.classifyErrorCode(NOTIFICATION_ID, errorCode);

        switch (errorCode) {
            case UNREGISTERED -> assertThat(result).isEqualTo(PushSendResult.terminal(NOTIFICATION_ID, "UNREGISTERED", true));
            case INVALID_ARGUMENT, SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR ->
                    assertThat(result).isEqualTo(PushSendResult.terminal(NOTIFICATION_ID, errorCode.name(), false));
            case QUOTA_EXCEEDED, UNAVAILABLE, INTERNAL ->
                    assertThat(result).isEqualTo(PushSendResult.retryable(NOTIFICATION_ID, errorCode.name()));
        }
    }

    @Test
    void classifiesNullAndBatchFailuresAsRetryableWithoutDeletingTheToken() {
        assertThat(classifier.classifyErrorCode(NOTIFICATION_ID, null))
                .isEqualTo(PushSendResult.retryable(NOTIFICATION_ID, null));
        assertThat(classifier.batchFailure(NOTIFICATION_ID))
                .isEqualTo(PushSendResult.retryable(NOTIFICATION_ID, "BATCH_FAILURE"));
    }
}
