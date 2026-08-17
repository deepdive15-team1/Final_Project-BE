package com.highpass.runspot.auth.domain.dao;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.UserRunningStats;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRunningStatsRepository extends JpaRepository<UserRunningStats, Long> {

    void deleteByUserId(Long userId);

    Optional<UserRunningStats> findByUser(User user);

    Optional<UserRunningStats> findByUserId(Long userId);
}
