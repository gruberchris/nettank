package org.chrisgruber.nettank.common.util;

public enum GameState {
    CONNECTING, // Client attempting connection
    WAITING,    // Server waiting for players
    COUNTDOWN,  // Round about to start
    PLAYING,    // Round in progress
    ROUND_OVER, // Round finished, showing results
    ERROR       // Connection error or other fatal issue
}