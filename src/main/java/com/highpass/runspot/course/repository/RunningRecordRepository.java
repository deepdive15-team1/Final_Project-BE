package com.highpass.runspot.course.repository;
import com.highpass.runspot.course.domain.RunningRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RunningRecordRepository extends JpaRepository<RunningRecord,Long>{List<RunningRecord> findByUserIdOrderByRecordedAtDescIdDesc(Long userId);boolean existsByIdAndUserId(Long id,Long userId);}
