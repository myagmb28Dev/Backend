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
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceRestController {

    private final UserPresenceService userPresenceService;
    private final ObjectProvider<NoticeChatService> noticeChatServiceProvider;

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<PresenceHeartbeatResponse>> heartbeat(
            @Valid @RequestBody PresenceHeartbeatRequest request,
            HttpServletRequest httpRequest
    ) {
        String firebaseUid = currentFirebaseUid();
        String scopedSessionId = scopedSessionId(request.getClientSessionId(), httpRequest);
        UserPresenceService.PresenceSnapshot snapshot = userPresenceService.heartbeat(firebaseUid, scopedSessionId);
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

    private String scopedSessionId(String clientSessionId, HttpServletRequest request) {
        String host = resolveRequestHost(request);
        String rawClientId = (clientSessionId == null || clientSessionId.isBlank()) ? "unknown-client" : clientSessionId.trim();
        return host + ":" + rawClientId;
    }

    private String resolveRequestHost(HttpServletRequest request) {
        if (request == null) {
            return "unknown-host";
        }

        String origin = trimToNull(request.getHeader("Origin"));
        if (origin != null) {
            String originHost = hostFromOrigin(origin);
            if (originHost != null) {
                return originHost;
            }
        }

        String forwardedHost = firstHostHeader(request.getHeader("X-Forwarded-Host"));
        if (forwardedHost != null) {
            return stripPort(forwardedHost);
        }

        String hostHeader = firstHostHeader(request.getHeader("Host"));
        if (hostHeader != null) {
            return stripPort(hostHeader);
        }

        String serverName = trimToNull(request.getServerName());
        return serverName != null ? serverName.toLowerCase(Locale.ROOT) : "unknown-host";
    }

    private String hostFromOrigin(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            String host = uri.getHost();
            return host == null || host.isBlank() ? null : host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String firstHostHeader(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return null;
        }
        int commaIndex = raw.indexOf(',');
        String first = commaIndex >= 0 ? raw.substring(0, commaIndex) : raw;
        return trimToNull(first);
    }

    private String stripPort(String hostValue) {
        String normalized = hostValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[")) {
            int end = normalized.indexOf(']');
            if (end > 0) {
                return normalized.substring(1, end);
            }
        }
        int colon = normalized.indexOf(':');
        return colon > 0 ? normalized.substring(0, colon) : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
