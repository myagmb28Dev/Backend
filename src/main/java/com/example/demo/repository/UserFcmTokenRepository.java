package com.example.demo.repository;

import com.example.demo.entity.User;
import com.example.demo.entity.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, UUID> {
    Optional<UserFcmToken> findByToken(String token);

    List<UserFcmToken> findByUserAndActiveTrueOrderByUpdatedAtDesc(User user);
}
