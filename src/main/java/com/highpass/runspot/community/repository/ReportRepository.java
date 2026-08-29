package com.highpass.runspot.community.repository;
import com.highpass.runspot.community.domain.Report;
import com.highpass.runspot.community.domain.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportRepository extends JpaRepository<Report,Long>{boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType,Long targetId);}
