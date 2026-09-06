package com.highpass.runspot.notification.push.gateway.firebase;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.SendResponse;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import com.highpass.runspot.notification.push.gateway.PushMessagingGateway;
import com.highpass.runspot.notification.push.gateway.PushSendResult;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(FirebaseMessaging.class)
public class FirebasePushMessagingGateway implements PushMessagingGateway {

    private static final int MAX_BATCH_SIZE = 100;

    private final FirebaseMessaging firebaseMessaging;
    private final AndroidPushMessageMapper messageMapper;
    private final FirebaseErrorClassifier errorClassifier;

    FirebasePushMessagingGateway(
            FirebaseMessaging firebaseMessaging,
            AndroidPushMessageMapper messageMapper,
            FirebaseErrorClassifier errorClassifier
    ) {
        this.firebaseMessaging = Objects.requireNonNull(firebaseMessaging);
        this.messageMapper = Objects.requireNonNull(messageMapper);
        this.errorClassifier = Objects.requireNonNull(errorClassifier);
    }

    @Override
    public List<PushSendResult> send(List<PushMessage> messages) {
        validateBatch(messages);
        List<PushMessage> batch = List.copyOf(messages);
        List<Message> firebaseMessages = batch.stream().map(messageMapper::map).toList();
        try {
            return toResults(batch, firebaseMessaging.sendEach(firebaseMessages));
        } catch (FirebaseMessagingException exception) {
            return batch.stream().map(message -> errorClassifier.batchFailure(message.notificationId())).toList();
        }
    }

    private void validateBatch(List<PushMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("FCM 배치는 1개 이상 100개 이하여야 합니다.");
        }
        if (messages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("FCM 배치에 null 메시지가 포함될 수 없습니다.");
        }
    }

    private List<PushSendResult> toResults(List<PushMessage> messages, BatchResponse batchResponse) {
        if (batchResponse == null) {
            return messages.stream().map(message -> errorClassifier.batchFailure(message.notificationId())).toList();
        }
        List<SendResponse> responses = batchResponse.getResponses();
        if (responses == null || responses.size() != messages.size()) {
            return messages.stream().map(message -> errorClassifier.batchFailure(message.notificationId())).toList();
        }
        return java.util.stream.IntStream.range(0, messages.size())
                .mapToObj(index -> toResult(messages.get(index), responses.get(index)))
                .toList();
    }

    private PushSendResult toResult(PushMessage message, SendResponse response) {
        if (response == null) {
            return errorClassifier.classify(message.notificationId(), null);
        }
        if (response.isSuccessful()) {
            return PushSendResult.success(message.notificationId());
        }
        return errorClassifier.classify(message.notificationId(), response.getException());
    }
}
