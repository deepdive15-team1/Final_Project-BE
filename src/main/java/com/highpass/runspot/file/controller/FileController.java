package com.highpass.runspot.file.controller;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.file.dto.*;
import com.highpass.runspot.file.service.S3PresignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "File", description = "S3 이미지 직접 업로드 API")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {
    private final S3PresignService service;

    @Operation(
            summary = "Presigned URL 발급",
            description =
                    "최대 3개 이미지의 5분 유효 PUT URL과 imageKey를 발급합니다. 응답의 Content-Type과 요청한 파일 크기를 PUT"
                        + " 요청에 그대로 사용해야 합니다.")
    @ApiResponse(responseCode = "200", description = "발급 성공")
    @ApiResponse(responseCode = "400", description = "개수·크기·형식 오류")
    @PostMapping("/presigned-urls")
    public ResponseEntity<PresignedUrlResponse> issue(
            @Valid @RequestBody PresignedUrlRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) throw new IllegalStateException("로그인이 필요합니다.");
        return ResponseEntity.ok(service.issue(principal.getId(), request));
    }
}
