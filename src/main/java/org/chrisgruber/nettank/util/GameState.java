package org.chrisgruber.nettank.util;

public enum GameState {
    CONNECTING,    // Client is attempting to connect
    WAITING,       // Waiting for enough players to start
    COUNTDOWN,     // Short countdown before the round begins
    PLAYING,       // Round is in progress
    ROUND_OVER     // Round finished, showing winner/score
}
