package com.highpass.runspot.chat.service.dto.request;

import jakarta.validation.constraints.*;

public record ChatNoticeRequest(@NotBlank @Size(max = 500) String content) {}
