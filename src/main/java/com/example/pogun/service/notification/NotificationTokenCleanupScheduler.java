package com.example.pogun.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTokenCleanupScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "${app.notification.inactive-token-cleanup-cron:0 10 4 * * *}")
    public void purgeInactiveTokens() {
        int deleted = notificationService.purgeInactiveFcmTokens();
        if (deleted > 0) {
            log.info("notification token cleanup completed. deleted={}", deleted);
        }
    }
}
