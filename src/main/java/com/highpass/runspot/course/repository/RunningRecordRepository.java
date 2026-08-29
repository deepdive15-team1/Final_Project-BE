package com.highpass.runspot.course.repository;

import com.highpass.runspot.course.domain.RunningRecord;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunningRecordRepository extends JpaRepository<RunningRecord, Long> {
    List<RunningRecord> findByUserIdOrderByRecordedAtDescIdDesc(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
