package com.highpass.runspot.chat.controller;
import com.highpass.runspot.chat.config.StompPrincipal;import com.highpass.runspot.chat.dto.*;import com.highpass.runspot.chat.service.*;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.messaging.handler.annotation.MessageMapping;import org.springframework.messaging.simp.SimpMessagingTemplate;import org.springframework.stereotype.Controller;
@Controller @RequiredArgsConstructor
public class ChatStompController {private final ChatMessageService service;private final ChatReadService reads;
 /** PUB /pub/chat/message -> SUB /sub/chat/room/{roomId}. CONNECT Authorization: Bearer JWT. */
 @MessageMapping("/chat/message")public void send(@Valid ChatSendRequest request,StompPrincipal principal){service.send(principal.userId(),request);}
 /** PUB /pub/chat/read. */
 @MessageMapping("/chat/read")public void read(@Valid ChatStompReadRequest request,StompPrincipal principal){reads.read(principal.userId(),request.roomId(),request.lastReadMessageId());}
}
