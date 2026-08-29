package com.highpass.runspot.file.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "uploaded_files",
        indexes =
                @Index(
                        name = "idx_uploaded_files_status_created",
                        columnList = "status,created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedFile extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_key", nullable = false, unique = true, length = 500)
    private String imageKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadedFileStatus status;

    public static UploadedFile pending(String key, User user) {
        UploadedFile f = new UploadedFile();
        f.imageKey = key;
        f.user = user;
        f.status = UploadedFileStatus.PENDING;
        return f;
    }

    public void link() {
        status = UploadedFileStatus.LINKED;
    }
}
