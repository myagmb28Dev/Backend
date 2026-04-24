package com.example.pogun.config.presence;

import com.example.pogun.service.presence.InMemoryPresenceSessionStore;
import com.example.pogun.service.presence.PresenceSessionStore;
import com.example.pogun.service.presence.RedisPresenceSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PresenceStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "app.presence.store", havingValue = "redis")
    public PresenceSessionStore redisPresenceSessionStore(StringRedisTemplate redisTemplate) {
        return new RedisPresenceSessionStore(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "app.presence.store", havingValue = "memory", matchIfMissing = true)
    public PresenceSessionStore inMemoryPresenceSessionStore() {
        return new InMemoryPresenceSessionStore();
    }
}
