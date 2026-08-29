package com.highpass.runspot.file.repository;

import com.highpass.runspot.file.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    List<UploadedFile> findByStatusAndCreatedAtBefore(
            UploadedFileStatus status, LocalDateTime before);

    List<UploadedFile> findByImageKeyInAndUserId(List<String> keys, Long userId);
}
