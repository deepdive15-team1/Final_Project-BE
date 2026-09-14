package com.highpass.runspot.session.service.dto.request;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.util.GeometryUtil;
import com.highpass.runspot.session.domain.GenderPolicy;
import com.highpass.runspot.session.domain.RunType;
import com.highpass.runspot.session.domain.Session;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SessionCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotNull(message = "러닝 종류는 필수입니다.")
        RunType runType,

        @NotBlank(message = "장소 이름은 필수입니다.")
        String locationName,

        @NotNull(message = "경도(x)는 필수입니다.")
        BigDecimal locationX,

        @NotNull(message = "위도(y)는 필수입니다.")
        BigDecimal locationY,

        @NotNull(message = "경로 정보는 필수입니다.")
        List<RoutePointDto> routePolyline,

        @NotNull(message = "목표 거리는 필수입니다.")
        @Min(value = 0, message = "거리는 0보다 커야 합니다.")
        BigDecimal targetDistanceKm,

        @NotNull(message = "평균 페이스는 필수입니다.")
        @Min(value = 1, message = "페이스는 0보다 커야 합니다.")
        Integer avgPaceSec,

        @NotNull(message = "시작 시간은 필수입니다.")
        // @Future(message = "시작 시간은 현재 시간 이후여야 합니다.") // 제거
        LocalDateTime startAt,

        @NotNull(message = "모집 인원은 필수입니다.")
        @Min(value = 1, message = "최소 1명 이상이어야 합니다.")
        Integer capacity,

        @NotNull(message = "성별 정책은 필수입니다.")
        GenderPolicy genderPolicy
) {
    public record RoutePointDto(
            BigDecimal x,
            BigDecimal y, //프론트에서 넘겨주는 값 보고 변경
            String alias, // 마커 별칭
            String detail // 마커 상세내용
    ) {
        public Session.RoutePoint toDomain() {
            return new Session.RoutePoint(x, y, alias, detail);
        }
    }

    public Session toEntity(User hostUser) {
        return Session.builder()
                .hostUser(hostUser)
                .title(title)
                .runType(runType)
                .locationName(locationName)
                .location(GeometryUtil.createPoint(locationY, locationX))
                .routePolyline(routePolyline.stream().map(RoutePointDto::toDomain).toList())
                .targetDistanceKm(targetDistanceKm)
                .avgPaceSec(avgPaceSec)
                .startAt(startAt)
                .capacity(capacity)
                .genderPolicy(genderPolicy)
                .build();
    }
}
