package com.highpass.runspot.community.dto;
import com.highpass.runspot.community.domain.ReportReason;
import com.highpass.runspot.community.domain.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record ReportRequest(@NotNull ReportTargetType targetType,@NotNull Long targetId,@NotNull ReportReason reasonCode,@Size(max=500) String detail){}
