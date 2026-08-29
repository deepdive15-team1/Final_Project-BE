package com.highpass.runspot.file.dto;
import java.util.List;
public record PresignedUrlResponse(List<Item> items){public record Item(String imageKey,String uploadUrl,long expiresIn){}}
