package org.chrisgruber.nettank.client.engine.ui;

public record KillFeedMessage(StatusMessageKind statusMessageKind, String message, long expiryTimeMillis) {
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTimeMillis;
    }

    public StatusMessageKind getStatusMessageKind() {
        return statusMessageKind;
    }
}
