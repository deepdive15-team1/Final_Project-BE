package com.highpass.runspot.session.api;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.service.SessionQueryService;
import com.highpass.runspot.session.service.SessionService;
import com.highpass.runspot.session.service.dto.request.AttendanceUpdateRequest;
import com.highpass.runspot.session.service.dto.request.PositionBasedSearchCondition;
import com.highpass.runspot.session.service.dto.request.RangeBasedMarkerSearchCondition;
import com.highpass.runspot.session.service.dto.request.SessionCreateRequest;
import com.highpass.runspot.session.service.dto.request.SessionJoinRequest;
import com.highpass.runspot.session.service.dto.response.SessionInfoDetailResponse;
import com.highpass.runspot.session.service.dto.response.SessionInfoSummaryResponse;
import com.highpass.runspot.session.service.dto.response.SessionMarkerSearchResponse;
import com.highpass.runspot.session.service.dto.response.SessionNearbySearchResponse;
import com.highpass.runspot.session.service.dto.response.SessionParticipantResponse;
import com.highpass.runspot.session.service.dto.response.SessionResponse;
import com.highpass.runspot.session.service.dto.response.SessionSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Session", description = "러닝 세션 관리 API")
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SessionQueryService sessionQueryService;

    @Operation(summary = "세션 이름 검색", description = "쿼리 문자열을 포함하는 세션 이름을 검색합니다. 커서 기반 페이징을 지원합니다.")
    @GetMapping("/search")
    public ResponseEntity<Slice<SessionSearchResponse>> searchSessionByName(
            @Parameter(description = "검색어", required = true) @RequestParam("q") final String query,
            @Parameter(description = "커서 ID (마지막으로 조회된 ID)") @RequestParam(name = "cursorId", required = false) final Long cursorId,
            @Parameter(description = "페이지 크기 (기본값: 10)") @RequestParam(name = "size", required = false, defaultValue = "10") final int size
    ) {
        final Slice<SessionSearchResponse> searchResponses = sessionQueryService.searchSessionByName(query,
                cursorId, size);
        return ResponseEntity.ok(searchResponses);
    }

    @Operation(summary = "세션 요약 정보 조회", description = "특정 세션의 요약 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{sessionId}/summary")
    public ResponseEntity<SessionInfoSummaryResponse> getSessionSummary(
            @Parameter(description = "세션 ID", required = true) @PathVariable final Long sessionId
    ) {
        final SessionInfoSummaryResponse response = sessionQueryService.getSessionSummary(sessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "세션 상세 정보 조회", description = "특정 세션의 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionInfoDetailResponse> getSessionDetail(
            @Parameter(description = "세션 ID", required = true) @PathVariable final Long sessionId
    ) {
        final SessionInfoDetailResponse response = sessionQueryService.getSessionDetail(sessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주변 세션 검색", description = "현재 위치를 기준으로 반경 내의 세션들을 검색합니다.")
    @GetMapping("/search/nearby")
    public ResponseEntity<List<SessionNearbySearchResponse>> getSessionsNearby(
            @Valid @ModelAttribute final PositionBasedSearchCondition condition,
            @Parameter(description = "응답 개수") @Valid @Positive(message = "응답 크기는 양수여야 합니다") @RequestParam(name = "size", required = false,
                    defaultValue = "3") final int size
    ) {
        final List<SessionNearbySearchResponse> response = sessionQueryService.getSessionsNearby(condition, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "지도 마커 검색", description = "지도 영역 내에 표시할 세션 마커 정보를 검색합니다.")
    @GetMapping("/search/markers")
    public ResponseEntity<List<SessionMarkerSearchResponse>> getSessionsMarker(
            @Valid @ModelAttribute final RangeBasedMarkerSearchCondition condition
    ) {
        final List<SessionMarkerSearchResponse> response = sessionQueryService.getSessionsMarker(condition);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody SessionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        SessionResponse response = sessionService.createSession(principal.getId(), request);

        return ResponseEntity.created(URI.create("/sessions/" + response.id()))
                .body(response);
    }

    @PostMapping("/{sessionId}/close")
    public ResponseEntity<Void> closeSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.closeSession(principal.getId(), sessionId);

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "러닝 시작", description = "출석 확정 후 러닝을 시작합니다. (강퇴 가능 상태로 전환)")
    @PostMapping("/{sessionId}/start")
    public ResponseEntity<Void> startSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.startSession(principal.getId(), sessionId);

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "러닝 종료", description = "러닝을 종료합니다. (평가 가능 상태로 전환)")
    @PostMapping("/{sessionId}/finish")
    public ResponseEntity<Void> finishSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.finishSession(principal.getId(), sessionId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/join")
    public ResponseEntity<Void> joinSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionJoinRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.joinSession(principal.getId(), sessionId, request);

        return ResponseEntity.created(URI.create("/sessions/" + sessionId + "/join-requests")).build();
    }

    @GetMapping("/{sessionId}/join-requests")
    public ResponseEntity<List<SessionParticipantResponse>> getJoinRequests(
            @PathVariable Long sessionId,
            @RequestParam(required = false) ParticipationStatus status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        List<SessionParticipantResponse> responses = sessionService.getJoinRequests(principal.getId(), sessionId, status);

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{sessionId}/join-requests/{participationId}/approve")
    public ResponseEntity<Void> approveJoinRequest(
            @PathVariable Long sessionId,
            @PathVariable Long participationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.approveJoinRequest(principal.getId(), sessionId, participationId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/join-requests/{participationId}/reject")
    public ResponseEntity<Void> rejectJoinRequest(
            @PathVariable Long sessionId,
            @PathVariable Long participationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.rejectJoinRequest(principal.getId(), sessionId, participationId);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{sessionId}/attendance")
    public ResponseEntity<List<SessionParticipantResponse>> getAttendanceList(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        List<SessionParticipantResponse> responses = sessionService.getAttendanceList(principal.getId(), sessionId);

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "세션 참여자 내보내기", description = "호스트가 세션 참여자를 강퇴합니다.")
    @ApiResponse(responseCode = "204", description = "내보내기 성공")
    @DeleteMapping("/{sessionId}/participants/{participationId}")
    public ResponseEntity<Void> kickParticipant(
            @PathVariable Long sessionId,
            @PathVariable Long participationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.kickParticipant(principal.getId(), sessionId, participationId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{sessionId}/participants/{participationId}/attendance")
    public ResponseEntity<Void> updateAttendance(
            @PathVariable Long sessionId,
            @PathVariable Long participationId,
            @Valid @RequestBody AttendanceUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        sessionService.updateAttendance(principal.getId(), sessionId, participationId, request);

        return ResponseEntity.ok().build();
    }
}
