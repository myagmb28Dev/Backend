package com.example.pogun.repository.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserSocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 UserSocialAccountRepository이다.
 */

@Repository
public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, UUID> {
    List<UserSocialAccount> findByUserOrderByCreatedAtAsc(User user);

    List<UserSocialAccount> findByUserAndLinkedTrueOrderByCreatedAtAsc(User user);

    Optional<UserSocialAccount> findByUserAndProvider(User user, String provider);
}
