package com.highpass.runspot.notification.push.gateway.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.push.gateway.PushFailureKind;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import com.highpass.runspot.notification.push.gateway.PushSendResult;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FirebasePushMessagingGatewayTest {

    @Test
    void rejectsEmptyAndOversizedBatchesBeforeCallingFirebase() {
        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        FirebasePushMessagingGateway gateway = gateway(firebaseMessaging);

        assertThatThrownBy(() -> gateway.send(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.send(IntStream.range(0, 101)
                .mapToObj(index -> pushMessage(index + 1L))
                .toList()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void sendsEachMessageAndReturnsOrderedTypedResults() throws Exception {
        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        BatchResponse batchResponse = mock(BatchResponse.class);
        List<SendResponse> responses = List.of(successfulResponse(), failedResponse(MessagingErrorCode.UNREGISTERED));
        when(batchResponse.getResponses()).thenReturn(responses);
        when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);
        FirebasePushMessagingGateway gateway = gateway(firebaseMessaging);

        List<PushSendResult> results = gateway.send(List.of(pushMessage(41L), pushMessage(42L)));

        assertThat(results).containsExactly(
                PushSendResult.success(41L),
                PushSendResult.terminal(42L, "UNREGISTERED", true)
        );
        verify(firebaseMessaging).sendEach(anyList());
        verifyNoMoreInteractions(firebaseMessaging);
    }

    @Test
    void convertsThrownBatchFailuresIntoOrderedRetryableResults() throws Exception {
        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException batchFailure = mock(FirebaseMessagingException.class);
        when(firebaseMessaging.sendEach(anyList())).thenThrow(batchFailure);
        FirebasePushMessagingGateway gateway = gateway(firebaseMessaging);

        List<PushSendResult> results = gateway.send(List.of(pushMessage(41L), pushMessage(42L)));

        assertThat(results).containsExactly(
                PushSendResult.retryable(41L, "BATCH_FAILURE"),
                PushSendResult.retryable(42L, "BATCH_FAILURE")
        );
    }

    @Test
    void convertsMismatchedBatchResponsesIntoRetryableResults() throws Exception {
        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        BatchResponse batchResponse = mock(BatchResponse.class);
        List<SendResponse> responses = List.of(successfulResponse());
        when(batchResponse.getResponses()).thenReturn(responses);
        when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);
        FirebasePushMessagingGateway gateway = gateway(firebaseMessaging);

        List<PushSendResult> results = gateway.send(List.of(pushMessage(41L), pushMessage(42L)));

        assertThat(results).allSatisfy(result -> {
            assertThat(result.failureKind()).isEqualTo(PushFailureKind.RETRYABLE);
            assertThat(result.deleteToken()).isFalse();
        });
    }

    @Test
    void doesNotHideUnexpectedRuntimeFailuresFromResponseProcessing() throws Exception {
        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        BatchResponse batchResponse = mock(BatchResponse.class);
        IllegalStateException unexpectedFailure = new IllegalStateException("unexpected response state");
        when(batchResponse.getResponses()).thenThrow(unexpectedFailure);
        when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);
        FirebasePushMessagingGateway gateway = gateway(firebaseMessaging);

        assertThatThrownBy(() -> gateway.send(List.of(pushMessage(41L))))
                .isSameAs(unexpectedFailure);
    }

    private FirebasePushMessagingGateway gateway(FirebaseMessaging firebaseMessaging) {
        return new FirebasePushMessagingGateway(
                firebaseMessaging,
                new AndroidPushMessageMapper(),
                new FirebaseErrorClassifier()
        );
    }

    private SendResponse successfulResponse() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private SendResponse failedResponse(MessagingErrorCode errorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(errorCode);
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getException()).thenReturn(exception);
        return response;
    }

    private PushMessage pushMessage(long notificationId) {
        return new PushMessage(
                notificationId,
                "test-device-token",
                NotificationType.PARTICIPATION_REQUESTED,
                "Persisted title",
                "Persisted body",
                7L,
                19L,
                NotificationActionType.APPROVE_OR_REJECT,
                NotificationActionStatus.PENDING
        );
    }
}
