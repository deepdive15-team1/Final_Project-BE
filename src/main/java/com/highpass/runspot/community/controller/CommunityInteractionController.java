package com.highpass.runspot.community.controller;
import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.community.dto.*;
import com.highpass.runspot.community.service.CommunityInteractionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class CommunityInteractionController {
 private final CommunityInteractionService service;
 @PostMapping("/posts/{postId}/like") public ResponseEntity<Void> like(@PathVariable Long postId,@AuthenticationPrincipal UserPrincipal p){service.like(id(p),postId);return ResponseEntity.noContent().build();}
 @DeleteMapping("/posts/{postId}/like") public ResponseEntity<Void> unlike(@PathVariable Long postId,@AuthenticationPrincipal UserPrincipal p){service.unlike(id(p),postId);return ResponseEntity.noContent().build();}
 @PostMapping("/posts/{postId}/scrap") public ResponseEntity<Void> scrap(@PathVariable Long postId,@AuthenticationPrincipal UserPrincipal p){service.scrap(id(p),postId);return ResponseEntity.noContent().build();}
 @DeleteMapping("/posts/{postId}/scrap") public ResponseEntity<Void> unscrap(@PathVariable Long postId,@AuthenticationPrincipal UserPrincipal p){service.unscrap(id(p),postId);return ResponseEntity.noContent().build();}
 @GetMapping("/me/scraps") public ResponseEntity<List<PostSummaryResponse>> myScraps(@AuthenticationPrincipal UserPrincipal p){return ResponseEntity.ok(service.myScraps(id(p)));}
 @GetMapping("/posts/{postId}/comments") public ResponseEntity<List<CommentResponse>> comments(@PathVariable Long postId){return ResponseEntity.ok(service.comments(postId));}
 @PostMapping("/posts/{postId}/comments") public ResponseEntity<CommentResponse> comment(@PathVariable Long postId,@Valid @RequestBody CommentRequest r,@AuthenticationPrincipal UserPrincipal p){CommentResponse response=service.comment(id(p),postId,r);return ResponseEntity.created(URI.create("/api/v1/comments/"+response.commentId())).body(response);}
 @DeleteMapping("/comments/{commentId}") public ResponseEntity<Void> deleteComment(@PathVariable Long commentId,@AuthenticationPrincipal UserPrincipal p){service.deleteComment(id(p),commentId);return ResponseEntity.noContent().build();}
 @PostMapping("/reports") public ResponseEntity<Void> report(@Valid @RequestBody ReportRequest r,@AuthenticationPrincipal UserPrincipal p){Long id=service.report(id(p),r);return ResponseEntity.created(URI.create("/api/v1/reports/"+id)).build();}
 private Long id(UserPrincipal p){if(p==null)throw new IllegalStateException("로그인이 필요합니다.");return p.getId();}
}
