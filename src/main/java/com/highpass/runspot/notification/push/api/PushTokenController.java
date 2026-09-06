package com.highpass.runspot.notification.push.api;

import com.highpass.runspot.common.exception.dto.ErrorResponse;
import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.notification.push.service.PushDeviceTokenService;
import com.highpass.runspot.notification.push.service.dto.request.PushTokenUpsertRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(name = "Android FCM 푸시 토큰", description = "Android 앱의 FCM 푸시 토큰 생명주기(등록·교체·철회) API")
@SecurityRequirement(name = "bearerAuth")
public class PushTokenController {

    private final PushDeviceTokenService pushDeviceTokenService;

    @Operation(
            summary = "내 Android FCM 푸시 토큰 등록 또는 교체",
            description = "인증된 사용자가 Android 앱에서 발급받은 FCM 푸시 토큰을 등록하거나 교체합니다. "
                    + "같은 토큰을 다시 등록하면 멱등적으로 처리하고, 기존 토큰은 새 토큰으로 교체합니다. "
                    + "요청 토큰이 다른 사용자의 토큰이면 기존 소유권을 해제하고 현재 사용자에게 이전합니다. "
                    + "성공하면 응답 본문 없이 204를 반환합니다. 입력값 검증 또는 요청 형식 오류는 400, "
                    + "동시 등록 충돌은 409를 반환합니다.")
    @ApiResponse(responseCode = "204", description = "Android FCM 푸시 토큰 등록 또는 교체 성공")
    @ApiResponse(
            responseCode = "400",
            description = "token 또는 platform 누락·공백, token 512자 초과 또는 요청 형식 오류",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "로그인이 필요합니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "409",
            description = "푸시 토큰 등록이 동시에 처리되었습니다. 다시 시도해주세요.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    @PutMapping
    public ResponseEntity<Void> upsert(
            @Valid @RequestBody PushTokenUpsertRequest request,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        pushDeviceTokenService.upsert(requireAuthenticated(userPrincipal), request.token(), request.pushPlatform());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "내 Android FCM 푸시 토큰 철회",
            description = "인증된 사용자의 Android FCM 푸시 토큰과 미전송 푸시 작업을 멱등적으로 철회합니다. "
                    + "토큰이 이미 없어도 삭제된 것으로 간주하여 응답 본문 없이 204를 반환합니다. "
                    + "PENDING 또는 PROCESSING 상태의 미전송 작업은 함께 삭제하고, 이미 종결된 SENT 또는 FAILED 작업 기록은 보존합니다. "
                    + "외부 푸시 제공자에 이미 발송 요청이 전달된 전송은 회수할 수 없습니다.")
    @ApiResponse(responseCode = "204", description = "Android FCM 푸시 토큰 철회 성공 또는 이미 철회된 상태")
    @ApiResponse(
            responseCode = "401",
            description = "로그인이 필요합니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    @DeleteMapping
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal) {
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
