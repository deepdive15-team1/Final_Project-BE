package com.highpass.runspot.session.domain.dao;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.session.domain.AttendanceStatus;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SessionParticipant sp WHERE sp.id = :id")
    Optional<SessionParticipant> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            DELETE FROM SessionParticipant sp
            WHERE sp.user.id = :userId
               OR sp.session.id IN (
                   SELECT s.id FROM Session s WHERE s.hostUser.id = :userId
               )
            """)
    void deleteAllRelatedToUser(@Param("userId") Long userId);

    boolean existsBySessionAndUser(Session session, User user);

    List<SessionParticipant> findBySessionId(Long sessionId);

    List<SessionParticipant> findBySessionIdAndStatus(Long sessionId, ParticipationStatus status);

    @Query("SELECT sp FROM SessionParticipant sp JOIN FETCH sp.user WHERE sp.session.id = :sessionId AND sp.status = :status")
    List<SessionParticipant> findBySessionIdAndStatusWithUser(@Param("sessionId") Long sessionId, @Param("status") ParticipationStatus status);

    long countBySessionIdAndStatus(Long sessionId, ParticipationStatus status);

    // 신청한 러닝 목록 조회 (REQUESTED, APPROVED, REJECTED 상태만, 최신순)
    @Query("SELECT sp FROM SessionParticipant sp " +
            "JOIN FETCH sp.session s " +
            "JOIN FETCH sp.user u " +
            "WHERE sp.user.id = :userId " +
            "AND sp.status IN ('REQUESTED', 'APPROVED', 'REJECTED') " +
            "ORDER BY sp.createdAt DESC")
    List<SessionParticipant> findAppliedRunnings(@Param("userId") Long userId, Pageable pageable);

    // 최근 참여 내역 조회 (출석한 세션만, 최신순 3개)
    @Query("SELECT sp FROM SessionParticipant sp " +
            "JOIN FETCH sp.session s " +
            "JOIN FETCH sp.user u " +
            "WHERE sp.user.id = :userId " +
            "AND sp.status = 'APPROVED' " +
            "AND sp.attendanceStatus = 'ATTENDED' " +
            "ORDER BY s.startAt DESC")
    List<SessionParticipant> findRecentAttendedRunnings(@Param("userId") Long userId, Pageable pageable);

    // 출석 처리된 세션 개수
    long countByUserIdAndStatusAndAttendanceStatus(Long userId, ParticipationStatus status, AttendanceStatus attendanceStatus);
}
