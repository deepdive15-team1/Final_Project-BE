package com.highpass.runspot.community.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Entity @Table(name="reports", uniqueConstraints=@UniqueConstraint(name="uk_reports_reporter_target", columnNames={"reporter_id","target_type","target_id"})) @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="reporter_id") private User reporter;
 @Enumerated(EnumType.STRING) @Column(name="target_type",nullable=false,length=20) private ReportTargetType targetType;
 @Column(name="target_id",nullable=false) private Long targetId;
 @Enumerated(EnumType.STRING) @Column(name="reason_code",nullable=false,length=20) private ReportReason reason;
 @Column(length=500) private String detail;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReportStatus status;
 public static Report create(User user, ReportTargetType type, Long targetId, ReportReason reason, String detail){Report r=new Report();r.reporter=user;r.targetType=type;r.targetId=targetId;r.reason=reason;r.detail=detail;r.status=ReportStatus.PENDING;return r;}
}
