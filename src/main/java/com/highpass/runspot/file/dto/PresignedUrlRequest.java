package com.highpass.runspot.file.dto;
import com.highpass.runspot.file.domain.FileDomain;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record PresignedUrlRequest(@NotNull FileDomain domain,@NotEmpty @Size(max=3) List<@Valid FileRequest> files){public record FileRequest(@NotBlank String fileName,@NotBlank String contentType,@NotNull @Positive Long size){}}
