package com.example.pogun.repository.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, UUID> {
    boolean existsByFollowerAndFollowing(User follower, User following);

    Optional<UserFollow> findByFollowerAndFollowing(User follower, User following);

    List<UserFollow> findByFollowerOrderByCreatedAtDesc(User follower);

    List<UserFollow> findByFollowingOrderByCreatedAtDesc(User following);
}
