package org.chrisgruber.nettank.common.gamemode;

public enum GameStartCondition {
    IMMEDIATE,                      // Game starts immediately
    COUNTDOWN,                      // Game starts after a specified countdown
    COUNT_OF_PLAYERS,               // Game starts when a certain number of players have joined
}
