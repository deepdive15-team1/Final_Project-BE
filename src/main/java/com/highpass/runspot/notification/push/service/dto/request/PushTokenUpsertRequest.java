package com.highpass.runspot.notification.push.service.dto.request;

import com.highpass.runspot.notification.push.domain.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Android FCM 푸시 토큰 등록 또는 교체 요청")
public record PushTokenUpsertRequest(
        @Schema(
                description = "Android 앱에서 발급된 FCM 등록 토큰(공백 불가, 최대 512자)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 1,
                maxLength = 512,
                example = "fcm-token-placeholder-not-a-secret"
        )
        @NotBlank(message = "푸시 토큰은 필수입니다.")
        @Size(max = 512, message = "푸시 토큰은 512자 이하여야 합니다.")
        String token,
        @Schema(
                description = "푸시 토큰 플랫폼. 현재 Android FCM만 지원합니다.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ANDROID"},
                example = "ANDROID"
        )
        @NotBlank(message = "푸시 플랫폼은 필수입니다.")
        @Pattern(regexp = "ANDROID", message = "지원하지 않는 푸시 플랫폼입니다.")
        String platform
) {

    public PushPlatform pushPlatform() {
        return PushPlatform.valueOf(platform);
    }
}
