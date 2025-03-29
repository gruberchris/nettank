package org.chrisgruber.nettank.common.gamemode;

public enum GameWinCondition {
    NONE,                       // No win condition, game continues until manually stopped
    TIMED_ROUND,                // Round ends after a set time limit
    FIRST_TO_REACH_SCORE,       // First player or team to reach a certain score wins
    SURVIVAL,                   // Last player or team remaining wins
}
