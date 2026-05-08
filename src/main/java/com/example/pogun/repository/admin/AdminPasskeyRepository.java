package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminPasskey;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminPasskeyRepository extends JpaRepository<AdminPasskey, UUID> {
    List<AdminPasskey> findByUserOrderByCreatedAtAsc(User user);
    Optional<AdminPasskey> findByCredentialId(String credentialId);
    Optional<AdminPasskey> findByCredentialIdAndRpId(String credentialId, String rpId);
    boolean existsByUser(User user);
    long deleteByUser(User user);
}
