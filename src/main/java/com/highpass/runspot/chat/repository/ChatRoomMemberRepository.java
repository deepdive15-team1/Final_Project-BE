package com.highpass.runspot.chat.repository;

import com.highpass.runspot.chat.domain.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    @EntityGraph(attributePaths = {"room", "room.session", "room.host", "room.guest"})
    List<ChatRoomMember> findByUserIdAndLeftAtIsNullOrderByRoomLastMessageAtDesc(Long userId);

    Optional<ChatRoomMember> findByRoomIdAndUserIdAndLeftAtIsNull(Long roomId, Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<ChatRoomMember> findByRoomIdAndLeftAtIsNull(Long roomId);

    boolean existsByRoomIdAndUserIdAndLeftAtIsNull(Long roomId, Long userId);

    @Query(
            "select m.room.id as roomId,count(m.id) as memberCount from ChatRoomMember m where"
                + " m.leftAt is null and m.room.id in :roomIds group by m.room.id")
    List<RoomMemberCount> countActiveByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    @Modifying(clearAutomatically = true)
    @Query(
            "update ChatRoomMember m set m.lastReadMessageId=:messageId where m.id=:id and"
                + " (m.lastReadMessageId is null or m.lastReadMessageId<:messageId)")
    void updateLastReadMessageId(@Param("id") Long id, @Param("messageId") Long messageId);
}
