package com.highpass.runspot.auth.api;

import com.highpass.runspot.auth.service.UserStatsService;
import com.highpass.runspot.auth.service.dto.response.UserProfileResponse;
import com.highpass.runspot.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/users")
public class UserProfileController {

    private final UserStatsService userStatsService;

    @Operation(summary = "다른 사용자 프로필 조회", description = "이름, 성별, 나이대, 평균 페이스 등 특정 사용자의 공개 프로필 정보를 조회합니다.")
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        UserProfileResponse response = userStatsService.getProfile(userId);
        return ResponseEntity.ok(response);
    }
}
