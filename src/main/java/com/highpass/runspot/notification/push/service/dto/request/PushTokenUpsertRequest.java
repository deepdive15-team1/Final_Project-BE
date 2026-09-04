package com.highpass.runspot.notification.push.service.dto.request;

import com.highpass.runspot.notification.push.domain.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushTokenUpsertRequest(
        @NotBlank(message = "푸시 토큰은 필수입니다.")
        @Size(max = 512, message = "푸시 토큰은 512자 이하여야 합니다.")
        String token,
        @NotBlank(message = "푸시 플랫폼은 필수입니다.")
        @Pattern(regexp = "ANDROID", message = "지원하지 않는 푸시 플랫폼입니다.")
        String platform
) {

    public PushPlatform pushPlatform() {
        return PushPlatform.valueOf(platform);
    }
}
