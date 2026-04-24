package com.example.pogun.service.presence;

import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresenceReconciliationScheduler {

    private final UserPresenceService userPresenceService;
    private final ObjectProvider<NoticeChatService> noticeChatServiceProvider;

    @Scheduled(fixedDelayString = "${app.presence.reconcile-interval-ms:5000}")
    public void reconcileStaleSessions() {
        NoticeChatService noticeChatService = noticeChatServiceProvider.getIfAvailable();
        if (noticeChatService == null) {
            return;
        }
        userPresenceService.reconcileStaleSessions()
                .forEach(noticeChatService::publishPresenceUpdatesByFirebaseUid);
    }
}
