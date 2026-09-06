package com.highpass.runspot.session.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "session_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_session_participants_session_user",
                        columnNames = {"session_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_session_participants_session_id", columnList = "session_id"),
                @Index(name = "idx_session_participants_user_id", columnList = "user_id"),
                @Index(name = "idx_session_participants_status", columnList = "status")
        }
)
public class SessionParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // BIGINT PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "session_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_session_participants_session")
    )
    private Session session; // session_id

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_session_participants_user")
    )
    private User user; // user_id

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParticipationStatus status = ParticipationStatus.REQUESTED; // 호스트 승인여부

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false)
    @Builder.Default
    private AttendanceStatus attendanceStatus = AttendanceStatus.ABSENT; // 출석여부

    @Lob
    @Column(name = "message_to_host")
    private String messageToHost; // 호스트에게 남길 메시지

    @Column(name = "completed_distance_km", precision = 8, scale = 2)
    private BigDecimal completedDistanceKm; // 완주 거리(00.00), 미완주는 null

    // 비즈니스 로직
    public void approve() {
        if (this.status != ParticipationStatus.REQUESTED) {
            throw new IllegalStateException("대기 상태의 참여자만 승인할 수 있습니다.");
        }
        this.status = ParticipationStatus.APPROVED;
    }

    public void reject() {
        if (this.status != ParticipationStatus.REQUESTED) {
            throw new IllegalStateException("대기 상태의 참여자만 거절할 수 있습니다.");
        }
        this.status = ParticipationStatus.REJECTED;
    }

    public void updateAttendance(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public void kick() {
        if (this.status != ParticipationStatus.APPROVED && this.status != ParticipationStatus.REQUESTED) {
            throw new IllegalStateException("승인 또는 대기 상태의 참여자만 내보낼 수 있습니다.");
        }
        this.status = ParticipationStatus.KICKED;
    }
}
