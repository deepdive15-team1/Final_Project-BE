package com.highpass.runspot.chat.dto;import jakarta.validation.constraints.*;public record ChatNoticeRequest(@NotBlank @Size(max=500)String content){}
