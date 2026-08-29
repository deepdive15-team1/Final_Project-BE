package com.highpass.runspot.community.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.community.domain.*;
import com.highpass.runspot.community.dto.*;
import com.highpass.runspot.community.exception.*;
import com.highpass.runspot.community.repository.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CommunityInteractionService {
 private final PostRepository posts; private final PostLikeRepository likes; private final PostScrapRepository scraps; private final CommentRepository comments; private final ReportRepository reports; private final UserRepository users;
 @Transactional public void like(Long userId,Long postId){if(likes.existsByPostIdAndUserId(postId,userId))throw e(CommunityErrorCode.ALREADY_LIKED);Post p=post(postId);try{likes.saveAndFlush(PostLike.create(p,user(userId)));}catch(DataIntegrityViolationException ex){throw e(CommunityErrorCode.ALREADY_LIKED);}p.increaseLikeCount();}
 @Transactional public void unlike(Long userId,Long postId){PostLike like=likes.findByPostIdAndUserId(postId,userId).orElseThrow(()->e(CommunityErrorCode.LIKE_NOT_FOUND));likes.delete(like);like.getPost().decreaseLikeCount();}
 @Transactional public void scrap(Long userId,Long postId){if(scraps.existsByPostIdAndUserId(postId,userId))throw e(CommunityErrorCode.ALREADY_SCRAPPED);try{scraps.saveAndFlush(PostScrap.create(post(postId),user(userId)));}catch(DataIntegrityViolationException ex){throw e(CommunityErrorCode.ALREADY_SCRAPPED);}}
 @Transactional public void unscrap(Long userId,Long postId){scraps.delete(scraps.findByPostIdAndUserId(postId,userId).orElseThrow(()->e(CommunityErrorCode.SCRAP_NOT_FOUND)));}
 public List<PostSummaryResponse> myScraps(Long userId){return scraps.findByUserIdOrderByIdDesc(userId).stream().map(PostScrap::getPost).filter(p->p.getStatus()==PostStatus.PUBLISHED).map(PostSummaryResponse::from).toList();}
 public List<CommentResponse> comments(Long postId){post(postId);return comments.findByPostIdOrderByIdAsc(postId).stream().map(CommentResponse::from).toList();}
 @Transactional public CommentResponse comment(Long userId,Long postId,CommentRequest request){Post p=post(postId);Comment parent=request.parentId()==null?null:comments.findById(request.parentId()).orElseThrow(()->e(CommunityErrorCode.COMMENT_NOT_FOUND));if(parent!=null&&parent.getParent()!=null)throw e(CommunityErrorCode.NESTED_REPLY_NOT_ALLOWED);if(parent!=null&&!parent.getPost().getId().equals(postId))throw e(CommunityErrorCode.PARENT_COMMENT_MISMATCH);Comment saved=comments.save(Comment.create(p,user(userId),parent,request.content()));p.increaseCommentCount();return CommentResponse.from(saved);}
 @Transactional public void deleteComment(Long userId,Long id){Comment c=comments.findById(id).orElseThrow(()->e(CommunityErrorCode.COMMENT_NOT_FOUND));if(c.getStatus()==CommentStatus.ACTIVE){c.delete(userId);c.getPost().decreaseCommentCount();}}
 @Transactional public Long report(Long userId,ReportRequest request){if(reports.existsByReporterIdAndTargetTypeAndTargetId(userId,request.targetType(),request.targetId()))throw e(CommunityErrorCode.ALREADY_REPORTED);try{return reports.saveAndFlush(Report.create(user(userId),request.targetType(),request.targetId(),request.reasonCode(),request.detail())).getId();}catch(DataIntegrityViolationException ex){throw e(CommunityErrorCode.ALREADY_REPORTED);}}
 private Post post(Long id){Post p=posts.findById(id).orElseThrow(()->e(CommunityErrorCode.POST_NOT_FOUND));if(p.getStatus()!=PostStatus.PUBLISHED)throw e(CommunityErrorCode.POST_NOT_FOUND);return p;}
 private User user(Long id){return users.findById(id).orElseThrow(()->e(CommunityErrorCode.USER_NOT_FOUND));}
 private CommunityException e(CommunityErrorCode code){return new CommunityException(code);}
}
