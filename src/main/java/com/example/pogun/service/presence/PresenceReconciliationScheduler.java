package com.example.pogun.service.presence;

import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceReconciliationScheduler {

    private final UserPresenceService userPresenceService;
    private final ObjectProvider<NoticeChatService> noticeChatServiceProvider;

    @Scheduled(fixedDelayString = "${app.presence.reconcile-interval-ms:5000}")
    public void reconcileStaleSessions() {
        NoticeChatService noticeChatService = noticeChatServiceProvider.getIfAvailable();
        if (noticeChatService == null) {
            return;
        }
        userPresenceService.reconcileStaleSessions().forEach(firebaseUid -> {
            log.info("[presence] reconciled stale/disconnected uid={} -> broadcast", firebaseUid);
            noticeChatService.publishPresenceEventsByFirebaseUid(firebaseUid);
        });
    }
}
