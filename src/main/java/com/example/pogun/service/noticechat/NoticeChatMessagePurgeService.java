package com.example.pogun.service.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 소프트 삭제된 DM 메시지를 보관 기간 이후 물리 삭제하는 정리 작업이다.
 */
@Service
@RequiredArgsConstructor
public class NoticeChatMessagePurgeService {

    private static final Duration RETENTION_PERIOD = Duration.ofDays(30);

    private final NoticeChatMessageRepository noticeChatMessageRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredDeletedMessages() {
        purgeExpiredDeletedMessages(Instant.now().minus(RETENTION_PERIOD));
    }

    @Transactional
    public void purgeExpiredDeletedMessages(Instant cutoff) {
        List<NoticeChatMessage> expiredMessages = noticeChatMessageRepository.findByDeletedAtBefore(cutoff);
        if (expiredMessages.isEmpty()) {
            return;
        }
        noticeChatMessageRepository.clearReplyTargets(expiredMessages);
        noticeChatMessageRepository.deleteAll(expiredMessages);
    }
}
