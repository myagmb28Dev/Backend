package com.example.pogun.controller.presence;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.presence.PresenceHeartbeatRequest;
import com.example.pogun.dto.presence.PresenceHeartbeatResponse;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceRestController {

    private final UserPresenceService userPresenceService;
    private final ObjectProvider<NoticeChatService> noticeChatServiceProvider;

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<PresenceHeartbeatResponse>> heartbeat(
            @Valid @RequestBody PresenceHeartbeatRequest request
    ) {
        String firebaseUid = currentFirebaseUid();
        UserPresenceService.PresenceSnapshot snapshot = userPresenceService.heartbeat(firebaseUid, request.getClientSessionId());
        NoticeChatService noticeChatService = noticeChatServiceProvider.getIfAvailable();
        if (noticeChatService != null) {
            noticeChatService.publishPresenceUpdatesByFirebaseUid(firebaseUid);
            noticeChatService.publishPresenceEventsByFirebaseUid(firebaseUid);
        }
        log.trace("[presence] heartbeat uid={} page={} connectionState={} effective={}",
                firebaseUid,
                request.getPage(),
                snapshot.actualConnectionState(),
                snapshot.availabilityStatus());
        PresenceHeartbeatResponse data = new PresenceHeartbeatResponse(
                snapshot.manualPresenceStatus().name(),
                snapshot.actualConnectionState(),
                snapshot.availabilityStatus().name(),
                snapshot.lastActiveAt()
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "presence heartbeat updated", data));
    }

    private String currentFirebaseUid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof String firebaseUid ? firebaseUid : "";
    }
}
