package com.example.dropbox.metadata.files;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileUploadCleanupService {

    private final FileUploadSessionRepository fileUploadSessionRepository;

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void deleteExpiredInitiatedUploadSessions() {
        fileUploadSessionRepository.deleteByStatusAndExpiresAtBefore(
                FileUploadStatus.INITIATED.name(),
                Instant.now()
        );
    }
}