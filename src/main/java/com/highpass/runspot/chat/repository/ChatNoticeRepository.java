package com.highpass.runspot.chat.repository;

import com.highpass.runspot.chat.domain.ChatNotice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ChatNoticeRepository extends JpaRepository<ChatNotice, Long> {
    Optional<ChatNotice> findByRoomIdAndActiveTrue(Long roomId);

    List<ChatNotice> findByRoomIdInAndActiveTrue(Collection<Long> roomIds);
}
