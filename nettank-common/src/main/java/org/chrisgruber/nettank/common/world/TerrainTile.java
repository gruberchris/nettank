package org.chrisgruber.nettank.common.world;

public class TerrainTile {
    private TerrainType baseType;
    private TerrainType overlayType;
    private String visualOverlay; // Non-interactable visual layer (tank tracks, roads, etc)
    private TerrainState currentState;
    private long stateChangeTime;
    private long fireDuration;

    public TerrainTile(TerrainType baseType) {
        this.baseType = baseType;
        this.overlayType = null;
        this.visualOverlay = null;
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

    public TerrainType getOverlayType() {
        return overlayType;
    }

    public void setOverlayType(TerrainType overlayType) {
        this.overlayType = overlayType;
    }

    public boolean hasOverlay() {
        return overlayType != null;
    }

    public String getVisualOverlay() {
        return visualOverlay;
    }

    public void setVisualOverlay(String visualOverlay) {
        this.visualOverlay = visualOverlay;
    }

    public boolean hasVisualOverlay() {
        return visualOverlay != null && !visualOverlay.isEmpty();
    }

    public TerrainType getEffectiveType() {
        return overlayType != null ? overlayType : baseType;
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
        TerrainType effectiveType = getEffectiveType();
        return effectiveType.getSpeedModifier() * currentState.getSpeedModifier();
    }

    public boolean isPassable() {
        TerrainType effectiveType = getEffectiveType();
        return effectiveType.isPassable();
    }

    public boolean blocksBullets() {
        TerrainType effectiveType = getEffectiveType();
        return effectiveType.blocksBullets();
    }

    public boolean isDestructible() {
        TerrainType effectiveType = getEffectiveType();
        return effectiveType.isDestructible();
    }
}
