package com.example.pogun.service.adminauth;

import com.example.pogun.entity.admin.AdminPasskey;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.admin.AdminPasskeyRepository;
import com.example.pogun.repository.user.UserRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminCredentialRepository implements CredentialRepository {

    private final AdminPasskeyRepository adminPasskeyRepository;
    private final UserRepository userRepository;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(adminPasskeyRepository::findByUserOrderByCreatedAtAsc)
                .orElseGet(java.util.List::of)
                .stream()
                .map(this::toDescriptor)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(User::getId)
                .map(UUID::toString)
                .map(value -> new ByteArray(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        try {
            UUID userId = UUID.fromString(new String(userHandle.getBytes(), StandardCharsets.UTF_8));
            return userRepository.findById(userId).map(User::getEmail);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        try {
            UUID userId = UUID.fromString(new String(userHandle.getBytes(), StandardCharsets.UTF_8));
            return adminPasskeyRepository.findByCredentialId(credentialId.getBase64Url())
                    .filter(passkey -> passkey.getUser().getId().equals(userId))
                    .map(this::toRegisteredCredential);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return adminPasskeyRepository.findByCredentialId(credentialId.getBase64Url())
                .map(this::toRegisteredCredential)
                .stream()
                .collect(Collectors.toSet());
    }

    private RegisteredCredential toRegisteredCredential(AdminPasskey passkey) {
        try {
            return RegisteredCredential.builder()
                    .credentialId(ByteArray.fromBase64Url(passkey.getCredentialId()))
                    .userHandle(new ByteArray(passkey.getUser().getId().toString().getBytes(StandardCharsets.UTF_8)))
                    .publicKeyCose(new ByteArray(Base64.getUrlDecoder().decode(passkey.getPublicKeyCose())))
                    .signatureCount(passkey.getSignatureCount())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("저장된 PassKey credentialId를 복원할 수 없습니다.", e);
        }
    }

    private Optional<PublicKeyCredentialDescriptor> toDescriptor(AdminPasskey passkey) {
        try {
            return Optional.of(PublicKeyCredentialDescriptor.builder()
                    .id(ByteArray.fromBase64Url(passkey.getCredentialId()))
                    .build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
