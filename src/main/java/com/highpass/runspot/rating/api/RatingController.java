package com.highpass.runspot.rating.api;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.rating.service.RatingService;
import com.highpass.runspot.rating.service.dto.request.HostRatingRequest;
import com.highpass.runspot.rating.service.dto.request.MemberRatingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rating", description = "세션 평가 API")
@RestController
@RequestMapping("/sessions/{sessionId}/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @Operation(summary = "호스트 평가", description = "세션 종료 후 호스트를 평가합니다.")
    @ApiResponse(responseCode = "204", description = "평가 완료")
    @PostMapping("/host")
    public ResponseEntity<Void> rateHost(
            @PathVariable Long sessionId,
            @Valid @RequestBody HostRatingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        ratingService.rateHost(principal.getId(), sessionId, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "멤버 평가", description = "세션 종료 후 함께한 멤버들을 평가합니다.")
    @ApiResponse(responseCode = "204", description = "평가 완료")
    @PostMapping("/members")
    public ResponseEntity<Void> rateMembers(
            @PathVariable Long sessionId,
            @Valid @RequestBody MemberRatingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        ratingService.rateMembers(principal.getId(), sessionId, request);

        return ResponseEntity.noContent().build();
    }
}
