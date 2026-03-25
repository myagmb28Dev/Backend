package com.example.pogun.repository.ai;

import com.example.pogun.entity.ai.AiPhoto;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 AiPhotoRepository이다.
 */

@Repository
public interface AiPhotoRepository extends JpaRepository<AiPhoto, UUID> {
    Optional<AiPhoto> findByIdAndAuthor(UUID id, User author);
}