package com.highpass.runspot.file.repository;
import com.highpass.runspot.file.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UploadedFileRepository extends JpaRepository<UploadedFile,Long>{List<UploadedFile> findByStatusAndCreatedAtBefore(UploadedFileStatus status,LocalDateTime before);List<UploadedFile> findByImageKeyInAndUserId(List<String> keys,Long userId);}
