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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminWebAuthnService {

    private final AdminConsoleProperties adminConsoleProperties;
    private final AdminCredentialRepository adminCredentialRepository;

    public PublicKeyCredentialCreationOptions startRegistration(User user, HttpServletRequest request) {
        RelyingParty rp = relyingParty(request);
        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getEmail())
                .id(new ByteArray(user.getId().toString().getBytes(StandardCharsets.UTF_8)))
                .build();
        return rp.startRegistration(StartRegistrationOptions.builder()
                .user(userIdentity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder().build())
                .extensions(RegistrationExtensionInputs.builder().build())
                .build());
    }

    public AssertionRequest startAssertion(User user, HttpServletRequest request) {
        return relyingParty(request).startAssertion(StartAssertionOptions.builder()
                .username(user.getEmail())
                .build());
    }

    public RegistrationFinishPayload finishRegistration(String requestJson, String credentialJson, HttpServletRequest request)
            throws RegistrationFailedException, com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
        PublicKeyCredentialCreationOptions registrationRequest = PublicKeyCredentialCreationOptions.fromJson(requestJson);
        var response = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
        var result = relyingParty(request).finishRegistration(FinishRegistrationOptions.builder()
                .request(registrationRequest)
                .response(response)
                .build());
        return new RegistrationFinishPayload(
                result.getKeyId().getId().getBase64Url(),
                result.getPublicKeyCose().getBase64Url(),
                result.getSignatureCount()
        );
    }

    public AssertionFinishPayload finishAssertion(String requestJson, String credentialJson, HttpServletRequest request)
            throws AssertionFailedException, com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
        AssertionRequest assertionRequest = AssertionRequest.fromJson(requestJson);
        var response = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        var result = relyingParty(request).finishAssertion(FinishAssertionOptions.builder()
                .request(assertionRequest)
                .response(response)
                .build());
        if (!result.isSuccess()) {
            throw new AssertionFailedException("PassKey 검증에 실패했습니다.");
        }
        return new AssertionFinishPayload(
                result.getCredentialId().getBase64Url(),
                result.getSignatureCount()
        );
    }

    private RelyingParty relyingParty(HttpServletRequest request) {
        String rpId = resolveRpId(request);
        Set<String> origins = resolveOrigins(request);
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(rpId)
                        .name(adminConsoleProperties.getWebauthn().getRpName())
                        .build())
                .credentialRepository(adminCredentialRepository)
                .origins(origins)
                .allowOriginPort(true)
                .allowOriginSubdomain(true)
                .build();
    }

    private String resolveRpId(HttpServletRequest request) {
        String configured = adminConsoleProperties.getWebauthn().getRpId();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Admin WebAuthn RP ID is not configured.");
        }
        return configured.trim();
    }

    private Set<String> resolveOrigins(HttpServletRequest request) {
        Set<String> origins = new LinkedHashSet<>();
        adminConsoleProperties.getWebauthn().getAllowedOrigins().stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .forEach(origins::add);
        if (origins.isEmpty()) {
            throw new IllegalStateException("Admin WebAuthn allowed origins are not configured.");
        }
        return origins;
    }

    public record RegistrationFinishPayload(String credentialId, String publicKeyCose, long signatureCount) {
    }

    public record AssertionFinishPayload(String credentialId, long signatureCount) {
    }
}
