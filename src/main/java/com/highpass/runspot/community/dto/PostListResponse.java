package com.highpass.runspot.community.dto;

import java.util.List;

public record PostListResponse(List<PostSummaryResponse> items, String nextCursor, boolean hasNext) {
}
