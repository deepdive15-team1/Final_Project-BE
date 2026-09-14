package com.highpass.runspot.auth.service.dto.response;

import com.highpass.runspot.auth.domain.UserBlock;
import java.time.LocalDateTime;

public record BlockedUserResponse(
        Long userId,
        String name,
        LocalDateTime blockedAt
) {
    public static BlockedUserResponse from(UserBlock userBlock) {
        return new BlockedUserResponse(
                userBlock.getBlocked().getId(),
                userBlock.getBlocked().getName(),
                userBlock.getCreatedAt()
        );
    }
}
