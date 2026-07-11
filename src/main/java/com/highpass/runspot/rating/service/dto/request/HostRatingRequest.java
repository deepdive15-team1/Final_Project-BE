package com.highpass.runspot.rating.service.dto.request;

import com.highpass.runspot.rating.domain.RatingType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record HostRatingRequest(
        @NotNull(message = "평가는 필수입니다.")
        RatingType rating,

        List<String> tags
) {
}
