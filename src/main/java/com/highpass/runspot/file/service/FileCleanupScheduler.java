package com.highpass.runspot.file.service;
import com.highpass.runspot.file.domain.UploadedFileStatus;
import com.highpass.runspot.file.repository.UploadedFileRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
@Component @RequiredArgsConstructor
public class FileCleanupScheduler {
 private final UploadedFileRepository files;private final S3Client s3;@Value("${aws.s3.bucket}")private String bucket;
 @Scheduled(cron="0 0 3 * * *",zone="Asia/Seoul") @Transactional public void cleanup(){for(var file:files.findByStatusAndCreatedAtBefore(UploadedFileStatus.PENDING,LocalDateTime.now().minusHours(24))){s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(file.getImageKey()).build());files.delete(file);}}
}
