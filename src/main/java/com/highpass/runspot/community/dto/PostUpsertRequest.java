package com.highpass.runspot.community.dto;

import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.PostStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostUpsertRequest(
        @NotNull BoardType boardType,
        @NotBlank @Size(max = 100) String title,
        @NotBlank String content,
        Long runningRecordId,
        @Size(max = 3) List<@NotBlank @Size(max = 500) String> imageKeys,
        @NotNull PostStatus status
) {
    public PostUpsertRequest {
        imageKeys = imageKeys == null ? List.of() : List.copyOf(imageKeys);
    }
}
