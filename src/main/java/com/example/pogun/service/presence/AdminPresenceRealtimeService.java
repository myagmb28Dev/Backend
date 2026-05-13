package com.example.pogun.service.presence;

import com.example.pogun.entity.user.User;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.user.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPresenceRealtimeService {

    public static final String ADMIN_PRESENCE_TOPIC = "/topic/admin/presence";

    private final UserRepository userRepository;
    private final UserPresenceService userPresenceService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Transactional(readOnly = true)
    public void publishByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        userRepository.findByFirebaseUid(firebaseUid).ifPresent(this::publishUserPresenceChanged);
    }

    @Transactional(readOnly = true)
    public void publishUserPresenceChanged(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        UserPresenceService.PresenceSnapshot snapshot = userPresenceService.snapshot(user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "PRESENCE_CHANGED");
        payload.put("userId", user.getId());
        payload.put("presence", snapshot.availabilityStatus().name());
        payload.put("presenceConnectionState", snapshot.actualConnectionState());
        payload.put("presenceLastActiveAt", snapshot.lastActiveAt() != null ? snapshot.lastActiveAt() : Instant.now());
        simpMessagingTemplate.convertAndSend(ADMIN_PRESENCE_TOPIC, (Object) payload);
        log.trace("[admin-presence] broadcast uid={} presence={} connectionState={}",
                user.getFirebaseUid(),
                snapshot.availabilityStatus(),
                snapshot.actualConnectionState());
    }
}
