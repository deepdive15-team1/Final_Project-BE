package com.highpass.runspot.session.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sessions")
public class Session extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // BIGINT PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sessions_host_user"))
    private User hostUser; // host_user_id BIGINT FK(users.id) NOT NULL

    @Column(nullable = false, length = 60)
    private String title; // VARCHAR(60) NOT NULL

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    private RunType runType; // ENUM() NOT NULL

    @Column(name = "location_name", nullable = false, length = 80)
    private String locationName; // VARCHAR(80) NOT NULL

    @Column(columnDefinition = "GEOMETRY SRID 4326", nullable = false)
    private Point location;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_polyline", nullable = false, columnDefinition = "json")
    private List<RoutePoint> routePolyline;

    @Column(name = "target_distance_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetDistanceKm; // DECIMAL(5,2) NOT NULL

    @Column(name = "avg_pace_sec", nullable = false)
    private Integer avgPaceSec; // INT NOT NULL (초/km)

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt; // DATETIME NOT NULL

    @Column(nullable = false)
    private Integer capacity; // INT NOT NULL

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_policy", nullable = false, length = 20)
    private GenderPolicy genderPolicy; // ENUM('MALE_ONLY','FEMALE_ONLY','MIXED') NOT NULL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.OPEN; // ENUM() NOT NULL DEFAULT 'OPEN'

    // 비즈니스 로직
    public void close(Long userId) {
        validateHost(userId);
        this.status = SessionStatus.CLOSED;
    }

    public void start(Long userId) {
        validateHost(userId);
        if (this.status == SessionStatus.IN_PROGRESS || this.status == SessionStatus.FINISHED) {
            throw new IllegalStateException("이미 시작되었거나 종료된 세션입니다.");
        }
        if (this.status == SessionStatus.CANCELED) {
            throw new IllegalStateException("취소된 세션은 시작할 수 없습니다.");
        }
        this.status = SessionStatus.IN_PROGRESS;
    }

    public void finish(Long userId) {
        validateHost(userId);
        if (this.status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("러닝 중인 세션만 종료할 수 있습니다.");
        }
        this.status = SessionStatus.FINISHED;
    }

    private void validateHost(Long userId) {
        if (!Objects.equals(this.hostUser.getId(), userId)) {
            throw new IllegalStateException("세션 호스트만 변경할 수 있습니다.");
        }
    }

    // JSON 요소 타입 (route_polyline)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoutePoint implements Serializable {
        private BigDecimal x;
        private BigDecimal y;
    }
}
