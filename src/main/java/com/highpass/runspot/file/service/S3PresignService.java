package com.highpass.runspot.file.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.file.domain.UploadedFile;
import com.highpass.runspot.file.dto.*;
import com.highpass.runspot.file.exception.*;
import com.highpass.runspot.file.repository.UploadedFileRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class S3PresignService {
    private static final long MAX = 5L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Duration TTL = Duration.ofMinutes(5);
    private final S3Presigner presigner;
    private final UploadedFileRepository files;
    private final UserRepository users;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Transactional
    public PresignedUrlResponse issue(Long userId, PresignedUrlRequest request) {
        if (request.files().size() > 3) throw e(FileErrorCode.INVALID_FILE_COUNT);
        User user = users.findById(userId).orElseThrow(() -> e(FileErrorCode.USER_NOT_FOUND));
        List<PresignedUrlResponse.Item> items = new ArrayList<>();
        for (var file : request.files()) {
            validate(file);
            String key = key(request.domain().directory(), file.fileName());
            PutObjectRequest put =
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.contentType())
                            .contentLength(file.size())
                            .build();
            String url =
                    presigner
                            .presignPutObject(
                                    PutObjectPresignRequest.builder()
                                            .signatureDuration(TTL)
                                            .putObjectRequest(put)
                                            .build())
                            .url()
                            .toString();
            files.save(UploadedFile.pending(key, user));
            items.add(new PresignedUrlResponse.Item(key, url, TTL.toSeconds()));
        }
        return new PresignedUrlResponse(items);
    }

    @Transactional
    public void link(Long userId, List<String> keys) {
        if (keys.isEmpty()) return;
        List<UploadedFile> owned = files.findByImageKeyInAndUserId(keys, userId);
        if (owned.size() != new HashSet<>(keys).size()) throw e(FileErrorCode.INVALID_FILE_KEY);
        owned.forEach(UploadedFile::link);
    }

    private void validate(PresignedUrlRequest.FileRequest f) {
        if (f.size() > MAX) throw e(FileErrorCode.INVALID_FILE_SIZE);
        if (!TYPES.contains(f.contentType())) throw e(FileErrorCode.INVALID_FILE_TYPE);
    }

    private String key(String domain, String name) {
        String ext =
                name.contains(".")
                        ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
                        : "jpg";
        LocalDate d = LocalDate.now(ZoneOffset.UTC);
        return "%s/%d/%02d/%02d/%s.%s"
                .formatted(
                        domain,
                        d.getYear(),
                        d.getMonthValue(),
                        d.getDayOfMonth(),
                        UUID.randomUUID(),
                        ext);
    }

    private FileException e(FileErrorCode code) {
        return new FileException(code);
    }
}
