package com.baakio.service;

import com.baakio.repository.ScanRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "baakio.scan.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScanRecoveryScheduler {
    private final ScanRepository scans;

    public ScanRecoveryScheduler(ScanRepository scans) { this.scans = scans; }

    @Scheduled(fixedDelayString = "${baakio.scan.recovery-poll-ms:60000}")
    public void recover() { scans.recoverStaleClaims(); }
}
