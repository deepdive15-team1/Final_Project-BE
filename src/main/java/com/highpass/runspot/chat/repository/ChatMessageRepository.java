package com.highpass.runspot.chat.repository;
import com.highpass.runspot.chat.domain.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ChatMessageRepository extends JpaRepository<ChatMessage,Long>{
 @EntityGraph(attributePaths={"sender"}) @Query("select m from ChatMessage m where m.room.id=:roomId and (:cursor is null or m.id<:cursor) order by m.id desc") List<ChatMessage> findPage(@Param("roomId")Long roomId,@Param("cursor")Long cursor,Pageable pageable);
 long countByRoomIdAndIdGreaterThan(Long roomId,Long id);long countByRoomId(Long roomId);Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);
 Optional<ChatMessage> findByRoomIdAndClientMessageId(Long roomId,String clientMessageId);
}
