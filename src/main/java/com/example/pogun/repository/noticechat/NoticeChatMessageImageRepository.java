package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NoticeChatMessageImageRepository extends JpaRepository<NoticeChatMessageImage, UUID> {
}
