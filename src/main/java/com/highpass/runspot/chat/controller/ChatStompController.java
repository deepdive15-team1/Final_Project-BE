package com.highpass.runspot.chat.controller;
import com.highpass.runspot.chat.config.StompPrincipal;import com.highpass.runspot.chat.dto.*;import com.highpass.runspot.chat.service.ChatMessageService;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.messaging.handler.annotation.MessageMapping;import org.springframework.messaging.simp.SimpMessagingTemplate;import org.springframework.stereotype.Controller;
@Controller @RequiredArgsConstructor
public class ChatStompController {private final ChatMessageService service;private final SimpMessagingTemplate messaging;
 /** PUB /pub/chat/message -> SUB /sub/chat/room/{roomId}. CONNECT Authorization: Bearer JWT. */
 @MessageMapping("/chat/message")public void send(@Valid ChatSendRequest request,StompPrincipal principal){ChatSendResponse response=service.send(principal.userId(),request);messaging.convertAndSend("/sub/chat/room/"+request.roomId(),response);}}
