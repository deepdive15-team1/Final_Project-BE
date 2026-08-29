package com.highpass.runspot.chat.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_room_members",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_chat_room_members_room_user",
                        columnNames = {"room_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMemberRole role;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    public static ChatRoomMember join(ChatRoom room, User user, ChatMemberRole role) {
        ChatRoomMember member = new ChatRoomMember();
        member.room = room;
        member.user = user;
        member.role = role;
        member.joinedAt = LocalDateTime.now();
        member.notificationEnabled = true;
        member.lastReadMessageId = room.getLastMessageId();
        return member;
    }

    public boolean active() {
        return leftAt == null;
    }

    public void leave() {
        leftAt = LocalDateTime.now();
    }

    public void read(Long messageId) {
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            lastReadMessageId = messageId;
        }
    }
}
