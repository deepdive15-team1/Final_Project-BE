package com.highpass.runspot.session.domain.dao;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM Session s WHERE s.hostUser.id = :userId")
    void deleteByHostUserId(@Param("userId") Long userId);

    @Query("""
        SELECT s FROM Session s
        WHERE s.hostUser = :hostUser
        ORDER BY
            CASE s.status
                WHEN 'OPEN' THEN 1
                WHEN 'CLOSED' THEN 2
                WHEN 'IN_PROGRESS' THEN 3
                WHEN 'FINISHED' THEN 4
            END ASC,
            s.createdAt DESC
        """)
    List<Session> findByHostUserOrderByStatusAscCreatedAtDesc(@Param("hostUser") User hostUser);
           
    List<Session> findByStatusAndTitleContainingAndIdLessThanOrderByIdDesc(SessionStatus sessionStatus, String title, Long id, Pageable pageable);

    List<Session> findByStatusAndTitleContainingOrderByIdDesc(SessionStatus sessionStatus, String title, Pageable pageable);

    @Query("""
            SELECT s FROM Session s
            WHERE s.status = 'OPEN'
            AND within(s.location, :area) = true
            ORDER BY function('ST_Distance_Sphere', s.location, :point)
            """)
    List<Session> findNearestSessions(@Param("point") Point point, @Param("area") Geometry area, Pageable pageable);

    @Query("""
            SELECT s FROM Session s
            WHERE s.status = 'OPEN'
            AND within(s.location, :area) = true
            """)
    List<Session> findWithinArea(@Param("area") Geometry area);
}
