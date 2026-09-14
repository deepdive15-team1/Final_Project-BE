package com.highpass.runspot.chat.service.dto.response;

import com.highpass.runspot.chat.domain.*;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long roomId,
        Long hostId,
        ChatRoomType roomType,
        String title,
        Long sessionId,
        int memberCount,
        String notice,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount) {}
