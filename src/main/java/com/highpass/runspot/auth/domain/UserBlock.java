package com.highpass.runspot.auth.domain;

import com.highpass.runspot.common.domain.BaseTimeEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_user_blocks_blocker_blocked",
                        columnNames = {"blocker_id", "blocked_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id")
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id")
    private User blocked;

    public static UserBlock create(User blocker, User blocked) {
        UserBlock userBlock = new UserBlock();
        userBlock.blocker = blocker;
        userBlock.blocked = blocked;
        return userBlock;
    }
}
