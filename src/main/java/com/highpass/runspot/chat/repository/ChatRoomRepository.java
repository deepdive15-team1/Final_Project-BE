package com.highpass.runspot.chat.repository;

import com.highpass.runspot.chat.domain.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    @EntityGraph(attributePaths = {"session", "host", "guest"})
    Optional<ChatRoom> findBySessionIdAndGuestIdAndRoomType(
            Long sessionId, Long guestId, ChatRoomType type);

    @EntityGraph(attributePaths = {"session", "host", "guest"})
    Optional<ChatRoom> findBySessionIdAndRoomType(Long sessionId, ChatRoomType type);

    @EntityGraph(attributePaths = {"session", "host", "guest"})
    @Query("select r from ChatRoom r where r.id=:id")
    Optional<ChatRoom> findDetail(@Param("id") Long id);
}
