package com.highpass.runspot.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(Long parentId, @NotBlank @Size(max = 500) String content) {}
