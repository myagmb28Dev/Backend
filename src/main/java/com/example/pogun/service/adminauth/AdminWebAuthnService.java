package com.example.pogun.service.adminauth;

import com.example.pogun.config.AdminConsoleProperties;
import com.example.pogun.entity.user.User;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RegistrationExtensionInputs;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.RegistrationFailedException;
import com.yubico.webauthn.exception.AssertionFailedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminWebAuthnService {

    private final AdminConsoleProperties adminConsoleProperties;
    private final AdminCredentialRepository adminCredentialRepository;

    public PublicKeyCredentialCreationOptions startRegistration(User user, HttpServletRequest request) {
        OriginRpContext context = resolveOriginRpContext(request);
        RelyingParty rp = relyingParty(context);
        adminCredentialRepository.setCurrentRpId(context.rpId());
        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getEmail())
                .id(new ByteArray(user.getId().toString().getBytes(StandardCharsets.UTF_8)))
                .build();
        try {
            return rp.startRegistration(StartRegistrationOptions.builder()
                    .user(userIdentity)
                    .authenticatorSelection(AuthenticatorSelectionCriteria.builder().build())
                    .extensions(RegistrationExtensionInputs.builder().build())
                    .build());
        } finally {
            adminCredentialRepository.clearCurrentRpId();
        }
    }

    public AssertionRequest startAssertion(User user, HttpServletRequest request) {
        OriginRpContext context = resolveOriginRpContext(request);
        adminCredentialRepository.setCurrentRpId(context.rpId());
        try {
            return relyingParty(context).startAssertion(StartAssertionOptions.builder()
                    .username(user.getEmail())
                    .build());
        } finally {
            adminCredentialRepository.clearCurrentRpId();
        }
    }

    public RegistrationFinishPayload finishRegistration(String requestJson, String credentialJson, HttpServletRequest request)
            throws RegistrationFailedException, com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
        OriginRpContext context = resolveOriginRpContext(request);
        PublicKeyCredentialCreationOptions registrationRequest = PublicKeyCredentialCreationOptions.fromJson(requestJson);
        var response = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
        adminCredentialRepository.setCurrentRpId(context.rpId());
        try {
            var result = relyingParty(context).finishRegistration(FinishRegistrationOptions.builder()
                    .request(registrationRequest)
                    .response(response)
                    .build());
            return new RegistrationFinishPayload(
                    result.getKeyId().getId().getBase64Url(),
                    result.getPublicKeyCose().getBase64Url(),
                    result.getSignatureCount(),
                    context.rpId()
            );
        } finally {
            adminCredentialRepository.clearCurrentRpId();
        }
    }

    public AssertionFinishPayload finishAssertion(String requestJson, String credentialJson, HttpServletRequest request)
            throws AssertionFailedException, com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
        OriginRpContext context = resolveOriginRpContext(request);
        AssertionRequest assertionRequest = AssertionRequest.fromJson(requestJson);
        var response = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        adminCredentialRepository.setCurrentRpId(context.rpId());
        try {
            var result = relyingParty(context).finishAssertion(FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(response)
                    .build());
            if (!result.isSuccess()) {
                throw new AssertionFailedException("PassKey 검증에 실패했습니다.");
            }
            return new AssertionFinishPayload(
                    result.getCredential().getCredentialId().getBase64Url(),
                    result.getSignatureCount(),
                    context.rpId()
            );
        } finally {
            adminCredentialRepository.clearCurrentRpId();
        }
    }

    public String resolveRpId(HttpServletRequest request) {
        return resolveOriginRpContext(request).rpId();
    }

    private RelyingParty relyingParty(OriginRpContext context) {
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(context.rpId())
                        .name(adminConsoleProperties.getWebauthn().getRpName())
                        .build())
                .credentialRepository(adminCredentialRepository)
                .origins(Set.of(context.origin()))
                .allowOriginPort(true)
                .allowOriginSubdomain(true)
                .build();
    }

    private OriginRpContext resolveOriginRpContext(HttpServletRequest request) {
        String origin = normalizeOrigin(request != null ? request.getHeader("Origin") : null);
        if (origin == null && request != null) {
            origin = normalizeOrigin(buildOriginFromRequest(request));
        }
        if (origin == null) {
            throw new IllegalStateException("Admin WebAuthn request origin is missing.");
        }
        String host = extractHost(origin);

        Map<String, String> originRpMappings = adminConsoleProperties.getWebauthn().getOriginRpMappings();
        String mappedRpId = originRpMappings.get(origin);
        if (mappedRpId != null && !mappedRpId.isBlank()) {
            String rpId = mappedRpId.trim().toLowerCase(Locale.ROOT);
            if (!isRpIdCompatibleWithOrigin(origin, rpId)) {
                throw new IllegalStateException("Admin WebAuthn RP ID is not compatible with origin. origin="
                        + origin + ", rpId=" + rpId);
            }
            return new OriginRpContext(origin, rpId);
        }

        Set<String> allowedOrigins = new LinkedHashSet<>();
        adminConsoleProperties.getWebauthn().getAllowedOrigins().stream()
                .filter(v -> v != null && !v.isBlank())
                .map(this::normalizeOrigin)
                .filter(v -> v != null && !v.isBlank())
                .forEach(allowedOrigins::add);
        if (allowedOrigins.contains(origin)) {
            String rpIdFromOrigin = host;
            if (rpIdFromOrigin != null && !rpIdFromOrigin.isBlank()) {
                return new OriginRpContext(origin, rpIdFromOrigin);
            }
            String fallbackRpId = adminConsoleProperties.getWebauthn().getRpId();
            if (fallbackRpId != null && !fallbackRpId.isBlank()) {
                return new OriginRpContext(origin, fallbackRpId.trim());
            }
            throw new IllegalStateException("Admin WebAuthn RP ID is not configured for allowed origin fallback.");
        }
        throw new IllegalStateException("Origin is not allowed for Admin WebAuthn: " + origin);
    }

    private String buildOriginFromRequest(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private String normalizeOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        String value = origin.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (scheme == null || host == null) {
                return value;
            }
            boolean defaultPort = ("https".equals(scheme) && port == 443)
                    || ("http".equals(scheme) && port == 80)
                    || port == -1;
            return scheme + "://" + host + (defaultPort ? "" : ":" + port);
        } catch (RuntimeException e) {
            return value;
        }
    }

    private String extractHost(String origin) {
        try {
            URI uri = URI.create(origin);
            return uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isRpIdCompatibleWithOrigin(String origin, String rpId) {
        String host = extractHost(origin);
        if (host == null || host.isBlank() || rpId == null || rpId.isBlank()) {
            return false;
        }
        String normalizedRpId = rpId.toLowerCase(Locale.ROOT);
        return host.equals(normalizedRpId) || host.endsWith("." + normalizedRpId);
    }

    public record RegistrationFinishPayload(String credentialId, String publicKeyCose, long signatureCount, String rpId) {
    }

    public record AssertionFinishPayload(String credentialId, long signatureCount, String rpId) {
    }

    private record OriginRpContext(String origin, String rpId) {
    }
}
