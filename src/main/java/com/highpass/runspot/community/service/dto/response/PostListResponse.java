package com.highpass.runspot.community.service.dto.response;

import java.util.List;

public record PostListResponse(
        List<PostSummaryResponse> items, String nextCursor, boolean hasNext) {}
