package com.example.pogun.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AiSourceCacheService {

    private static final String CACHE_KEY_PREFIX = "cache:ai-source:";
    private static final String VERSION_KEY_PREFIX = "cache:ai-source:version:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai-source-cache.enabled:true}")
    private boolean enabled;

    public <T> T getOrLoad(String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        if (!enabled) {
            return loader.get();
        }
        String cacheKey = CACHE_KEY_PREFIX + key;
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey);
            if (payload != null && !payload.isBlank()) {
                return objectMapper.readValue(payload, type);
            }
        } catch (Exception ignored) {
        }

        T loaded = loader.get();
        try {
            String payload = objectMapper.writeValueAsString(loaded);
            redisTemplate.opsForValue().set(cacheKey, payload, ttl);
        } catch (Exception ignored) {
        }
        return loaded;
    }

    public String currentVersion(String namespace) {
        if (!enabled) {
            return "0";
        }
        String key = VERSION_KEY_PREFIX + namespace;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                redisTemplate.opsForValue().set(key, "1");
                return "1";
            }
            return value;
        } catch (Exception ignored) {
            return "0";
        }
    }

    public void bumpVersion(String namespace) {
        if (!enabled) {
            return;
        }
        String key = VERSION_KEY_PREFIX + namespace;
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value == null || value <= 0) {
                redisTemplate.opsForValue().set(key, "1");
            }
        } catch (Exception ignored) {
        }
    }
}
