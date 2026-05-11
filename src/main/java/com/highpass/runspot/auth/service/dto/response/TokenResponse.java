package com.highpass.runspot.auth.service.dto.response;

import com.highpass.runspot.auth.domain.User;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String name;
    private String ageGroup;
    private String gender;
    private Integer weeklyRuns;
    private Integer avgPaceMinPerKm;
    private BigDecimal mannerTemp;

    public static TokenResponse of(String accessToken, String refreshToken, User user) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .name(user.getName())
                .ageGroup(user.getAgeGroup().getCode())
                .gender(user.getGender().name())
                .weeklyRuns(user.getWeeklyRunningGoal())
                .avgPaceMinPerKm(formatPaceToInteger(user.getPacePreferenceSec()))
                .mannerTemp(user.getMannerTemp())
                .build();
    }

    public static TokenResponse ofTokensOnly(String accessToken, String refreshToken) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private static Integer formatPaceToInteger(Integer seconds) {
        if (seconds == null || seconds == 0) {
            return 0;
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes * 100 + secs;
    }
}
