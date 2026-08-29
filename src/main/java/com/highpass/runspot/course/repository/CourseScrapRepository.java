package com.highpass.runspot.course.repository;

import com.highpass.runspot.course.domain.CourseScrap;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseScrapRepository extends JpaRepository<CourseScrap, Long> {
    boolean existsByRunningRecordIdAndUserId(Long recordId, Long userId);

    @EntityGraph(attributePaths = {"runningRecord", "runningRecord.user"})
    List<CourseScrap> findByUserIdOrderByIdDesc(Long userId);
}
