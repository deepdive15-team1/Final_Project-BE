package com.highpass.runspot.file.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.file.domain.FileDomain;
import com.highpass.runspot.file.dto.PresignedUrlRequest;
import com.highpass.runspot.file.dto.PresignedUrlRequest.FileRequest;
import com.highpass.runspot.file.exception.FileErrorCode;
import com.highpass.runspot.file.exception.FileException;
import com.highpass.runspot.file.repository.UploadedFileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class S3PresignServiceTest {
    @Mock S3Presigner presigner;
    @Mock UploadedFileRepository files;
    @Mock UserRepository users;
    @InjectMocks S3PresignService service;

    @BeforeEach
    void setUp() {
        lenient().when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
    }

    @Test
    void 파일은_한번에_세개를_초과할_수_없다() {
        FileRequest file = new FileRequest("a.jpg", "image/jpeg", 100L);
        assertThatThrownBy(() -> service.issue(1L,
                new PresignedUrlRequest(FileDomain.POST, List.of(file, file, file, file))))
                .isInstanceOf(FileException.class)
                .hasFieldOrPropertyWithValue("exceptionType", FileErrorCode.INVALID_FILE_COUNT);
    }

    @Test
    void 파일은_5MB를_초과할_수_없다() {
        FileRequest file = new FileRequest("a.jpg", "image/jpeg", 5L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.issue(1L,
                new PresignedUrlRequest(FileDomain.POST, List.of(file))))
                .isInstanceOf(FileException.class)
                .hasFieldOrPropertyWithValue("exceptionType", FileErrorCode.INVALID_FILE_SIZE);
    }

    @Test
    void 허용되지_않은_콘텐츠_타입은_거부한다() {
        FileRequest file = new FileRequest("virus.exe", "application/octet-stream", 100L);
        assertThatThrownBy(() -> service.issue(1L,
                new PresignedUrlRequest(FileDomain.POST, List.of(file))))
                .isInstanceOf(FileException.class)
                .hasFieldOrPropertyWithValue("exceptionType", FileErrorCode.INVALID_FILE_TYPE);
    }
}
