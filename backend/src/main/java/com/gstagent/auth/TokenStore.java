package com.gstagent.auth;

import java.time.Duration;

/**
 * Short-lived key store for JWT bookkeeping: active refresh tokens and blacklisted access tokens.
 * Backed by Redis in deployed environments, by an in-memory map for local development.
 */
public interface TokenStore {

    void put(String key, String value, Duration ttl);

    boolean exists(String key);

    void delete(String key);
}
