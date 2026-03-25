package com.example.pogun.repository.bookmark;

import com.example.pogun.entity.bookmark.NoticeBookmark;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 NoticeBookmarkRepository이다.
 */

@Repository
public interface NoticeBookmarkRepository extends JpaRepository<NoticeBookmark, UUID> {
    Optional<NoticeBookmark> findByUserAndNotice(User user, PetNotice notice);

    List<NoticeBookmark> findByUserOrderByCreatedAtDesc(User user);

    List<NoticeBookmark> findByNotice(PetNotice notice);

    long countByNotice(PetNotice notice);

    void deleteByNotice(PetNotice notice);
}
