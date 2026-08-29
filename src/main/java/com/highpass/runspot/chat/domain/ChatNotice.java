package com.highpass.runspot.chat.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "chat_notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatNotice extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @Column(nullable = false, length = 500)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static ChatNotice create(ChatRoom room, User user, String content) {
        ChatNotice notice = new ChatNotice();
        notice.room = room;
        notice.createdBy = user;
        notice.content = content;
        notice.active = true;
        return notice;
    }

    public void deactivate() {
        active = false;
    }

    public void update(String content) {
        this.content = content;
    }
}
