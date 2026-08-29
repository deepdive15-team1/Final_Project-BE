package com.highpass.runspot.chat.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@Entity
@Table(
        name = "chat_messages",
        indexes = @Index(name = "idx_chat_messages_room_id_id", columnList = "room_id,id"),
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_chat_messages_room_client",
                        columnNames = {"room_id", "client_message_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType;

    @Column(length = 1000)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_keys", columnDefinition = "json")
    private List<String> imageKeys;

    public static ChatMessage create(
            ChatRoom room,
            User sender,
            String clientMessageId,
            ChatMessageType messageType,
            String content,
            List<String> imageKeys) {
        ChatMessage message = new ChatMessage();
        message.room = room;
        message.sender = sender;
        message.clientMessageId = clientMessageId;
        message.messageType = messageType;
        message.content = content;
        message.imageKeys = imageKeys;
        return message;
    }

    public static ChatMessage system(ChatRoom room, String content) {
        ChatMessage message = new ChatMessage();
        message.room = room;
        message.messageType = ChatMessageType.SYSTEM;
        message.content = content;
        message.imageKeys = List.of();
        return message;
    }
}
