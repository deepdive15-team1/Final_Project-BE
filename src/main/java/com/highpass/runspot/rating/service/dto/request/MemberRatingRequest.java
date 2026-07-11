package com.highpass.runspot.rating.service.dto.request;

import com.highpass.runspot.rating.domain.RatingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MemberRatingRequest(
        @NotNull(message = "평가 목록은 필수입니다.")
        @Size(min = 1, message = "최소 1명 이상의 평가가 필요합니다.")
        @Valid
        List<MemberRatingItem> ratings
) {
    public record MemberRatingItem(
            @NotNull(message = "대상 사용자 ID는 필수입니다.")
            Long targetUserId,

            @NotNull(message = "평가는 필수입니다.")
            RatingType rating
    ) {
    }
}
