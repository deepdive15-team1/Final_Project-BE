package com.highpass.runspot.notification.push.gateway;

import java.util.List;

public interface PushMessagingGateway {

    List<PushSendResult> send(List<PushMessage> messages);
}
