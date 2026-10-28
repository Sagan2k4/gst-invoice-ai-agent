package com.gstagent.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.token-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, Instant> expiries = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, Duration ttl) {
        expiries.put(key, Instant.now().plus(ttl));
    }

    @Override
    public boolean exists(String key) {
        Instant expiry = expiries.get(key);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            expiries.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public void delete(String key) {
        expiries.remove(key);
    }
}
