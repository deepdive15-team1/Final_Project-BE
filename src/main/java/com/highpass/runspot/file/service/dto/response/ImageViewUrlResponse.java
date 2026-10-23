package com.highpass.runspot.file.service.dto.response;

import java.util.List;

/** 비공개 S3 객체를 조회하기 위한 presigned GET URL 묶음. */
public record ImageViewUrlResponse(List<Item> items) {
    public record Item(String imageKey, String viewUrl, long expiresIn) {}
}
