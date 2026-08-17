package com.highpass.runspot.rating.domain.dao;

import com.highpass.runspot.rating.domain.Rating;
import com.highpass.runspot.rating.domain.RatingTargetType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    @Modifying
    @Query("""
            DELETE FROM Rating r
            WHERE r.rater.id = :userId
               OR r.target.id = :userId
               OR r.session.id IN (
                   SELECT s.id FROM Session s WHERE s.hostUser.id = :userId
               )
            """)
    void deleteAllRelatedToUser(@Param("userId") Long userId);

    boolean existsBySessionIdAndRaterIdAndTargetId(Long sessionId, Long raterId, Long targetId);

    @Query("SELECT r.target.id FROM Rating r WHERE r.session.id = :sessionId AND r.rater.id = :raterId")
    List<Long> findTargetIdsBySessionIdAndRaterId(@Param("sessionId") Long sessionId, @Param("raterId") Long raterId);

    long countBySessionIdAndRaterIdAndTargetType(Long sessionId, Long raterId, RatingTargetType targetType);
}
