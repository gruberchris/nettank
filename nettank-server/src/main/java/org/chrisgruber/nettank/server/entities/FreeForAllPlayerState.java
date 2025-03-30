package org.chrisgruber.nettank.server.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeForAllPlayerState extends GameModePlayerState {
    private static final Logger logger = LoggerFactory.getLogger(FreeForAllPlayerState.class);

    public FreeForAllPlayerState(int playerId) {
        super(playerId);
    }
}
