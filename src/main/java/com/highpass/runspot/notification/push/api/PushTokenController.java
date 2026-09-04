package com.highpass.runspot.notification.push.api;

import com.highpass.runspot.common.exception.dto.ErrorResponse;
import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.notification.push.service.PushDeviceTokenService;
import com.highpass.runspot.notification.push.service.dto.request.PushTokenUpsertRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/push-token")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushDeviceTokenService pushDeviceTokenService;

    @Operation(summary = "내 Android 푸시 토큰 등록 또는 교체")
    @PutMapping
    public ResponseEntity<Void> upsert(
            @Valid @RequestBody PushTokenUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        pushDeviceTokenService.upsert(requireAuthenticated(userPrincipal), request.token(), request.pushPlatform());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 Android 푸시 토큰 삭제")
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        pushDeviceTokenService.delete(requireAuthenticated(userPrincipal));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest() {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
    }

    private Long requireAuthenticated(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        return userPrincipal.getId();
    }
}
