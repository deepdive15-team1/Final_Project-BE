package com.highpass.runspot.chat.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import com.highpass.runspot.session.domain.Session;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_rooms",
        indexes = @Index(name = "idx_chat_rooms_last_message_at", columnList = "last_message_at"),
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_chat_rooms_session_guest",
                        columnNames = {"session_id", "guest_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private ChatRoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id")
    private User host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private User guest;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public static ChatRoom direct(Session session, User guest) {
        ChatRoom room = new ChatRoom();
        room.roomType = ChatRoomType.DIRECT;
        room.session = session;
        room.host = session.getHostUser();
        room.guest = guest;
        room.title = session.getTitle();
        room.status = ChatRoomStatus.ACTIVE;
        return room;
    }

    public static ChatRoom group(Session session) {
        ChatRoom room = new ChatRoom();
        room.roomType = ChatRoomType.GROUP;
        room.session = session;
        room.host = session.getHostUser();
        room.title = session.getTitle();
        room.status = ChatRoomStatus.ACTIVE;
        return room;
    }

    public void delete() {
        status = ChatRoomStatus.DELETED;
    }

    public void updateLastMessage(Long messageId, LocalDateTime messageCreatedAt) {
        lastMessageId = messageId;
        lastMessageAt = messageCreatedAt;
    }
}
