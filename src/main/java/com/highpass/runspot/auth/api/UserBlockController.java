package com.highpass.runspot.auth.api;

import com.highpass.runspot.auth.service.UserBlockService;
import com.highpass.runspot.auth.service.dto.response.BlockedUserResponse;
import com.highpass.runspot.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/users")
public class UserBlockController {

    private final UserBlockService userBlockService;

    @Operation(summary = "사용자 차단", description = "지정한 사용자를 차단합니다.")
    @PostMapping("/{userId}/block")
    public ResponseEntity<Void> block(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        userBlockService.block(principal.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "사용자 차단 해제", description = "지정한 사용자의 차단을 해제합니다.")
    @DeleteMapping("/{userId}/block")
    public ResponseEntity<Void> unblock(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        userBlockService.unblock(principal.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "차단한 사용자 목록 조회", description = "내가 차단한 사용자 목록을 조회합니다.")
    @GetMapping("/blocks")
    public ResponseEntity<List<BlockedUserResponse>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        List<BlockedUserResponse> response = userBlockService.getBlockedUsers(principal.getId());
        return ResponseEntity.ok(response);
    }
}
