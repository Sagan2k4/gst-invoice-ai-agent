package com.gstagent.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.token-store", havingValue = "redis")
public class RedisTokenStore implements TokenStore {

    private static final String PREFIX = "gst-agent:auth:";

    private final StringRedisTemplate redis;

    @Override
    public void put(String key, String value, Duration ttl) {
        redis.opsForValue().set(PREFIX + key, value, ttl);
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + key));
    }

    @Override
    public void delete(String key) {
        redis.delete(PREFIX + key);
    }
}
