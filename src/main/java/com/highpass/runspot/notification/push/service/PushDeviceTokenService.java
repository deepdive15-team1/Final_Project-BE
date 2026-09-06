package com.highpass.runspot.notification.push.service;

import com.highpass.runspot.notification.push.domain.PushDeviceToken;
import com.highpass.runspot.notification.push.domain.PushPlatform;
import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.exception.PushDeviceTokenErrorCode;
import com.highpass.runspot.notification.push.exception.PushDeviceTokenException;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeviceTokenService {

    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushOutboxRepository pushOutboxRepository;

    @Transactional
    public void upsert(Long userId, String token, PushPlatform platform) {
        try {
            PushDeviceToken callerToken = pushDeviceTokenRepository.findByUserIdForUpdate(userId).orElse(null);
            PushDeviceToken tokenOwner = pushDeviceTokenRepository.findByTokenForUpdate(token).orElse(null);

            if (tokenOwner != null && tokenOwner.getUserId().equals(userId)) {
                return;
            }

            if (tokenOwner != null) {
                pushDeviceTokenRepository.delete(tokenOwner);
                pushDeviceTokenRepository.flush();
            }

            if (callerToken == null) {
                pushDeviceTokenRepository.save(PushDeviceToken.builder()
                        .userId(userId)
                        .token(token)
                        .platform(platform)
                        .build());
            } else {
                callerToken.replace(token, platform);
            }
            pushDeviceTokenRepository.flush();
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            throw new PushDeviceTokenException(PushDeviceTokenErrorCode.TOKEN_REGISTRATION_CONFLICT);
        }
    }

    /**
     * Revokes local delivery state; a send already issued to the provider cannot be recalled.
     */
    @Transactional
    public void delete(Long userId) {
        pushOutboxRepository.deleteUnsentByRecipientUserId(userId);
        pushDeviceTokenRepository.findByUserIdForUpdate(userId)
                .ifPresent(token -> {
                    pushDeviceTokenRepository.delete(token);
                    pushDeviceTokenRepository.flush();
                });
    }
}
