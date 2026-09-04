package com.highpass.runspot.notification.push.domain.dao;

import com.highpass.runspot.notification.push.domain.PushDeviceToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {

    Optional<PushDeviceToken> findByUserId(Long userId);

    Optional<PushDeviceToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pushDeviceToken from PushDeviceToken pushDeviceToken where pushDeviceToken.userId = :userId")
    Optional<PushDeviceToken> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pushDeviceToken from PushDeviceToken pushDeviceToken where pushDeviceToken.token = :token")
    Optional<PushDeviceToken> findByTokenForUpdate(@Param("token") String token);
}
