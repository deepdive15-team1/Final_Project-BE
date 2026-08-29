package com.highpass.runspot.course.controller;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.course.dto.RunningRecordResponse;
import com.highpass.runspot.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Running Course", description = "개인 러닝 기록 및 저장 코스 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService service;

    @Operation(summary = "내 러닝 기록 조회", description = "코스 게시글에 연결할 본인의 러닝 기록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/me/running-records")
    public ResponseEntity<List<RunningRecordResponse>> records(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.myRecords(id(principal)));
    }

    @Operation(summary = "러닝 코스 저장", description = "러닝 기록의 코스를 내 저장 목록에 추가합니다.")
    @ApiResponse(responseCode = "204", description = "저장 성공")
    @ApiResponse(responseCode = "404", description = "러닝 기록 없음")
    @ApiResponse(responseCode = "409", description = "이미 저장한 코스")
    @PostMapping("/courses/{recordId}/scrap")
    public ResponseEntity<Void> scrap(@PathVariable Long recordId,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        service.scrap(id(principal), recordId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "저장한 코스 조회", description = "저장한 러닝 코스를 최신 저장순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/me/courses")
    public ResponseEntity<List<RunningRecordResponse>> courses(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.myCourses(id(principal)));
    }

    private Long id(UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        return principal.getId();
    }
}
