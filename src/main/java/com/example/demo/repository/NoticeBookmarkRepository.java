package com.example.demo.repository;

import com.example.demo.entity.NoticeBookmark;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeBookmarkRepository extends JpaRepository<NoticeBookmark, UUID> {
    Optional<NoticeBookmark> findByUserAndNotice(User user, PetNotice notice);

    List<NoticeBookmark> findByUserOrderByCreatedAtDesc(User user);

    List<NoticeBookmark> findByNotice(PetNotice notice);

    long countByNotice(PetNotice notice);
}
