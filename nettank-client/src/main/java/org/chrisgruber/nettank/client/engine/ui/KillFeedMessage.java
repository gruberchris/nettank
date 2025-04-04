package org.chrisgruber.nettank.client.engine.ui;

public record KillFeedMessage(String message, long expiryTimeMillis) {
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTimeMillis;
    }
}
