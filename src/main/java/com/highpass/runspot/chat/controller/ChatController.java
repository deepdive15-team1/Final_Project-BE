package com.highpass.runspot.chat.controller;

import com.highpass.runspot.chat.domain.ChatRoomType;
import com.highpass.runspot.chat.dto.*;
import com.highpass.runspot.chat.service.ChatNoticeService;
import com.highpass.runspot.chat.service.ChatReadService;
import com.highpass.runspot.chat.service.ChatRoomService;
import com.highpass.runspot.common.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "Chat REST", description = "채팅방과 메시지 조회 API")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatRoomService roomService;
    private final ChatReadService reads;
    private final ChatNoticeService notices;

    @Operation(summary = "채팅방 목록 조회", description = "GROUP 또는 DIRECT 유형으로 필터링할 수 있습니다.")
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> rooms(
            @RequestParam(required = false) ChatRoomType type,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roomService.rooms(userId(principal), type));
    }

    @Operation(summary = "채팅방 상세 조회")
    @ApiResponse(responseCode = "403", description = "방 멤버가 아님")
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> detail(
            @PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roomService.detail(userId(principal), roomId));
    }

    @Operation(summary = "채팅 메시지 조회", description = "과거 방향 커서 페이지네이션입니다.")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessagePageResponse> messages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roomService.messages(userId(principal), roomId, cursor, size));
    }

    @Operation(summary = "1:1 문의방 생성", description = "이미 존재하면 기존 방을 반환합니다.")
    @ApiResponse(responseCode = "201", description = "생성 또는 기존 방 반환")
    @PostMapping("/rooms/direct")
    public ResponseEntity<DirectRoomResponse> direct(
            @Valid @RequestBody DirectRoomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        DirectRoomResponse response = roomService.direct(userId(principal), request);
        return ResponseEntity.created(URI.create("/api/v1/chat/rooms/" + response.roomId()))
                .body(response);
    }

    @Operation(summary = "채팅방 나가기", description = "MEMBER만 나갈 수 있습니다.")
    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<Void> leave(
            @PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal principal) {
        roomService.leave(userId(principal), roomId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "채팅방 삭제", description = "HOST만 삭제할 수 있습니다.")
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal principal) {
        roomService.delete(userId(principal), roomId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "채팅방 읽음 처리", description = "마지막으로 확인한 메시지 ID까지 읽음 처리합니다.")
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> read(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatReadRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        reads.read(userId(principal), roomId, request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "전체 안 읽은 메시지 수", description = "하단 채팅 탭 배지에 사용할 총 안 읽은 수를 반환합니다.")
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unread(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new UnreadCountResponse(reads.total(userId(principal))));
    }

    @Operation(summary = "고정 공지 등록 또는 수정", description = "그룹방 호스트가 활성 공지를 등록하거나 수정합니다.")
    @PutMapping("/rooms/{roomId}/notice")
    public ResponseEntity<Void> notice(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatNoticeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        notices.upsert(userId(principal), roomId, request.content());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "고정 공지 삭제", description = "그룹방 호스트가 활성 공지를 해제합니다.")
    @DeleteMapping("/rooms/{roomId}/notice")
    public ResponseEntity<Void> deleteNotice(
            @PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal principal) {
        notices.delete(userId(principal), roomId);
        return ResponseEntity.noContent().build();
    }

    private Long userId(UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        return principal.getId();
    }
}
