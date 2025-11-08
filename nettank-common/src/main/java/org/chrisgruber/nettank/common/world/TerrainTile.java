package org.chrisgruber.nettank.common.world;

public class TerrainTile {
    private TerrainType baseType;
    private TerrainState currentState;
    private long stateChangeTime;
    private long fireDuration;

    public TerrainTile(TerrainType baseType) {
        this.baseType = baseType;
        this.currentState = TerrainState.NORMAL;
        this.stateChangeTime = 0;
        this.fireDuration = 0;
    }

    public TerrainType getBaseType() {
        return baseType;
    }

    public void setBaseType(TerrainType baseType) {
        this.baseType = baseType;
    }

    public TerrainState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(TerrainState currentState) {
        this.currentState = currentState;
    }

    public long getStateChangeTime() {
        return stateChangeTime;
    }

    public void setStateChangeTime(long stateChangeTime) {
        this.stateChangeTime = stateChangeTime;
    }

    public long getFireDuration() {
        return fireDuration;
    }

    public void setFireDuration(long fireDuration) {
        this.fireDuration = fireDuration;
    }

    public float getEffectiveSpeedModifier() {
        return baseType.getSpeedModifier() * currentState.getSpeedModifier();
    }

    public boolean isPassable() {
        return baseType.isPassable();
    }
}
