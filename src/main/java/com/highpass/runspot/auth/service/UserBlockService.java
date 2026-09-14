package com.highpass.runspot.auth.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.UserBlock;
import com.highpass.runspot.auth.domain.dao.UserBlockRepository;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.exception.UserErrorCode;
import com.highpass.runspot.auth.exception.UserException;
import com.highpass.runspot.auth.service.dto.response.BlockedUserResponse;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {

    private final UserRepository users;
    private final UserBlockRepository userBlocks;

    @Transactional
    public void block(Long blockerId, Long blockedUserId) {
        if (Objects.equals(blockerId, blockedUserId)) {
            throw new UserException(UserErrorCode.SELF_BLOCK_NOT_ALLOWED);
        }
        if (userBlocks.existsByBlockerIdAndBlockedId(blockerId, blockedUserId)) {
            throw new UserException(UserErrorCode.ALREADY_BLOCKED);
        }
        User blocker = users.findById(blockerId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        User blocked = users.findById(blockedUserId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        userBlocks.save(UserBlock.create(blocker, blocked));
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedUserId) {
        UserBlock userBlock = userBlocks.findByBlockerIdAndBlockedId(blockerId, blockedUserId)
                .orElseThrow(() -> new UserException(UserErrorCode.BLOCK_NOT_FOUND));
        userBlocks.delete(userBlock);
    }

    public List<BlockedUserResponse> getBlockedUsers(Long blockerId) {
        return userBlocks.findByBlockerId(blockerId).stream()
                .map(BlockedUserResponse::from)
                .toList();
    }
}
