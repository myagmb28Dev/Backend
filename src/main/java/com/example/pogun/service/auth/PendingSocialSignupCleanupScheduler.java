package com.example.pogun.service.auth;

import com.example.pogun.repository.user.PendingSocialSignupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingSocialSignupCleanupScheduler {

    private final PendingSocialSignupRepository pendingSocialSignupRepository;

    @Scheduled(cron = "${app.auth.pending-signup-cleanup-cron:0 */30 * * * *}")
    public void purgeExpiredPendingSignups() {
        long deleted = pendingSocialSignupRepository.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("pending social signup cleanup completed. deleted={}", deleted);
        }
    }
}
