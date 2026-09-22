package com.baakio.service;

import com.baakio.repository.ScanJobRecord;
import com.baakio.repository.ScanRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "baakio.scan.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScanCleanupScheduler {
    private final ScanRepository scans;
    private final DocumentStorage storage;
    private final int retentionDays;

    public ScanCleanupScheduler(ScanRepository scans, DocumentStorage storage,
                                @Value("${baakio.scan.retention-days:30}") int retentionDays) {
        this.scans = scans;
        this.storage = storage;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${baakio.scan.cleanup-cron:0 15 3 * * *}", zone = "UTC")
    public void cleanupExpiredImages() {
        for (ScanJobRecord job : scans.findExpiredImages(retentionDays)) {
            try {
                storage.delete(job.objectKey());
                scans.markImageDeleted(job.id());
            } catch (RuntimeException exception) {
                // Keep the row eligible so a later run retries an object-store failure.
            }
        }
    }
}
