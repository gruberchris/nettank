package org.chrisgruber.nettank.client.game;

import org.chrisgruber.nettank.client.engine.core.GameEngine;
import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.client.engine.network.GameClient;
import org.chrisgruber.nettank.client.engine.network.NetworkCallbackHandler;
import org.chrisgruber.nettank.client.engine.ui.KillFeedMessage;
import org.chrisgruber.nettank.client.engine.ui.HealthBar;
import org.chrisgruber.nettank.client.engine.ui.StatusMessageKind;
import org.chrisgruber.nettank.client.engine.ui.UIManager;
import org.chrisgruber.nettank.client.game.effects.ExplosionEffect;
import org.chrisgruber.nettank.client.game.effects.FlameEffect;
import org.chrisgruber.nettank.client.game.effects.SmokeEffect;
import org.chrisgruber.nettank.client.game.entities.ClientBullet;
import org.chrisgruber.nettank.client.game.entities.ClientTank;
import org.chrisgruber.nettank.client.game.world.ClientGameMap;
import org.chrisgruber.nettank.common.entities.BulletData;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.util.Colors;
import org.chrisgruber.nettank.common.util.GameState;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*; // For JOptionPane on error/disconnect
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.system.MemoryUtil.NULL;

public class TankBattleGame extends GameEngine implements NetworkCallbackHandler {

    private static final Logger logger = LoggerFactory.getLogger(TankBattleGame.class);

    private static final float UI_TEXT_SCALE_NORMAL = 0.5f; // Base size
    private static final float UI_TEXT_SCALE_LARGE = 0.85f;  // Larger size
    private static final float UI_TEXT_SCALE_SECONDARY_STATUS = 0.95f; // Slightly smaller for secondary status
    private static final float UI_TEXT_SCALE_STATUS = 1.0f; // Size for status messages like HIT POINTS
    private static final float UI_TEXT_SCALE_ANNOUNCEMENT = 1.3f; // For large center messages like announcements
    private static final float UI_TEXT_SCALE_KILL_FEED = 0.75f; // Scale for kill feed messages

    private static final long KILL_FEED_DISPLAY_TIME_MS = 10000L; // 10 seconds display time

    private Shader shader;
    private Renderer renderer;
    private Camera camera;
    private UIManager uiManager;
    private HealthBar healthBar;

    // Textures
    private Texture tankTexture; // STANDARD hull (also used for the wreck tint and as a portrait/UI fallback)
    private Texture bulletTexture;
    private final Map<org.chrisgruber.nettank.common.entities.TankType, Texture> hullTexturesByType = new EnumMap<>(org.chrisgruber.nettank.common.entities.TankType.class);
    private final Map<org.chrisgruber.nettank.common.entities.TankType, Texture> turretTexturesByType = new EnumMap<>(org.chrisgruber.nettank.common.entities.TankType.class);
    private final Map<org.chrisgruber.nettank.common.entities.PowerUpType, Texture> powerUpIconTextures = new EnumMap<>(org.chrisgruber.nettank.common.entities.PowerUpType.class);

    // Effect textures (Phase 5/7 sprite sheets)
    private final List<Texture> muzzleFlashFrameTextures = new ArrayList<>();
    private final List<Texture> sparkFrameTextures = new ArrayList<>();
    private Texture tracerTexture;
    private Texture trackMarkTexture;
    private Texture scorchDecalTexture;
    private Texture auraRingTexture;

    // Sound engine
    private final org.chrisgruber.nettank.client.engine.audio.AudioManager audioManager =
            new org.chrisgruber.nettank.client.engine.audio.AudioManager();
    private boolean engineSoundActive = false;

    // Terrain Textures
    private Texture summerGrassTexture;
    private Texture mudFieldTexture;
    private Texture dirtFieldTexture;
    private Texture forestFloorTexture;
    private Texture desertSandTexture;
    private Texture shallowWaterTexture;
    private Texture summerTreeTexture;
    private Texture hillTexture;
    private Texture rocksTexture;
    private Texture scorchedTerrainTexture;

    // Game Objects
    private ClientGameMap gameMap;
    private final Map<Integer, ClientTank> tanks = new ConcurrentHashMap<>();
    private final List<ClientBullet> bullets = new CopyOnWriteArrayList<>();

    // Player specific
    private int localPlayerId = -1;
    private ClientTank localTank = null;
    private int localAmmoCount = -1; // -1 = unlimited (mode never sends AMO)
    private org.chrisgruber.nettank.common.entities.TankType selectedTankType = org.chrisgruber.nettank.common.entities.TankType.STANDARD;
    private static final float CLOAK_FADE_PER_SECOND = 2.5f; // ~0.4 s fade
    private static final float OWN_CLOAK_ALPHA = 0.5f;
    // Fixed gunmetal tint for turrets so they contrast against the team-colored hull
    // and their aim direction stays readable regardless of team color.
    private static final Vector3f TURRET_NEUTRAL_TINT = new Vector3f(0.55f, 0.57f, 0.6f);

    // Lobby tank selection screen (Phase 9)
    private org.chrisgruber.nettank.client.engine.ui.TankSelectionScreen selectionScreen;
    private boolean selectionConfirmed = false;
    // Lobby readiness: the round starts only when every player is ready
    private final Map<Integer, Boolean> lobbyReadyByPlayerId = new ConcurrentHashMap<>();
    private volatile long countdownEndTimeMillis = 0;
    private long lastCountdownSecond = -1;
    private long countdownTickTimeMillis = 0;

    // Directional armor HUD state (owner-only, fed by ARM messages); index = ArmorSide.ordinal()
    private org.chrisgruber.nettank.client.engine.ui.ArmorIndicator armorIndicator;
    private final int[] localArmor = {-1, -1, -1, -1}; // -1 = no snapshot received yet
    private final long[] armorHitFlashTimes = new long[4];

    // Power-ups: battlefield pickups and active buffs (auras + HUD countdowns)
    private record ClientPowerUp(int id, org.chrisgruber.nettank.common.entities.PowerUpType type,
                                 Vector2f position, long spawnTime) {}
    private record ActiveBuff(org.chrisgruber.nettank.common.entities.PowerUpType type, long endTimeMillis) {}
    private final Map<Integer, ClientPowerUp> powerUps = new ConcurrentHashMap<>();
    private final Map<Integer, Map<org.chrisgruber.nettank.common.entities.PowerUpType.Category, ActiveBuff>> activeBuffsByPlayerId = new ConcurrentHashMap<>();
    private static final float POWERUP_RENDER_SIZE = 24.0f;

    // Hit feedback effects
    private final List<org.chrisgruber.nettank.client.game.effects.HitSparkEffect> hitSparks = new CopyOnWriteArrayList<>();
    private final List<org.chrisgruber.nettank.client.game.effects.FloatingTextEffect> floatingTexts = new CopyOnWriteArrayList<>();
    private static final long HIT_SPARK_DURATION_MS = 250;
    private static final float HIT_SPARK_RENDER_SIZE = 26.0f;
    private static final long FLOATING_TEXT_DURATION_MS = 1200;

    // Effects quality (from game-config.json): LOW drops decals, dust, and vignettes
    private enum EffectsQuality { OFF, LOW, FULL }
    private EffectsQuality effectsQuality = EffectsQuality.FULL;
    private boolean effectsAtLeastLow() { return effectsQuality != EffectsQuality.OFF; }
    private boolean effectsFull() { return effectsQuality == EffectsQuality.FULL; }

    // Phase 7 visual effects
    private final List<org.chrisgruber.nettank.client.game.effects.MuzzleFlashEffect> muzzleFlashes = new CopyOnWriteArrayList<>();
    private final java.util.Deque<org.chrisgruber.nettank.client.game.effects.TrackMarkEffect> trackMarks = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final List<org.chrisgruber.nettank.client.game.effects.DustPuffEffect> dustPuffs = new CopyOnWriteArrayList<>();
    private final List<org.chrisgruber.nettank.client.game.effects.DustPuffEffect> exhaustPuffs = new CopyOnWriteArrayList<>();
    private Texture exhaustTexture;
    private final java.util.Deque<org.chrisgruber.nettank.client.game.effects.ScorchDecal> scorchDecals = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final Map<Integer, Vector2f> lastGroundEffectPosition = new ConcurrentHashMap<>();
    private long lastHullSmokeTime = 0;
    private org.chrisgruber.nettank.client.engine.ui.VignetteOverlay vignetteOverlay;
    private long lastOwnHitTime = 0;
    private boolean lastOwnHitCrit = false;
    private static final int TRACK_MARK_CAP = 400;
    private static final int SCORCH_DECAL_CAP = 200;
    private static final float GROUND_EFFECT_SPACING = 12.0f;
    private static final long MUZZLE_FLASH_DURATION_MS = 120;
    private static final long RESPAWN_SHIMMER_DURATION_MS = 500;
    private static final long OWN_HIT_VIGNETTE_MS = 450;
    private static final float EXPLOSION_SHAKE_RANGE = 400.0f;
    private final String playerName;
    private boolean isSpectating = false;
    private long roundStartTimeMillis = 0;
    private long finalElapsedTimeMillis = -1;
    private int playerKills = 0;
    private final List<KillFeedMessage> killFeedMessages = new CopyOnWriteArrayList<>();

    // Networking
    private GameClient gameClient;
    private final String serverIp;
    private final int serverPort;

    // Game State
    private volatile GameState currentGameState = GameState.CONNECTING;
    private final List<String> announcements = new CopyOnWriteArrayList<>();
    private long lastAnnouncementTime = 0;
    private static final long ANNOUNCEMENT_DISPLAY_TIME_MS = 5000;

    // Input Game State
    private boolean prevKeyW = false;
    private boolean prevKeyS = false;
    private boolean prevKeyA = false;
    private boolean prevKeyD = false;
    private float prevTurretTurn = 0.0f;

    // Config
    public static final float VIEW_RANGE = 400.0f;

    // Game world map
    private int mapWidthTiles = -1;
    private int mapHeightTiles = -1;
    private float mapTileSize = -1.0f;
    private String receivedTerrainData = null;
    private boolean mapInitialized = false;
    private volatile boolean mapInfoReceivedForProcessing = false;
    private volatile boolean terrainInfoReceivedForProcessing = false;

    // Tank explosion effect
    private final List<Texture> explosionFrameTextures = new ArrayList<>();
    private final List<ExplosionEffect> activeExplosions = new CopyOnWriteArrayList<>();
    private static final int EXPLOSION_TOTAL_FRAMES = 8; // 8 animation frames
    private static final long EXPLOSION_DURATION_MS = 600; // Adjust duration as needed (e.g., 600ms for 8 frames)
    private static final float EXPLOSION_RENDER_SIZE = 80.0f; // How big to draw it
    private static final String EXPLOSION_FILENAME_PREFIX = "textures/explosion/Explosion_"; // Base name
    private static final String EXPLOSION_FILENAME_SUFFIX = ".png"; // File extension

    // Flame effect
    private final List<Texture> flameFrameTextures = new ArrayList<>();
    private final List<FlameEffect> activeFlames = new CopyOnWriteArrayList<>();
    private static final int FLAME_TOTAL_FRAMES = 8;
    private static final long FLAME_DURATION_MS = 1000; // Flames last longer? (1 second)
    private static final float FLAME_RENDER_SIZE = 65.0f; // Flames slightly smaller?
    private static final String FLAME_FILENAME_PREFIX = "textures/flame/Flame_"; // Adjust path/prefix
    private static final String FLAME_FILENAME_SUFFIX = ".png";

    // Terrain tile fire (driven by TST messages from the server)
    private record TileStateUpdate(int x, int y, org.chrisgruber.nettank.common.world.TerrainState state) {}
    private final java.util.Queue<TileStateUpdate> pendingTerrainStateChanges = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<Long, FlameEffect> tileFireEffects = new ConcurrentHashMap<>();
    private static final float TILE_FIRE_RENDER_SIZE = 40.0f;

    // Smoke effect
    private final List<Texture> smokeFrameTextures = new ArrayList<>();
    private final Map<Integer, SmokeEffect> activeSmokeEffects = new ConcurrentHashMap<>();
    private static final int SMOKE_TOTAL_FRAMES = 3;
    private static final long SMOKE_FRAME_DURATION_MS = 150; // Adjust speed (e.g., 150ms per frame)
    private static final float SMOKE_RENDER_SIZE = 55.0f;   // Slightly smaller than explosion? 35.0f
    private static final String SMOKE_FILENAME_PREFIX = "textures/smoke/Smoke_"; // Base name
    private static final String SMOKE_FILENAME_SUFFIX = ".png"; // File extension

    /**
     * Constructor for the Tank Battle Game.
     */
    public TankBattleGame(String hostIp, int port, String playerName, String title, int width, int height) {
        this(hostIp, port, playerName, "STANDARD", title, width, height);
    }

    public TankBattleGame(String hostIp, int port, String playerName, String initialTankType, String title, int width, int height) {
        this(hostIp, port, playerName, initialTankType, "FULL", title, width, height);
    }

    public TankBattleGame(String hostIp, int port, String playerName, String initialTankType, String effectsQuality, String title, int width, int height) {
        super(title, width, height);
        this.serverIp = hostIp;
        this.serverPort = port;
        this.playerName = playerName;
        this.selectedTankType = org.chrisgruber.nettank.common.entities.TankType.fromString(initialTankType);
        try {
            this.effectsQuality = EffectsQuality.valueOf(effectsQuality.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            this.effectsQuality = EffectsQuality.FULL;
        }
    }

    // --- Implementation of Abstract Methods from GameEngine ---

    @Override
    public void initGame() {
        logger.info("Initializing TankBattleGame...");
        try {
            glEnable(GL_BLEND); // Enable blending
            // Set the blend function for standard alpha blending
            // Formula: finalColor = (sourceColor * sourceAlpha) + (destColor * (1 - sourceAlpha))
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            logger.debug("OpenGL blending enabled (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA).");

            shader = new Shader("/shaders/quad.vert", "/shaders/quad.frag");
            shader.bind();

            renderer = new Renderer();
            camera = new Camera(windowWidth, windowHeight);
            uiManager = new UIManager();
            healthBar = new HealthBar();
            armorIndicator = new org.chrisgruber.nettank.client.engine.ui.ArmorIndicator();
            vignetteOverlay = new org.chrisgruber.nettank.client.engine.ui.VignetteOverlay();
            selectionScreen = new org.chrisgruber.nettank.client.engine.ui.TankSelectionScreen();

            // Load game textures
            logger.debug("Loading textures...");
            bulletTexture = new Texture("textures/bullet.png");
            tankTexture = new Texture("textures/tank.png");
            uiManager.loadFontTexture("textures/font.png");
            logger.debug("Textures loaded.");

            // Per-type hull + turret textures (turret rotates independently of the hull)
            logger.debug("Loading per-type tank hull/turret textures...");
            Texture turretStandard = new Texture("textures/turret.png");
            hullTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.STANDARD, tankTexture);
            turretTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.STANDARD, turretStandard);
            hullTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.HEAVY, new Texture("textures/tank_heavy.png"));
            turretTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.HEAVY, new Texture("textures/turret_heavy.png"));
            hullTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.LIGHT, new Texture("textures/tank_light.png"));
            turretTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.LIGHT, new Texture("textures/turret_light.png"));
            hullTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.STEALTH, new Texture("textures/tank_stealth.png"));
            turretTexturesByType.put(org.chrisgruber.nettank.common.entities.TankType.STEALTH, new Texture("textures/turret_stealth.png"));

            // Power-up pickup icons (tinted per-category at render time)
            logger.debug("Loading power-up icon textures...");
            for (var type : org.chrisgruber.nettank.common.entities.PowerUpType.values()) {
                powerUpIconTextures.put(type, new Texture("textures/powerups/" + type.name().toLowerCase() + ".png"));
            }

            // Effect sprites
            logger.debug("Loading effect sprite textures...");
            for (int i = 0; i < 3; i++) {
                muzzleFlashFrameTextures.add(new Texture("textures/effects/muzzle_flash_" + i + ".png"));
            }
            for (int i = 0; i < 4; i++) {
                sparkFrameTextures.add(new Texture("textures/effects/spark_" + i + ".png"));
            }
            tracerTexture = new Texture("textures/effects/tracer.png");
            trackMarkTexture = new Texture("textures/effects/track_mark.png");
            scorchDecalTexture = new Texture("textures/effects/scorch.png");
            auraRingTexture = new Texture("textures/effects/aura_ring.png");
            exhaustTexture = new Texture("textures/effects/exhaust.png");

            // Sound engine: OpenAL init is independent of OpenGL and never blocks the game
            // if no audio device is available (isInitialized() gates all playback).
            audioManager.init();
            audioManager.loadSound("shoot", "sounds/shoot.ogg");
            audioManager.loadSound("hit", "sounds/hit.ogg");
            audioManager.loadSound("hit_crit", "sounds/hit_crit.ogg");
            audioManager.loadSound("destroyed", "sounds/destroyed.ogg");
            audioManager.loadSound("powerup_pickup", "sounds/powerup_pickup.ogg");
            audioManager.loadSound("ui_select", "sounds/ui_select.ogg");
            audioManager.loadSound("ui_confirm", "sounds/ui_confirm.ogg");
            audioManager.loadSound("countdown_tick", "sounds/countdown_tick.ogg");
            audioManager.loadEngineLoop("sounds/engine_loop.ogg");

            // --- Load Individual Explosion Frames ---
            logger.debug("Loading explosion frame textures...");
            boolean explosionLoadSuccess = true;
            for (int i = 0; i < EXPLOSION_TOTAL_FRAMES; i++) {
                String filename = EXPLOSION_FILENAME_PREFIX + i + EXPLOSION_FILENAME_SUFFIX;
                try {
                    logger.trace("Loading explosion frame: {}", filename);
                    Texture frameTexture = new Texture(filename);
                    explosionFrameTextures.add(frameTexture);
                } catch (IOException e) {
                    logger.error("Failed to load explosion frame texture: {}", filename, e);
                    explosionLoadSuccess = false;
                    // Option 1: Stop loading further frames
                    // break;
                    // Option 2: Continue, but log that the animation will be incomplete
                    // (Need to handle potential errors during rendering/effect creation later if continuing)
                    // For simplicity, let's break on first failure
                    break;
                }
            }
            if (explosionLoadSuccess && explosionFrameTextures.size() == EXPLOSION_TOTAL_FRAMES) {
                logger.debug("Successfully loaded {} explosion frame textures.", EXPLOSION_TOTAL_FRAMES);
            } else {
                logger.error("Failed to load all explosion frames. Animation may be incomplete or broken.");
                // You might want to throw an exception or handle this more gracefully
                // For now, it will proceed with however many frames loaded successfully before failure.
                // Clear the list if loading failed partway to prevent using incomplete data?
                // if (!explosionLoadSuccess) explosionFrameTextures.clear(); // Example cleanup
            }
            // ---------------------------------------

            // --- Load Flame Frames ---
            logger.debug("Loading flame frame textures...");
            boolean flameLoadSuccess = true;
            for (int i = 0; i < FLAME_TOTAL_FRAMES; i++) {
                String filename = FLAME_FILENAME_PREFIX + i + FLAME_FILENAME_SUFFIX;
                try {
                    logger.trace("Loading flame frame: {}", filename);
                    Texture frameTexture = new Texture(filename);
                    flameFrameTextures.add(frameTexture);
                } catch (IOException e) {
                    logger.error("Failed to load flame frame texture: {}", filename, e);
                    flameLoadSuccess = false;
                    break; // Stop loading if one fails
                }
            }
            if (flameLoadSuccess && flameFrameTextures.size() == FLAME_TOTAL_FRAMES) {
                logger.debug("Successfully loaded {} flame frame textures.", FLAME_TOTAL_FRAMES);
            } else {
                logger.error("Failed to load all flame frames. Flame effect may be broken.");
                // Consider clearing flameFrameTextures if loading failed?
            }
            // ---------------------------

            // --- Load Smoke Frame Textures ---
            logger.debug("Loading smoke frame textures...");
            boolean smokeLoadSuccess = true;
            for (int i = 0; i < SMOKE_TOTAL_FRAMES; i++) {
                String filename = SMOKE_FILENAME_PREFIX + i + SMOKE_FILENAME_SUFFIX;
                try {
                    logger.trace("Loading smoke frame: {}", filename);
                    Texture frameTexture = new Texture(filename);
                    smokeFrameTextures.add(frameTexture);
                } catch (IOException e) {
                    logger.error("Failed to load smoke frame texture: {}", filename, e);
                    smokeLoadSuccess = false;
                    break; // Stop loading on failure
                }
            }
            if (smokeLoadSuccess && smokeFrameTextures.size() == SMOKE_TOTAL_FRAMES) {
                logger.debug("Successfully loaded {} smoke frame textures.", SMOKE_TOTAL_FRAMES);
            } else {
                logger.error("Failed to load all smoke frames. Smoke effect disabled.");
                smokeFrameTextures.clear(); // Ensure list is empty if loading failed
            }
            // ----------------------------------


            // Setup projection matrix
            shader.bind();
            shader.setUniformMat4f("u_projection", camera.getProjectionMatrix());
            shader.setUniform1i("u_texture", 0); // Use texture unit 0

            // Start networking game client AFTER core graphics setup
            logger.info("Starting network client...");
            currentGameState = GameState.CONNECTING;
            gameClient = new GameClient(serverIp, serverPort, playerName, selectedTankType.name(), this);
            new Thread(gameClient, "GameClientThread").start();

        } catch (IOException e) {
            logger.error("Failed to initialize game resources", e);

            // Use SwingUtilities for UI thread safety with JOptionPane
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Failed to load game resources:\n" + e.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE));

            // Signal engine to close if init fails critically
            if (windowHandle != NULL) {
                glfwSetWindowShouldClose(windowHandle, true);
            } else {
                System.exit(1); // Force exit if window wasn't even created
            }

        } catch (Exception e) {
            logger.error("Unexpected error during game initialization", e);

            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Unexpected error initializing game:\n" + e.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE));

            if (windowHandle != NULL) {
                glfwSetWindowShouldClose(windowHandle, true);
            }
            else {
                System.exit(1);
            }
        }
    }

    @Override
    public void updateGame(float deltaTime) {
        // --- Update Active Explosions & Trigger Flames ---
        activeExplosions.removeIf(explosion -> {
            boolean explosionFinished = explosion.update(); // Update explosion animation
            if (explosionFinished) {
                logger.trace("Explosion finished at ({},{}), triggering flame.", explosion.getPosition().x, explosion.getPosition().y);
                // *** TRIGGER FLAME EFFECT HERE ***
                if (!flameFrameTextures.isEmpty()) { // Check if flame textures are loaded
                    try {
                        FlameEffect flame = new FlameEffect(
                                explosion.getPosition(), // Start flame at explosion's location
                                FLAME_DURATION_MS,
                                flameFrameTextures,      // Pass the list of flame textures
                                FLAME_RENDER_SIZE
                        );
                        activeFlames.add(flame); // Add to the list of active flames
                        logger.trace("Added new flame effect.");
                    } catch (Exception e) {
                        logger.error("Failed to create FlameEffect instance", e);
                    }
                } else {
                    logger.warn("Cannot create flame effect, flame textures not loaded or list is empty.");
                }
                // ********************************
            }
            return explosionFinished; // Return true to remove the explosion itself
        });
        // ----------------------------------------------

        // --- Update Active Flames ---
        activeFlames.removeIf(flame -> {
            boolean flameFinished = flame.update(); // Update flame animation
            if (flameFinished) {
                logger.trace("Flame effect finished at ({},{}).", flame.getPosition().x, flame.getPosition().y);
            }
            return flameFinished; // removeIf removes finished flames
        });
        // --------------------------------

        // --- Apply queued terrain state changes and sync tile fire visuals ---
        processTerrainStateChanges();
        updateTileFireEffects();

        // --- Ease cloak fades ---
        for (ClientTank tank : tanks.values()) {
            tank.updateAlpha(deltaTime, CLOAK_FADE_PER_SECOND);
        }

        // --- Hit feedback effects ---
        hitSparks.removeIf(org.chrisgruber.nettank.client.game.effects.HitSparkEffect::update);
        floatingTexts.removeIf(org.chrisgruber.nettank.client.game.effects.FloatingTextEffect::isFinished);

        // --- Phase 7 cosmetic effects ---
        muzzleFlashes.removeIf(org.chrisgruber.nettank.client.game.effects.MuzzleFlashEffect::update);
        trackMarks.removeIf(org.chrisgruber.nettank.client.game.effects.TrackMarkEffect::isFinished);
        dustPuffs.removeIf(org.chrisgruber.nettank.client.game.effects.DustPuffEffect::isFinished);
        exhaustPuffs.removeIf(org.chrisgruber.nettank.client.game.effects.DustPuffEffect::isFinished);
        scorchDecals.removeIf(org.chrisgruber.nettank.client.game.effects.ScorchDecal::isFinished);
        spawnGroundEffects();

        // --- Update Active Smoke Effects ---
        activeSmokeEffects.entrySet().removeIf(entry -> {
            SmokeEffect smoke = entry.getValue();
            if (smoke.isActive()) {
                smoke.update(); // Update animation frame
                return false; // Keep active smoke
            } else {
                // Remove stopped smoke effects
                logger.trace("Removed stopped smoke effect for player {}.", entry.getKey());
                return true;
            }
        });
        // ----------------------------------

        // Initialize the map only when both MAP_INFO and TERRAIN_INIT have been received
        if (mapInfoReceivedForProcessing && terrainInfoReceivedForProcessing && !mapInitialized) {
            initializeMapAndTextures();
            mapInfoReceivedForProcessing = false; // Reset the signal flags
            terrainInfoReceivedForProcessing = false;
        } else if (terrainInfoReceivedForProcessing && mapInitialized && gameMap != null && receivedTerrainData != null) {
            // New round: the server regenerated terrain, re-decode it into the existing map
            gameMap.reloadTerrain(receivedTerrainData);
            pendingTerrainStateChanges.clear();
            tileFireEffects.clear();
            powerUps.clear();
            activeBuffsByPlayerId.clear();
            terrainInfoReceivedForProcessing = false;
        }

        // Update local bullet positions for client-side prediction
        long now = System.currentTimeMillis();

        killFeedMessages.removeIf(msg -> now >= msg.expiryTimeMillis());

        // Update bullets and remove expired/out-of-bounds/terrain-blocked in a single pass
        for (ClientBullet bullet : bullets) {
            bullet.update(deltaTime);
        }
        
        bullets.removeIf(bullet -> {
            boolean expired = (now - bullet.getSpawnTime() >= BulletData.LIFETIME_MS);
            boolean outOfBounds = (mapInitialized && gameMap != null && gameMap.isOutOfBounds(bullet));
            boolean hitTerrain = (mapInitialized && gameMap != null && 
                                  gameMap.blocksBulletsAt(bullet.getPosition().x, bullet.getPosition().y));
            return expired || outOfBounds || hitTerrain;
        });

        // Update camera position to follow the local tank (if it exists)
        if (localTank != null) {
            camera.setPosition(localTank.getPosition().x(), localTank.getPosition().y());
            if (mapInitialized && gameMap != null) {
                camera.clampToWorldBounds(gameMap.getWorldWidth(), gameMap.getWorldHeight());
            }
            audioManager.setListenerPosition(localTank.getPosition().x(), localTank.getPosition().y());
        } else if (isSpectating && mapInitialized && gameMap != null) {
            camera.setPosition(gameMap.getWorldWidth() / 2.0f, gameMap.getWorldHeight() / 2.0f);
        } else if (!mapInitialized) {
            // Keep camera at default or 0,0 while map loads
            camera.setPosition(0,0);
        }

        camera.update(); // Update camera matrices

        // Update announcements display timer
        if (!announcements.isEmpty() && (now - lastAnnouncementTime > ANNOUNCEMENT_DISPLAY_TIME_MS)) {
            announcements.removeFirst();    // Remove the oldest announcement
            lastAnnouncementTime = announcements.isEmpty() ? 0 : now;   // Reset timer
        }
    }

    @Override
    public void storeMapInfo(int widthTiles, int heightTiles, float tileSize) {
        logger.info("Stored Map Info from network: WidthTiles={}, HeightTiles={}, TileSize={}", widthTiles, heightTiles, tileSize);
        this.mapWidthTiles = widthTiles;
        this.mapHeightTiles = heightTiles;
        this.mapTileSize = tileSize;
        this.mapInfoReceivedForProcessing = true; // Signal the main thread
    }

    @Override
    public void receiveTerrainData(int width, int height, String encodedData) {
        logger.info("Received terrain data from server: {}x{} tiles, {} bytes", width, height, encodedData.length());
        this.receivedTerrainData = encodedData;
        this.terrainInfoReceivedForProcessing = true; // Signal the main thread
    }

    @Override
    public void powerUpSpawned(int powerUpId, String type, float x, float y) {
        var powerUpType = org.chrisgruber.nettank.common.entities.PowerUpType.fromString(type);
        if (powerUpType == null) {
            logger.error("Received unknown power-up type '{}' for id {}", type, powerUpId);
            return;
        }
        powerUps.put(powerUpId, new ClientPowerUp(powerUpId, powerUpType, new Vector2f(x, y), System.currentTimeMillis()));
        logger.debug("Power-up spawned: {} ({}) at ({}, {})", powerUpId, type, x, y);
    }

    @Override
    public void powerUpRemoved(int powerUpId, String reason) {
        powerUps.remove(powerUpId);
        logger.debug("Power-up removed: {} ({})", powerUpId, reason);
    }

    @Override
    public void powerUpActivated(int playerId, String type, long durationMs) {
        var powerUpType = org.chrisgruber.nettank.common.entities.PowerUpType.fromString(type);
        if (powerUpType == null) return;

        activeBuffsByPlayerId
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(powerUpType.getCategory(), new ActiveBuff(powerUpType, System.currentTimeMillis() + durationMs));

        if (playerId == localPlayerId) {
            audioManager.playSound("powerup_pickup");
        }
        logger.debug("Power-up activated for player {}: {} ({} ms)", playerId, type, durationMs);
    }

    @Override
    public void powerUpEnded(int playerId, String type) {
        var powerUpType = org.chrisgruber.nettank.common.entities.PowerUpType.fromString(type);
        if (powerUpType == null) return;

        var buffs = activeBuffsByPlayerId.get(playerId);
        if (buffs != null) {
            buffs.remove(powerUpType.getCategory());
        }
        logger.debug("Power-up ended for player {}: {}", playerId, type);
    }

    private static Vector3f powerUpCategoryColor(org.chrisgruber.nettank.common.entities.PowerUpType.Category category) {
        return switch (category) {
            case DAMAGE -> new Vector3f(1.0f, 0.25f, 0.2f);
            case RELOAD -> new Vector3f(1.0f, 0.6f, 0.1f);
            case SPEED -> new Vector3f(0.25f, 0.55f, 1.0f);
            case AMMO -> new Vector3f(0.2f, 0.95f, 0.95f);
            case ARMOR -> new Vector3f(0.3f, 0.9f, 0.3f);
            case HEALTH -> new Vector3f(1.0f, 0.5f, 0.8f);
        };
    }

    @Override
    public void updatePlayerReady(int playerId, boolean ready) {
        lobbyReadyByPlayerId.put(playerId, ready);

        // Server echo is authoritative for our own ready state (e.g. revoked by a type change)
        if (playerId == localPlayerId) {
            selectionConfirmed = ready;
        }

        logger.debug("Lobby ready state: player {} -> {}", playerId, ready);
    }

    @Override
    public void updateTankVisibility(int playerId, boolean visible) {
        ClientTank tank = tanks.get(playerId);
        if (tank == null) {
            logger.warn("Received visibility update for unknown player ID: {}", playerId);
            return;
        }

        if (visible) {
            tank.setTargetAlpha(1.0f);
        } else {
            // Own tank stays faintly visible while cloaked; enemies fade out fully
            tank.setTargetAlpha(playerId == localPlayerId ? OWN_CLOAK_ALPHA : 0.0f);
        }

        logger.debug("Tank visibility update: player {} -> {}", playerId, visible);
    }

    @Override
    public void terrainStateChanged(int tileX, int tileY, String stateName) {
        try {
            var state = org.chrisgruber.nettank.common.world.TerrainState.valueOf(stateName);
            pendingTerrainStateChanges.add(new TileStateUpdate(tileX, tileY, state));
        } catch (IllegalArgumentException e) {
            logger.error("Received unknown terrain state '{}' for tile ({}, {})", stateName, tileX, tileY);
        }
    }

    @Override
    public void updateAmmoCount(int playerId, int ammoCount) {
        if (playerId == localPlayerId) {
            this.localAmmoCount = ammoCount;
            logger.debug("Updated local ammo count: {}", ammoCount);
        }
    }

    @Override
    public void updateShootCooldown(long cooldownRemainingMs) {
        if (localTank != null) {
            localTank.setCooldown(cooldownRemainingMs);
            logger.debug("Updated shoot cooldown for local tank: {}ms remaining", cooldownRemainingMs);
        } else {
            logger.warn("Received shoot cooldown update ({}ms remaining) but localTank is null. This may indicate an unexpected state or timing issue.", cooldownRemainingMs);
        }
    }

    private static long tileKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    // Drains TST updates queued by the network thread into the map (render thread only)
    private void processTerrainStateChanges() {
        if (gameMap == null) return;

        TileStateUpdate update;
        while ((update = pendingTerrainStateChanges.poll()) != null) {
            gameMap.onTerrainStateChanged(update.x(), update.y(), update.state());

            long key = tileKey(update.x(), update.y());
            if (update.state().hasVisualEffect()) {
                if (!tileFireEffects.containsKey(key) && !flameFrameTextures.isEmpty()) {
                    tileFireEffects.put(key, newTileFlame(update.x(), update.y()));
                }
            } else {
                tileFireEffects.remove(key);
            }
        }
    }

    // Tile fires loop until the tile stops burning, so finished flame animations are respawned
    private void updateTileFireEffects() {
        if (gameMap == null || tileFireEffects.isEmpty()) return;

        for (var entry : tileFireEffects.entrySet()) {
            long key = entry.getKey();
            int x = (int) (key >> 32);
            int y = (int) key;

            if (!gameMap.getTileState(x, y).hasVisualEffect()) {
                tileFireEffects.remove(key);
            } else if (entry.getValue().update() && !flameFrameTextures.isEmpty()) {
                tileFireEffects.put(key, newTileFlame(x, y));
            }
        }
    }

    private FlameEffect newTileFlame(int tileX, int tileY) {
        float tileSize = gameMap.getTileSize();
        Vector2f center = new Vector2f((tileX + 0.5f) * tileSize, (tileY + 0.5f) * tileSize);
        return new FlameEffect(center, FLAME_DURATION_MS, flameFrameTextures, TILE_FIRE_RENDER_SIZE);
    }

    private void initializeMapAndTextures() {
        if (mapInitialized) return; // Should not happen if logic is correct, but safe check

        logger.info("Main thread initializing map with dimensions: {}x{}, terrain data: {} bytes", 
            mapWidthTiles, mapHeightTiles, receivedTerrainData != null ? receivedTerrainData.length() : 0);
        try {
            // Create the map object (uses the stored dimensions and terrain data from server)
            this.gameMap = new ClientGameMap(mapWidthTiles, mapHeightTiles, receivedTerrainData);

            logger.debug("Main thread loading map textures...");
            summerGrassTexture = new Texture("textures/Summer_Grass.png");
            mudFieldTexture = new Texture("textures/Mud_Field.png");
            dirtFieldTexture = new Texture("textures/Dirt_Field.png");
            forestFloorTexture = new Texture("textures/Forest_Floor.png");
            desertSandTexture = new Texture("textures/Desert.png");
            shallowWaterTexture = new Texture("textures/Shallow_Water.png");
            summerTreeTexture = new Texture("textures/Summer_Tree.png");
            hillTexture = new Texture("textures/Hill.png");
            rocksTexture = new Texture("textures/Rocks.png");
            scorchedTerrainTexture = new Texture("textures/Scorched.png");
            logger.debug("Map textures loaded by main thread.");

            // Register terrain textures with the new terrain system
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.GRASS, summerGrassTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.DIRT, dirtFieldTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.MUD, mudFieldTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.SAND, desertSandTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.STONE, summerGrassTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.SHALLOW_WATER, shallowWaterTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.FOREST, summerTreeTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.HILL, hillTexture);
            gameMap.registerTerrainTexture(org.chrisgruber.nettank.common.world.TerrainType.ROCKS, rocksTexture);
            gameMap.registerStateOverlayTexture(org.chrisgruber.nettank.common.world.TerrainState.SCORCHED, scorchedTerrainTexture);
            logger.debug("Registered terrain textures for all types.");

            mapInitialized = true; // Mark map as fully ready
            logger.info("ClientGameMap and textures initialized successfully.");

        } catch (IOException e) {
            logger.error("Failed to initialize map or map textures on main thread", e);
            handleInitializationError("Failed to load map resources:\n" + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during map initialization on main thread", e);
            handleInitializationError("Unexpected error initializing map:\n" + e.getMessage());
        }
    }

    // Helper for error handling during initialization
    private void handleInitializationError(String message) {
        if (gameClient != null) gameClient.stop();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, "Initialization Error", JOptionPane.ERROR_MESSAGE));
        if (windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
    }


    @Override
    public void renderGame() {
        shader.bind();
        shader.setUniformMat4f("u_view", camera.getViewMatrix());
        shader.setUniform1i("u_texture", 0); // Ensure texture unit 0
        glActiveTexture(GL_TEXTURE0);

        // Render Map
        if (mapInitialized && gameMap != null) {
            float range = isSpectating ? Float.MAX_VALUE : VIEW_RANGE;
            Vector2f fogCenter = (localTank != null) ? localTank.getPosition() : null;
            // Ensure map textures are loaded before rendering
            if (summerGrassTexture != null) {
                gameMap.render(renderer, shader, summerGrassTexture, summerGrassTexture, camera, range, fogCenter);
            } else {
                logger.warn("Attempted to render map, but map textures are not loaded.");
            }
        } else if (!mapInitialized) {
            // Optionally render a "Loading Map..." message or just black background
            // logger.trace("Map not yet initialized, skipping map rendering.");
        }

        // Render Tanks
        Vector2f playerPos = (localTank != null) ? localTank.getPosition() : null;
        float renderRangeSq = isSpectating ? Float.MAX_VALUE : VIEW_RANGE * VIEW_RANGE;

        // --- Render Ground Decals (scorch marks, track marks, dust) ---
        if (effectsFull() && scorchDecalTexture != null && trackMarkTexture != null) {
            scorchDecalTexture.bind();
            for (var decal : scorchDecals) {
                shader.setUniform4f("u_tintColor", 0.65f, 0.55f, 0.5f, decal.getAlpha());
                renderer.drawQuad(decal.getPosition().x, decal.getPosition().y,
                        decal.getRenderSize(), decal.getRenderSize(), decal.getRotationDegrees(), shader);
            }

            trackMarkTexture.bind();
            for (var mark : trackMarks) {
                float alpha = mark.getAlpha();
                if (alpha <= 0.01f) continue;
                shader.setUniform4f("u_tintColor", 0.7f, 0.6f, 0.5f, alpha);
                renderer.drawQuad(mark.getLeftTrack().x, mark.getLeftTrack().y,
                        mark.getMarkSize(), mark.getMarkSize(), mark.getRotationDegrees(), shader);
                renderer.drawQuad(mark.getRightTrack().x, mark.getRightTrack().y,
                        mark.getMarkSize(), mark.getMarkSize(), mark.getRotationDegrees(), shader);
            }
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        }

        if (effectsFull() && !dustPuffs.isEmpty() && !smokeFrameTextures.isEmpty()) {
            smokeFrameTextures.getFirst().bind();
            for (var puff : dustPuffs) {
                shader.setUniform4f("u_tintColor", puff.getTint(), puff.getAlpha());
                renderer.drawQuad(puff.getPosition().x, puff.getPosition().y,
                        puff.getCurrentSize(), puff.getCurrentSize(), 0f, shader);
            }
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        }

        if (effectsFull() && !exhaustPuffs.isEmpty() && exhaustTexture != null) {
            exhaustTexture.bind();
            for (var puff : exhaustPuffs) {
                shader.setUniform4f("u_tintColor", puff.getTint(), puff.getAlpha());
                renderer.drawQuad(puff.getPosition().x, puff.getPosition().y,
                        puff.getCurrentSize(), puff.getCurrentSize(), 0f, shader);
            }
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // -------------------------

        // --- Render Power-Up Pickups ---
        if (!powerUps.isEmpty() && auraRingTexture != null) {
            long now = System.currentTimeMillis();

            for (ClientPowerUp powerUp : powerUps.values()) {
                if (!isObjectVisible(powerUp.position(), playerPos, renderRangeSq)) continue;

                float age = (now - powerUp.spawnTime()) / 1000.0f;
                float bobOffset = (float) Math.sin(age * 3.0f) * 3.0f;
                float pulse = 0.3f + 0.15f * (float) Math.sin(age * 5.0f);
                Vector3f color = powerUpCategoryColor(powerUp.type().getCategory());

                // Soft glow ring under the icon
                auraRingTexture.bind();
                shader.setUniform4f("u_tintColor", color, pulse);
                renderer.drawQuad(powerUp.position().x, powerUp.position().y + bobOffset,
                        POWERUP_RENDER_SIZE * 2.0f, POWERUP_RENDER_SIZE * 2.0f, 0f, shader);

                // Icon
                Texture icon = powerUpIconTextures.get(powerUp.type());
                if (icon != null) {
                    icon.bind();
                    shader.setUniform4f("u_tintColor", color, 1.0f);
                    renderer.drawQuad(powerUp.position().x, powerUp.position().y + bobOffset,
                            POWERUP_RENDER_SIZE, POWERUP_RENDER_SIZE, 0f, shader);
                }
            }
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // -------------------------

        // --- Render Buff Auras (pulsing colored ring under buffed tanks) ---
        if (!activeBuffsByPlayerId.isEmpty() && auraRingTexture != null) {
            auraRingTexture.bind();
            long now = System.currentTimeMillis();

            for (var entry : activeBuffsByPlayerId.entrySet()) {
                ClientTank tank = tanks.get(entry.getKey());
                if (tank == null || tank.getAlpha() <= 0.02f || entry.getValue().isEmpty()) continue;
                if (!isObjectVisible(tank.getPosition(), playerPos, renderRangeSq)) continue;

                var firstBuff = entry.getValue().values().iterator().next();
                Vector3f color = powerUpCategoryColor(firstBuff.type().getCategory());
                float pulse = 0.2f + 0.1f * (float) Math.sin(now / 150.0);

                shader.setUniform4f("u_tintColor", color, pulse);
                renderer.drawQuad(tank.getPosition().x, tank.getPosition().y,
                        TankData.SIZE * 1.7f, TankData.SIZE * 1.7f, 0f, shader);
            }
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // -------------------------

        for (ClientTank tank : tanks.values()) {
            if (tank.getAlpha() <= 0.02f) continue; // fully cloaked

            if (isObjectVisible(tank.getPosition(), playerPos, renderRangeSq)) {
                boolean isWreck = tank.getHitPoints() <= 0;
                float shimmer = tank.getRespawnShimmerProgress(RESPAWN_SHIMMER_DURATION_MS);

                Vector3f hullTint = tank.getColor();
                // Turret stays a fixed neutral gunmetal regardless of team color so it
                // always contrasts against the colored hull and its aim direction reads
                // clearly at a glance.
                Vector3f turretTint = TURRET_NEUTRAL_TINT;
                if (isWreck) {
                    // Destroyed tanks remain as darkened wrecks until they respawn
                    hullTint = new Vector3f(tank.getColor()).mul(0.3f);
                } else if (effectsAtLeastLow() && tank.isHitFlashing()) {
                    // Hit-confirm flash: white for normal hits, gold for crits (both hull and turret flash together)
                    Vector3f flash = tank.isHitFlashGold() ? new Vector3f(1.0f, 0.84f, 0.2f) : new Vector3f(1.0f, 1.0f, 1.0f);
                    hullTint = flash;
                    turretTint = flash;
                }

                float renderAlpha = tank.getAlpha() * (isWreck ? 1.0f : 0.3f + 0.7f * shimmer);

                // Respawn shimmer scales the tank in over ~0.5 s so spawns read clearly
                float renderSize = TankData.SIZE * (isWreck ? 1.0f : 0.6f + 0.4f * shimmer);

                Texture hull = hullTexturesByType.getOrDefault(tank.getTankType(), tankTexture);
                hull.bind();
                shader.setUniform4f("u_tintColor", hullTint, renderAlpha);
                renderer.drawQuad(tank.getPosition().x, tank.getPosition().y,
                        renderSize, renderSize,
                        tank.getRotation(), shader);

                // Turret layered on top at its own rotation (wrecks keep hull only)
                if (!isWreck) {
                    Texture turret = turretTexturesByType.get(tank.getTankType());
                    if (turret != null) {
                        turret.bind();
                        shader.setUniform4f("u_tintColor", turretTint, renderAlpha);
                        renderer.drawQuad(tank.getPosition().x, tank.getPosition().y,
                                renderSize, renderSize,
                                tank.getTurretRotation(), shader);
                    }
                }
            }
        }

        // --- Render Muzzle Flashes ---
        if (!muzzleFlashes.isEmpty()) {
            for (var flash : muzzleFlashes) {
                if (flash.isFinished()) continue;
                Texture frame = flash.getCurrentFrameTexture();
                if (frame == null) continue;
                frame.bind();
                shader.setUniform4f("u_tintColor", 1.0f, 0.95f, 0.7f, 1.0f);
                renderer.drawQuad(flash.getPosition().x, flash.getPosition().y,
                        flash.getRenderSize(), flash.getRenderSize(), flash.getRotationDegrees(), shader);
            }
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // -------------------------

        shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);

        // Render Bullets
        bulletTexture.bind(); // Bind bullet texture once
        shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);

        for (ClientBullet bullet : bullets) {
            if (isObjectVisible(bullet.getPosition(), playerPos, renderRangeSq) && !bullet.isDestroyed()) {
                Vector2f velocity = bullet.getVelocity();

                float rotationDegrees = 0.0f;

                // Calculate rotation only if the bullet is actually moving
                // (Avoids issues with atan2(0, 0) which is undefined but often returns 0)
                if (velocity.lengthSquared() > 0.0001f) { // Use a small threshold
                    // Calculate angle in radians from velocity vector components
                    // atan2(y, x) gives angle relative to positive X axis
                    double angleRadians = Math.atan2(velocity.y, velocity.x);

                    // Convert radians to degrees
                    float angleDegrees = (float) Math.toDegrees(angleRadians);

                    // Adjust angle:
                    // Texture points UP at 0 degrees rotation.
                    // atan2 gives 90 degrees for UP.
                    // atan2 gives 0 degrees for RIGHT.
                    // We need to subtract 90 degrees from atan2 result.
                    rotationDegrees = angleDegrees - 90.0f;
                }

                Vector2f bulletPosition = bullet.getPosition();
                logger.trace("Rendering Bullet: Pos=({}, {}), Vel=({}, {}), Rotation={}",
                        bulletPosition.x, bulletPosition.y, velocity.x, velocity.y, rotationDegrees);

                // Tracer streak: a bright stretched quad trailing the bullet makes
                // firefights readable at long range
                if (effectsAtLeastLow() && velocity.lengthSquared() > 0.0001f && tracerTexture != null) {
                    float speed = velocity.length();
                    float trailX = bullet.getPosition().x - velocity.x / speed * 12.0f;
                    float trailY = bullet.getPosition().y - velocity.y / speed * 12.0f;
                    tracerTexture.bind();
                    shader.setUniform4f("u_tintColor", 1.0f, 0.9f, 0.5f, 0.55f);
                    renderer.drawQuad(trailX, trailY, 4.0f, 22.0f, rotationDegrees, shader);
                    shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
                    bulletTexture.bind();
                }

                // Draw the quad using the calculated rotation
                renderer.drawQuad(bullet.getPosition().x, bullet.getPosition().y,
                        BulletData.SIZE, BulletData.SIZE,
                        rotationDegrees,
                        shader);
            }
        }

        // --- Render Explosions ---
        if (!activeExplosions.isEmpty()) {
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f); // Reset tint

            for (ExplosionEffect explosion : activeExplosions) {
                if (explosion.isFinished()) continue;

                // Get the specific texture for the current frame
                Texture currentFrameTexture = explosion.getCurrentFrameTexture();
                if (currentFrameTexture == null) {
                    logger.warn("Explosion effect returned null texture for frame, skipping render.");
                    continue; // Skip if texture is missing
                }

                // *** Bind the texture for THIS frame ***
                currentFrameTexture.bind();

                // Get position and size
                Vector2f pos = explosion.getPosition();
                float size = explosion.getRenderSize();

                // Use the original drawQuad method, as we render the whole texture
                renderer.drawQuad(pos.x, pos.y, size, size, 0f, shader);

                // Unbind texture? Not strictly necessary if the next loop iteration or
                // subsequent rendering binds another texture, but can be good practice.
                // currentFrameTexture.unbind(); // Optional
            }
        }
        // -------------------------

        // --- NEW: Render Flames ---
        if (!activeFlames.isEmpty()) {
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f); // Reset tint if needed
            for (FlameEffect flame : activeFlames) {
                if (flame.isFinished()) continue; // Skip finished ones

                Texture currentFrameTexture = flame.getCurrentFrameTexture();
                if (currentFrameTexture == null) {
                    logger.warn("Flame effect returned null texture for frame, skipping render.");
                    continue;
                }

                // Bind the specific flame texture for this frame
                currentFrameTexture.bind();

                Vector2f pos = flame.getPosition();
                float size = flame.getRenderSize();

                // Render the quad using the bound flame texture
                renderer.drawQuad(pos.x, pos.y, size, size, 0f, shader);
            }
        }
        // -------------------------

        // --- Render Hit Sparks ---
        if (!hitSparks.isEmpty()) {
            for (var spark : hitSparks) {
                if (spark.isFinished()) continue;

                Texture frame = spark.getCurrentFrameTexture();
                if (frame == null) continue;

                frame.bind();
                // Crits flash gold, normal hits bright white
                if (spark.isCritical()) {
                    shader.setUniform4f("u_tintColor", 1.0f, 0.84f, 0.2f, 1.0f);
                } else {
                    shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
                }
                Vector2f pos = spark.getPosition();
                renderer.drawQuad(pos.x, pos.y, spark.getRenderSize(), spark.getRenderSize(), 0f, shader);
            }
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // -------------------------

        // --- Render Tile Fires ---
        if (!tileFireEffects.isEmpty()) {
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            for (FlameEffect flame : tileFireEffects.values()) {
                if (flame.isFinished()) continue;

                Texture currentFrameTexture = flame.getCurrentFrameTexture();
                if (currentFrameTexture == null) continue;

                currentFrameTexture.bind();
                Vector2f pos = flame.getPosition();
                float size = flame.getRenderSize();
                renderer.drawQuad(pos.x, pos.y, size, size, 0f, shader);
            }
        }
        // -------------------------

        // --- Render Smoke Effects ---
        if (!activeSmokeEffects.isEmpty()) {
            shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f); // Reset tint

            for (SmokeEffect smoke : activeSmokeEffects.values()) {
                if (!smoke.isActive()) continue; // Skip rendering if stopped

                Texture currentFrameTexture = smoke.getCurrentFrameTexture();
                if (currentFrameTexture != null) {
                    currentFrameTexture.bind(); // Bind the texture for THIS frame

                    Vector2f pos = smoke.getPosition();
                    float size = smoke.getRenderSize();

                    // Use the standard drawQuad for the whole frame texture
                    renderer.drawQuad(pos.x, pos.y, size, size, 0f, shader);
                }
            }
        }
        // --------------------------

        renderUI();
    }

    // Helper for renderGame
    private boolean isObjectVisible(Vector2f objectPos, Vector2f playerPos, float rangeSq) {
        if (isSpectating || playerPos == null) return true; // Spectators see everything
        return objectPos.distanceSquared(playerPos) <= rangeSq;
    }

    private void renderUI() {
        uiManager.startUIRendering(windowWidth, windowHeight);

        // Damage vignette: pulse on taking a hit, slow heartbeat at critically low HP
        if (effectsFull() && vignetteOverlay != null && localTank != null && !isSpectating) {
            long now = System.currentTimeMillis();
            float intensity = 0.0f;

            long sinceHit = now - lastOwnHitTime;
            if (lastOwnHitTime > 0 && sinceHit < OWN_HIT_VIGNETTE_MS) {
                float pulse = 1.0f - sinceHit / (float) OWN_HIT_VIGNETTE_MS;
                intensity = (lastOwnHitCrit ? 1.0f : 0.65f) * pulse;
            }

            int maxHitPoints = localTank.getTankType().getDefaultStats().maxHitPoints();
            if (localTank.getHitPoints() > 0 && localTank.getHitPoints() <= Math.max(1, maxHitPoints / 4)) {
                float heartbeat = 0.25f + 0.2f * (float) Math.sin(now / 350.0);
                intensity = Math.max(intensity, heartbeat);
            }

            vignetteOverlay.draw(uiManager.getProjectionMatrix(), windowWidth, windowHeight, intensity);
        }

        // --- Top-Left UI Elements ---
        final float statusTextX = 10;
        float currentY = 10; // Starting Y position
        float lineSpacing = 5; // Space between UI elements
        float textHeight = uiManager.getTextHeight(UI_TEXT_SCALE_STATUS);
        float secondaryTextHeight = uiManager.getTextHeight(UI_TEXT_SCALE_SECONDARY_STATUS);

        // Render tank health and game state
        if (localTank != null && !isSpectating) {
            // --- Render Health Bar (max HP comes from the chassis type) ---
            if (healthBar != null) {
                float healthBarWidth = 150;
                float healthBarHeight = 22;
                int maxHitPoints = localTank.getTankType().getDefaultStats().maxHitPoints();
                healthBar.draw(uiManager.getProjectionMatrix(), localTank.getHitPoints(), maxHitPoints, statusTextX, currentY, healthBarWidth, healthBarHeight, uiManager);
                currentY += healthBarHeight + lineSpacing;
            }

            uiManager.drawText("TANK: " + localTank.getTankType().name(), statusTextX, currentY, UI_TEXT_SCALE_NORMAL, Colors.WHITE);
            currentY += uiManager.getTextHeight(UI_TEXT_SCALE_NORMAL) + lineSpacing;

            // --- Directional armor diagram beside the health bar ---
            if (armorIndicator != null && localArmor[0] >= 0) {
                var stats = localTank.getTankType().getDefaultStats();
                int[] maxArmor = {stats.frontArmor(), stats.leftArmor(), stats.rightArmor(), stats.rearArmor()};
                armorIndicator.draw(uiManager.getProjectionMatrix(), localArmor, maxArmor, armorHitFlashTimes,
                        statusTextX + 170, 10, 54, uiManager);
            }
            // -------------------------
        } else if (isSpectating) {
            uiManager.drawText("SPECTATING", statusTextX, currentY, UI_TEXT_SCALE_STATUS, Colors.YELLOW);
            currentY += textHeight + lineSpacing;
        } else {
            uiManager.drawText(currentGameState == GameState.CONNECTING ? "CONNECTING..." : "LOADING...",
                             statusTextX, currentY, UI_TEXT_SCALE_STATUS, Colors.WHITE);
            currentY += textHeight + lineSpacing;
        }

        // Render Timer
        if (currentGameState == GameState.PLAYING && roundStartTimeMillis > 0) {
            long elapsedMillis = System.currentTimeMillis() - roundStartTimeMillis;
            long seconds = (elapsedMillis / 1000) % 60;
            long minutes = (elapsedMillis / (1000 * 60)) % 60;
            long hours = (elapsedMillis / (1000 * 60 * 60)) % 24;

            String timeStr = (hours > 0)
                ? String.format("TIME: %02d:%02d:%02d", hours, minutes, seconds)
                : String.format("TIME: %02d:%02d", minutes, seconds);

            uiManager.drawText(timeStr, statusTextX, currentY, UI_TEXT_SCALE_SECONDARY_STATUS, Colors.WHITE);
            currentY += secondaryTextHeight + lineSpacing;
        }

        // Render Player's Kills & Player Count
        if (localTank != null && !isSpectating) {
            uiManager.drawText("KILLS: %d".formatted(playerKills),
                    statusTextX, currentY, UI_TEXT_SCALE_SECONDARY_STATUS, Colors.RED);
            currentY += secondaryTextHeight + lineSpacing;

            uiManager.drawText("PLAYERS: %d".formatted(tanks.size()),
                             statusTextX, currentY, UI_TEXT_SCALE_SECONDARY_STATUS, Colors.WHITE);
        }


        // --- Bottom-Center and Right-Side UI ---
        final float killFeedPaddingX = 10;
        final float killFeedStartY = 10;
        final float killFeedLineHeight = 20;

        // Render weapon cooldown indicator (center bottom of screen)
        if (localTank != null && !isSpectating) {
            long cooldownRemaining = localTank.getCooldownRemaining();
            if (cooldownRemaining > 0) {
                String cooldownText = String.format("RELOADING: %.1fs", cooldownRemaining / 1000.0f);
                float textWidth = uiManager.getTextWidth(cooldownText, UI_TEXT_SCALE_STATUS);
                float x = (windowWidth - textWidth) / 2.0f;
                float y = windowHeight - 40;
                uiManager.drawText(cooldownText, x, y, UI_TEXT_SCALE_STATUS, Colors.WHITE);
            }
        }

        // Render Kill Feed Messages (top-right); fresh messages pop with a brief scale bounce
        long killFeedNow = System.currentTimeMillis();
        float currentKillFeedY = killFeedStartY;
        for (KillFeedMessage feedMessage : killFeedMessages) {
            long age = killFeedNow - (feedMessage.expiryTimeMillis() - KILL_FEED_DISPLAY_TIME_MS);
            float bounce = (effectsAtLeastLow() && age >= 0 && age < 300)
                    ? 1.0f + 0.35f * (1.0f - age / 300.0f) : 1.0f;
            float scale = UI_TEXT_SCALE_KILL_FEED * bounce;

            float textWidth = uiManager.getTextWidth(feedMessage.message(), scale);
            float x = windowWidth - textWidth - killFeedPaddingX;
            Vector3f textColor = switch (feedMessage.getStatusMessageKind()) {
                case PlayerKilled -> Colors.RED;
                case PlayerLeft -> Colors.ORANGE;
                case PlayerJoined -> Colors.WHITE;
                default -> Colors.CYAN;
            };
            uiManager.drawText(feedMessage.message(), x, currentKillFeedY, scale, textColor);
            currentKillFeedY += killFeedLineHeight;
        }

        // --- Render Centered Messages ---
        final float centerMessageY = windowHeight * 0.4f;
        boolean showSelectionScreen = selectionScreen != null && currentGameState == GameState.WAITING;

        // Render Announcements (the selection screen shows them in its status line instead;
        // COUNTDOWN's server announcement, e.g. "ROUND STARTING IN 5 SECONDS.", is redundant
        // with the big "GET READY" countdown overlay below and would overlap it)
        if (!announcements.isEmpty() && !showSelectionScreen && currentGameState != GameState.COUNTDOWN) {
            String announcement = announcements.getFirst();
            float textWidth = uiManager.getTextWidth(announcement, UI_TEXT_SCALE_ANNOUNCEMENT);
            float x = (windowWidth - textWidth) / 2.0f;
            uiManager.drawText(announcement, x, centerMessageY, UI_TEXT_SCALE_ANNOUNCEMENT, Colors.RED);
        }

        // Render Game State messages (COUNTDOWN's messaging is handled by the big
        // "GET READY" countdown overlay below, once the selection screen has closed)
        String stateMessage = "";
        if (currentGameState == GameState.WAITING) {
            stateMessage = "WAITING FOR PLAYERS (" + tanks.size() + ")";
        }

        if (!stateMessage.isEmpty() && !showSelectionScreen) {
            float textWidth = uiManager.getTextWidth(stateMessage, UI_TEXT_SCALE_ANNOUNCEMENT);
            float x = (windowWidth - textWidth) / 2.0f;
            uiManager.drawText(stateMessage, x, centerMessageY, UI_TEXT_SCALE_ANNOUNCEMENT, Colors.RED);
        }

        // Active buff stack with countdowns (bottom-left)
        var localBuffs = activeBuffsByPlayerId.get(localPlayerId);
        if (localBuffs != null && !localBuffs.isEmpty()) {
            long now = System.currentTimeMillis();
            float buffY = windowHeight - 40.0f;

            for (ActiveBuff buff : localBuffs.values()) {
                long remainingMs = Math.max(0, buff.endTimeMillis() - now);
                String line = buff.type().name() + " " + ((remainingMs + 999) / 1000) + "S";
                uiManager.drawText(line, 10, buffY, UI_TEXT_SCALE_NORMAL,
                        powerUpCategoryColor(buff.type().getCategory()));
                buffY -= uiManager.getTextHeight(UI_TEXT_SCALE_NORMAL) + 6.0f;
            }
        }

        // Floating damage text above the player's tank (world -> screen projection)
        if (!floatingTexts.isEmpty() && camera != null) {
            float viewWidth = camera.getViewRight() - camera.getViewLeft();
            float viewHeight = camera.getViewTop() - camera.getViewBottom();

            for (var floatingText : floatingTexts) {
                Vector2f world = floatingText.getCurrentWorldPosition();
                float screenX = (world.x - camera.getViewLeft()) / viewWidth * windowWidth;
                float screenY = (camera.getViewTop() - world.y) / viewHeight * windowHeight;

                float alpha = floatingText.getAlpha();
                Vector3f color = new Vector3f(floatingText.getColor()).mul(alpha);
                float textWidth = uiManager.getTextWidth(floatingText.getText(), UI_TEXT_SCALE_NORMAL);
                uiManager.drawText(floatingText.getText(), screenX - textWidth / 2.0f, screenY, UI_TEXT_SCALE_NORMAL, color);
            }
        }

        // Full lobby tank selection screen: backdrop, portrait, stats, roster
        if (showSelectionScreen) {
            selectionScreen.drawBackdrop(uiManager.getProjectionMatrix(), windowWidth, windowHeight);
            drawSelectionPortrait();

            // Roster: every connected player with their ready state
            var roster = new ArrayList<org.chrisgruber.nettank.client.engine.ui.TankSelectionScreen.RosterEntry>();
            for (ClientTank tank : tanks.values()) {
                roster.add(new org.chrisgruber.nettank.client.engine.ui.TankSelectionScreen.RosterEntry(
                        tank.getName(),
                        Boolean.TRUE.equals(lobbyReadyByPlayerId.get(tank.getPlayerId())),
                        tank.getPlayerId() == localPlayerId));
            }
            roster.sort(java.util.Comparator.comparing(e -> !e.isLocal()));

            selectionScreen.drawInfo(uiManager.getProjectionMatrix(), uiManager, windowWidth, windowHeight,
                    selectedTankType, selectionConfirmed, roster);
        }

        // Big "GET READY" countdown centered on screen, popping in each second
        if (currentGameState == GameState.COUNTDOWN && countdownEndTimeMillis > 0) {
            long now = System.currentTimeMillis();
            long secondsLeft = Math.max(1, (countdownEndTimeMillis - now + 999) / 1000);
            if (secondsLeft != lastCountdownSecond) {
                lastCountdownSecond = secondsLeft;
                countdownTickTimeMillis = now;
                audioManager.playSound("countdown_tick");
            }

            float sinceTick = now - countdownTickTimeMillis;
            float fadeIn = Math.min(1.0f, sinceTick / 120.0f);                      // quick fade-in
            float pop = 1.0f + 0.5f * Math.max(0.0f, 1.0f - sinceTick / 250.0f);    // scale pop per second

            Vector3f countdownColor = new Vector3f(1.0f, 0.8f, 0.1f).mul(0.35f + 0.65f * fadeIn);

            String header = "GET READY";
            float headerScale = 1.3f;
            float headerWidth = uiManager.getTextWidth(header, headerScale);
            uiManager.drawText(header, (windowWidth - headerWidth) / 2.0f, windowHeight * 0.40f, headerScale, countdownColor);

            String number = String.valueOf(secondsLeft);
            float numberScale = 3.2f * pop;
            float numberWidth = uiManager.getTextWidth(number, numberScale);
            uiManager.drawText(number, (windowWidth - numberWidth) / 2.0f, windowHeight * 0.46f, numberScale, countdownColor);
        } else {
            lastCountdownSecond = -1;
        }

        uiManager.endUIRendering();
    }

    // Draws the selected tank's hull + turret portrait inside the selection panel
    // using the world quad shader with a temporary screen-space projection.
    private void drawSelectionPortrait() {
        if (tankTexture == null || shader == null || renderer == null || selectionScreen == null) return;

        Matrix4f screenProjection = new Matrix4f().setOrtho(0, windowWidth, windowHeight, 0, -1, 1);
        shader.bind();
        shader.setUniformMat4f("u_projection", screenProjection);
        shader.setUniformMat4f("u_view", new Matrix4f());

        float size = selectionScreen.portraitSize();
        float centerX = selectionScreen.portraitCenterX(windowWidth);
        float centerY = selectionScreen.portraitCenterY(windowHeight);
        shader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);

        // 180-degree rotation compensates for the y-down screen projection
        Texture hull = hullTexturesByType.getOrDefault(selectedTankType, tankTexture);
        hull.bind();
        renderer.drawQuad(centerX, centerY, size, size, 180.0f, shader);

        Texture turret = turretTexturesByType.get(selectedTankType);
        if (turret != null) {
            turret.bind();
            renderer.drawQuad(centerX, centerY, size, size, 180.0f, shader);
        }

        // Restore the world projection for the next frame's world pass
        if (camera != null) {
            shader.setUniformMat4f("u_projection", camera.getProjectionMatrix());
        }
    }


    @Override
    public void handleGameInput(float deltaTime) {
        if (inputHandler == null) return;

        if (inputHandler.isExitPressed()) {
            logger.info("Exit key pressed. Closing game window.");
            if(windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
            return;
        }

        // Lobby tank type selection: cycle with A/D, D-pad, or bumpers before the round starts.
        // Locked once COUNTDOWN begins - the selection screen has already closed by then.
        if (currentGameState == GameState.WAITING) {
            int direction = 0;
            if (inputHandler.isKeyPressed(GLFW_KEY_A) || inputHandler.isDpadLeftPressed()
                    || inputHandler.isLeftBumperPressed()) direction = -1;
            else if (inputHandler.isKeyPressed(GLFW_KEY_D) || inputHandler.isDpadRightPressed()
                    || inputHandler.isRightBumperPressed()) direction = 1;

            if (direction != 0) {
                inputHandler.resetKey(GLFW_KEY_A);
                inputHandler.resetKey(GLFW_KEY_D);

                var types = org.chrisgruber.nettank.common.entities.TankType.values();
                int index = (selectedTankType.ordinal() + direction + types.length) % types.length;
                selectedTankType = types[index];

                if (gameClient != null && gameClient.isConnected()) {
                    gameClient.sendTankTypeSelection(selectedTankType.name());
                    // Changing type revokes readiness (the server enforces this too)
                    if (selectionConfirmed) {
                        gameClient.sendReady(false);
                    }
                }
                selectionConfirmed = false;
                audioManager.playSound("ui_select");
                logger.info("Selected tank type: {}", selectedTankType);
            }

            // Space or gamepad A toggles ready; the round starts once everyone is ready
            if (inputHandler.isShootPressed()) {
                inputHandler.resetKey(GLFW_KEY_SPACE);
                selectionConfirmed = !selectionConfirmed;
                if (gameClient != null && gameClient.isConnected()) {
                    gameClient.sendReady(selectionConfirmed);
                }
                audioManager.playSound(selectionConfirmed ? "ui_confirm" : "ui_select");
            }
        }

        // Handle Game Controls only if playing
        if (localTank != null && !isSpectating && currentGameState == GameState.PLAYING) {
            // Use new unified input methods (supports both keyboard and gamepad)
            boolean keyW = inputHandler.isForwardPressed();
            boolean keyS = inputHandler.isBackwardPressed();
            boolean keyA = inputHandler.isRotateLeftPressed();
            boolean keyD = inputHandler.isRotateRightPressed();
            boolean keySpace = inputHandler.isShootPressed();
            float turretTurn = inputHandler.getTurretRotationInput();

            // Quantize to the 2 decimals sent on the wire so analog jitter doesn't spam sends
            float quantizedTurretTurn = Math.round(turretTurn * 100.0f) / 100.0f;

            // Check if movement input state has changed
            if (keyW != prevKeyW || keyS != prevKeyS || keyA != prevKeyA || keyD != prevKeyD
                    || quantizedTurretTurn != prevTurretTurn) {
                // Send only when input state changes
                if (gameClient != null && gameClient.isConnected()) {
                    logger.debug("Sending movement input: W:{} S:{} A:{} D:{} turret:{}", keyW, keyS, keyA, keyD, quantizedTurretTurn);
                    gameClient.sendInput(keyW, keyS, keyA, keyD, quantizedTurretTurn);
                }

                // Update previous state
                prevKeyW = keyW;
                prevKeyS = keyS;
                prevKeyA = keyA;
                prevKeyD = keyD;
                prevTurretTurn = quantizedTurretTurn;
            }

            // Handle shooting command
            if (keySpace) {
                if (gameClient != null && gameClient.isConnected()) {
                    logger.debug("Sending SHOOT command");
                    gameClient.sendShoot();
                }

                inputHandler.resetKey(GLFW_KEY_SPACE); // Consume press event
            }

            // Local tank's engine hum: active while moving, pitch/gain nudged by direction
            engineSoundActive = keyW || keyS;
            audioManager.setEngineLoopActive(engineSoundActive, keyS && !keyW ? 0.6f : 0.8f, keyS && !keyW ? 0.9f : 1.0f);
        } else if (engineSoundActive) {
            engineSoundActive = false;
            audioManager.setEngineLoopActive(false, 0f, 1f);
        }
    }

    @Override
    public void cleanupGame() {
        logger.info("Cleaning up TankBattleGame resources...");

        // Stop the game client if it's running

        if (gameClient != null) {
            logger.debug("Stopping network client...");

            try {
                gameClient.stop();
            } catch (Exception e) {
                logger.error("Error stopping game client", e);
            } finally {
                gameClient = null;
                logger.debug("Network client stopped and dereferenced.");
            }
        }

        // Cleanup OpenGL resources

        // --- Cleanup Explosion Frame Textures ---
        logger.debug("Deleting explosion frame textures...");
        for (Texture frameTexture : explosionFrameTextures) {
            try {
                if (frameTexture != null) frameTexture.delete();
            } catch (Exception e) {
                logger.error("Error deleting explosion frame texture", e);
            }
        }
        explosionFrameTextures.clear(); // Clear the list
        // ---------------------------------------

        // --- Cleanup Flame Frame Textures ---
        logger.debug("Deleting flame frame textures...");
        for (Texture frameTexture : flameFrameTextures) {
            try {
                if (frameTexture != null) frameTexture.delete();
            } catch (Exception e) {
                logger.error("Error deleting flame frame texture", e);
            }
        }
        flameFrameTextures.clear(); // Clear the list
        // ---------------------------------------

        // --- Cleanup Smoke Frame Textures ---
        logger.debug("Deleting smoke frame textures...");
        for (Texture frameTexture : smokeFrameTextures) {
            try {
                if (frameTexture != null) frameTexture.delete();
            } catch (Exception e) {
                logger.error("Error deleting smoke frame texture", e);
            }
        }
        smokeFrameTextures.clear();
        // ---------------------------------------

        // Clear explosion list
        activeExplosions.clear();
        activeFlames.clear();
        activeSmokeEffects.clear();

        logger.debug("Deleting game textures, shaders, renderer, UI...");

        try { if (tankTexture != null) tankTexture.delete(); } catch (Exception e) { logger.error("Error deleting tankTexture", e); }
        try { if (bulletTexture != null) bulletTexture.delete(); } catch (Exception e) { logger.error("Error deleting bulletTexture", e); }

        for (Texture t : turretTexturesByType.values()) {
            try { t.delete(); } catch (Exception e) { logger.error("Error deleting a turret texture", e); }
        }
        for (var entry : hullTexturesByType.entrySet()) {
            if (entry.getValue() == tankTexture) continue; // already deleted above
            try { entry.getValue().delete(); } catch (Exception e) { logger.error("Error deleting a hull texture", e); }
        }
        for (Texture t : powerUpIconTextures.values()) {
            try { t.delete(); } catch (Exception e) { logger.error("Error deleting a power-up icon texture", e); }
        }
        for (Texture t : muzzleFlashFrameTextures) {
            try { t.delete(); } catch (Exception e) { logger.error("Error deleting a muzzle flash texture", e); }
        }
        for (Texture t : sparkFrameTextures) {
            try { t.delete(); } catch (Exception e) { logger.error("Error deleting a spark texture", e); }
        }
        try { if (tracerTexture != null) tracerTexture.delete(); } catch (Exception e) { logger.error("Error deleting tracerTexture", e); }
        try { if (trackMarkTexture != null) trackMarkTexture.delete(); } catch (Exception e) { logger.error("Error deleting trackMarkTexture", e); }
        try { if (scorchDecalTexture != null) scorchDecalTexture.delete(); } catch (Exception e) { logger.error("Error deleting scorchDecalTexture", e); }
        try { if (auraRingTexture != null) auraRingTexture.delete(); } catch (Exception e) { logger.error("Error deleting auraRingTexture", e); }
        try { if (exhaustTexture != null) exhaustTexture.delete(); } catch (Exception e) { logger.error("Error deleting exhaustTexture", e); }

        try { audioManager.cleanup(); } catch (Exception e) { logger.error("Error cleaning up audioManager", e); }

        if (mapInitialized) {
            try { if (summerGrassTexture != null) summerGrassTexture.delete(); } catch (Exception e) { logger.error("Error deleting summerGrassTexture", e); }
            try { if (mudFieldTexture != null) mudFieldTexture.delete(); } catch (Exception e) { logger.error("Error deleting mudFieldTexture", e); }
            try { if (dirtFieldTexture != null) dirtFieldTexture.delete(); } catch (Exception e) { logger.error("Error deleting dirtFieldTexture", e); }
            try { if (forestFloorTexture != null) forestFloorTexture.delete(); } catch (Exception e) { logger.error("Error deleting forestFloorTexture", e); }
            try { if (desertSandTexture != null) desertSandTexture.delete(); } catch (Exception e) { logger.error("Error deleting desertSandTexture", e); }
            try { if (hillTexture != null) hillTexture.delete(); } catch (Exception e) { logger.error("Error deleting hillTexture", e); }
            try { if (rocksTexture != null) rocksTexture.delete(); } catch (Exception e) { logger.error("Error deleting rocksTexture", e); }
            try { if (scorchedTerrainTexture != null) scorchedTerrainTexture.delete(); } catch (Exception e) { logger.error("Error deleting scorchedTerrainTexture", e); }
        }

        // UIManager cleanup deletes its font texture, shader, renderer
        try {
            if (uiManager != null) {
                uiManager.cleanup();
            }
            if (healthBar != null) {
                healthBar.cleanup();
            }
            if (armorIndicator != null) {
                armorIndicator.cleanup();
            }
            if (vignetteOverlay != null) {
                vignetteOverlay.cleanup();
            }
            if (selectionScreen != null) {
                selectionScreen.cleanup();
            }
        } catch (Exception e) {
            logger.error("Error cleaning up uiManager", e);
        }

        logger.debug("Game-specific OpenGL resources deleted.");

        // Clear game state collections
        tanks.clear();
        bullets.clear();
        announcements.clear();
        killFeedMessages.clear();

        logger.info("TankBattleGame cleanup finished.");
    }

    // Called when ASSIGN_ID is received
    public void setLocalPlayerId(int id) {
        this.localPlayerId = id;
        logger.info("Assigned local player ID: {}", id);
    }

    @Override
    public void addOrUpdateTank(int id, float x, float y, float rotation, String name, float r, float g, float b, float turretRotation, String tankType) {
        ClientTank tank = tanks.get(id);

        TankData data = new TankData();
        data.updateFromServer(id, name, x, y, rotation, r, g, b, turretRotation);
        data.setTankType(org.chrisgruber.nettank.common.entities.TankType.fromString(tankType));

        if (tank == null) {
            logger.info("Creating new ClientTank for player ID: {} Name: {}", id, name);

            tank = new ClientTank(data); // Create client wrapper

            tanks.put(id, tank);    // Put the NEW tank into the map *after* creating it

            if (id == localPlayerId) {
                localTank = tank;
                isSpectating = tank.isDestroyed();
                logger.info("Local tank object created. Spectating: {}", isSpectating);
            }

            var statusMessage = String.format("%s has joined", tank.getName());
            addStatusMessage(StatusMessageKind.PlayerJoined, statusMessage);
        } else {
            logger.trace("Updating existing ClientTank for player ID: {}", id);

            tank.updateFromServerEntity(data);

            if (id == localPlayerId) {
                localTank = tank;
            }
        }
    }

    // Called when PLAYER_LEFT is received
    public void removeTank(int id) {
        SmokeEffect smoke = activeSmokeEffects.remove(id);

        if (smoke != null) {
            smoke.stop(); // Mark as inactive (though it's removed anyway)
            logger.info("Stopped and removed smoke effect for leaving player {}.", id);
        }

        ClientTank removed = tanks.remove(id);

        if (removed != null) {
            logger.info("Removed tank for player ID: {} Name: {}", id, removed.getName());

            var statusMessage = String.format("%s has left", removed.getName());
            addStatusMessage(StatusMessageKind.PlayerLeft, statusMessage);
        } else {
            logger.warn("Received removeTank for unknown ID: {}", id);
        }
    }

    // Called when PLAYER_UPDATE is received
    public void updateTankState(int id, float x, float y, float rotation, float turretRotation, boolean isRespawn) {
        ClientTank tank = tanks.get(id);

        if (tank == null) {
            logger.warn("Received updateTankState for unknown Player ID: {}", id);
            return;
        }

        if (tank.getPosition().x() == x && tank.getPosition().y() == y
                && tank.getRotation() == rotation && tank.getTurretRotation() == turretRotation) {
            // no change in state
            logger.trace("No state change for tank ID: {} x: {}, y: {}, rotation: {}", id, x, y, rotation);
            return;
        }

        if (isRespawn) {
            // Stop and remove the smoke effect for this player
            SmokeEffect smoke = activeSmokeEffects.get(tank.getPlayerId());
            if (smoke != null) {
                smoke.stop(); // Mark as inactive
                // Removal happens during the update loop's cleanup phase
            } else {
                logger.warn("No active smoke effect found for respawning player {}.", tank.getPlayerId());
            }

            tank.setHitPoints(tank.getTankType().getDefaultStats().maxHitPoints());
            tank.startRespawnShimmer();
        }

        logger.trace("Updating tank state for player ID: {}. Existing state is x: {}, y: {}, rotation: {}", id, tank.getPosition().x(), tank.getPosition().y(), tank.getRotation());

        tank.HandlerPlayerUpdateMessage(new Vector2f(x, y), rotation, turretRotation);

        logger.trace("Updated tank state for player ID: {} x: {}, y: {}, rotation: {}", id, x, y, rotation);
    }

    // Called when SHOOT is received
    public void spawnBullet(UUID bulletId, int ownerId, float x, float y, float dirX, float dirY) {
        // Calculate velocity based on direction and common speed
        Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(BulletData.SPEED);
        Vector2f position = new Vector2f(x, y);
        long spawnTime = System.currentTimeMillis(); // Client uses its own time for prediction expiry

        // TODO: Where is bullet rotation set and should it be here?
        float rotation = 0.0f;

        // Create common BulletData (damage is server-authoritative; client bullets are visual only)
        BulletData bulletData = new BulletData(bulletId, ownerId, position, velocity, rotation, spawnTime, false, 0);
        // Create ClientBullet wrapper for rendering/prediction
        ClientBullet clientBullet = new ClientBullet(bulletData);

        bullets.add(clientBullet);

        // Muzzle flash at the firing point, rotated to the shot direction
        if (effectsAtLeastLow() && !muzzleFlashFrameTextures.isEmpty()) {
            float flashRotation = (float) Math.toDegrees(Math.atan2(dirY, dirX)) - 90.0f;
            muzzleFlashes.add(new org.chrisgruber.nettank.client.game.effects.MuzzleFlashEffect(
                    position, flashRotation, MUZZLE_FLASH_DURATION_MS, muzzleFlashFrameTextures, 22.0f));
        }

        audioManager.playSoundAt("shoot", x, y);

        logger.trace("Spawned bullet owned by {}", ownerId);
    }

    @Override
    public void handlePlayerHit(int targetId, int shooterId, UUID bulletId, int damage, String side, boolean critical) {
        logger.debug("Player hit: Target={}, Shooter={}, BulletID={}, Damage={}, Side={}, Crit={}",
                targetId, shooterId, bulletId, damage, side, critical);

        // Remove the impacting bullet
        bullets.removeIf(bullet -> bullet.getId().equals(bulletId));

        // In-world impact flash at the struck hull side (rendered for every tank)
        ClientTank hitTank = tanks.get(targetId);
        if (hitTank != null) {
            spawnHitSpark(hitTank, side, critical);

            // Hit-confirm: when YOUR shot connects, the struck enemy flashes (gold on crit)
            if (shooterId == localPlayerId && effectsAtLeastLow()) {
                hitTank.triggerHitFlash(critical, critical ? 160 : 80);
            }
        }

        // Own tank: flash the matching HUD armor segment; HP/armor values arrive via ARM
        if (targetId == localPlayerId) {
            int sideIndex = org.chrisgruber.nettank.common.entities.ArmorSide.fromString(side).ordinal();
            armorHitFlashTimes[sideIndex] = System.currentTimeMillis();
            lastOwnHitTime = System.currentTimeMillis();
            lastOwnHitCrit = critical;

            if (critical && localTank != null) {
                floatingTexts.add(new org.chrisgruber.nettank.client.game.effects.FloatingTextEffect(
                        "CRIT!", aboveTank(localTank, 14.0f), new Vector3f(1.0f, 0.84f, 0.2f), FLOATING_TEXT_DURATION_MS));
            }
        }
    }

    @Override
    public void updateArmorStatus(int front, int left, int right, int rear, int hitPoints) {
        int[] newArmor = {front, left, right, rear};

        if (localTank != null) {
            // Spawn floating damage text from the diffs (skip the very first snapshot)
            if (localArmor[0] >= 0 && currentGameState == GameState.PLAYING) {
                String[] sideNames = {"FRONT", "LEFT", "RIGHT", "REAR"};
                for (int i = 0; i < newArmor.length; i++) {
                    int loss = localArmor[i] - newArmor[i];
                    if (loss > 0) {
                        floatingTexts.add(new org.chrisgruber.nettank.client.game.effects.FloatingTextEffect(
                                "-" + loss + " ARMOR (" + sideNames[i] + ")", aboveTank(localTank, 0.0f),
                                new Vector3f(1.0f, 1.0f, 0.3f), FLOATING_TEXT_DURATION_MS));
                    }
                }

                int hpLoss = localTank.getHitPoints() - hitPoints;
                if (hpLoss > 0) {
                    floatingTexts.add(new org.chrisgruber.nettank.client.game.effects.FloatingTextEffect(
                            "-" + hpLoss + " HP", aboveTank(localTank, 28.0f),
                            new Vector3f(1.0f, 0.35f, 0.35f), FLOATING_TEXT_DURATION_MS));
                }
            }

            // ARM is the authoritative HP source for the owning player
            localTank.setHitPoints(hitPoints);
        }

        System.arraycopy(newArmor, 0, localArmor, 0, newArmor.length);
        logger.debug("Armor status: F{} L{} R{} B{}, HP {}", front, left, right, rear, hitPoints);
    }

    // Track marks and terrain-themed dust behind moving tanks, plus a smoke trickle
    // from the player's own hull below 50% HP. All gated behind FULL effects quality.
    private void spawnGroundEffects() {
        if (!effectsFull() || gameMap == null) return;

        long now = System.currentTimeMillis();

        for (ClientTank tank : tanks.values()) {
            if (tank.getHitPoints() <= 0 || tank.getAlpha() <= 0.02f) continue;

            Vector2f lastPosition = lastGroundEffectPosition.get(tank.getPlayerId());
            if (lastPosition == null) {
                lastGroundEffectPosition.put(tank.getPlayerId(), new Vector2f(tank.getPosition()));
                continue;
            }
            if (tank.getPosition().distance(lastPosition) < GROUND_EFFECT_SPACING) continue;

            lastPosition.set(tank.getPosition());

            trackMarks.addLast(new org.chrisgruber.nettank.client.game.effects.TrackMarkEffect(
                    tank.getPosition(), tank.getRotation(), TankData.SIZE * 0.6f, 5.0f, 0.35f));
            while (trackMarks.size() > TRACK_MARK_CAP) {
                trackMarks.pollFirst();
            }

            // Terrain-themed dust kick-up behind the tracks
            var terrainType = gameMap.getEffectiveTypeAt(tank.getPosition().x, tank.getPosition().y);
            Vector3f dustTint = switch (terrainType) {
                case DIRT, SAND -> new Vector3f(0.82f, 0.72f, 0.5f);  // tan dust
                case MUD -> new Vector3f(0.32f, 0.24f, 0.16f);        // dark mud splatter
                case SHALLOW_WATER -> new Vector3f(0.65f, 0.8f, 0.95f); // water spray
                default -> null;
            };
            if (dustTint != null && !smokeFrameTextures.isEmpty()) {
                dustPuffs.add(new org.chrisgruber.nettank.client.game.effects.DustPuffEffect(
                        tank.getPosition(), dustTint, 600, 14.0f));
            }

            // Engine exhaust flicker at the rear, opposite the hull's forward direction
            if (exhaustTexture != null) {
                float rearAngleRad = (float) Math.toRadians(tank.getRotation() + 180.0f);
                float half = TankData.SIZE / 2.0f;
                Vector2f rearPosition = new Vector2f(
                        tank.getPosition().x - (float) Math.sin(rearAngleRad) * half,
                        tank.getPosition().y + (float) Math.cos(rearAngleRad) * half);
                exhaustPuffs.add(new org.chrisgruber.nettank.client.game.effects.DustPuffEffect(
                        rearPosition, new Vector3f(1.0f, 1.0f, 1.0f), 400, 10.0f));
            }
        }

        // Damaged hull smoke trickle (own tank only: enemy HP is not public)
        if (localTank != null && localTank.getHitPoints() > 0 && !smokeFrameTextures.isEmpty()) {
            int maxHitPoints = localTank.getTankType().getDefaultStats().maxHitPoints();
            if (localTank.getHitPoints() <= maxHitPoints / 2 && now - lastHullSmokeTime >= 400) {
                lastHullSmokeTime = now;
                dustPuffs.add(new org.chrisgruber.nettank.client.game.effects.DustPuffEffect(
                        localTank.getPosition(), new Vector3f(0.35f, 0.35f, 0.35f), 900, 12.0f));
            }
        }
    }

    private Vector2f aboveTank(ClientTank tank, float extraHeight) {
        return new Vector2f(tank.getPosition().x, tank.getPosition().y + TankData.SIZE + extraHeight);
    }

    // Positions a brief spark burst at the hull edge of the struck side
    private void spawnHitSpark(ClientTank tank, String sideName, boolean critical) {
        if (sparkFrameTextures.isEmpty()) return;

        float offsetDegrees = switch (org.chrisgruber.nettank.common.entities.ArmorSide.fromString(sideName)) {
            case FRONT -> 0.0f;
            case LEFT -> 90.0f;
            case REAR -> 180.0f;
            case RIGHT -> 270.0f;
        };
        float angleRad = (float) Math.toRadians(tank.getRotation() + offsetDegrees);
        float half = TankData.SIZE / 2.0f;
        Vector2f position = new Vector2f(
                tank.getPosition().x - (float) Math.sin(angleRad) * half,
                tank.getPosition().y + (float) Math.cos(angleRad) * half);

        float size = critical ? HIT_SPARK_RENDER_SIZE * 1.5f : HIT_SPARK_RENDER_SIZE;
        hitSparks.add(new org.chrisgruber.nettank.client.game.effects.HitSparkEffect(
                position, HIT_SPARK_DURATION_MS, sparkFrameTextures, size, critical));

        audioManager.playSoundAt(critical ? "hit_crit" : "hit", position.x, position.y);
    }

    @Override
    public void handlePlayerDestroyed(int targetId, int shooterId) {
        // Existing logic to handle visual effects or messages for any destruction might go here.
        logger.debug("Received PlayerDestroyed: Target={}, Shooter={}", targetId, shooterId);

        // Check if the shooter is the *local* player
        if (shooterId == this.localPlayerId && localPlayerId != -1) {
            // Increment the local player's kill count
            this.playerKills++;
            logger.info("Local player ({}) got a kill! Total kills: {}", localPlayerId, playerKills);
            // TODO: maybe add a temporary announcement here like "YOU DESTROYED A TANK!"
        }

        ClientTank shooterTank = tanks.get(shooterId);
        ClientTank targetTank = tanks.get(targetId);

        if (targetTank != null) {
            targetTank.setHitPoints(0);
            audioManager.playSoundAt("destroyed", targetTank.getPosition().x, targetTank.getPosition().y);

            // Camera shake for nearby explosions and a persistent scorch mark
            if (effectsAtLeastLow() && camera != null && localTank != null
                    && localTank.getPosition().distance(targetTank.getPosition()) <= EXPLOSION_SHAKE_RANGE) {
                camera.addShake(6.0f);
            }
            if (effectsFull()) {
                scorchDecals.addLast(new org.chrisgruber.nettank.client.game.effects.ScorchDecal(
                        targetTank.getPosition(), (float) (Math.random() * 360.0), 46.0f));
                while (scorchDecals.size() > SCORCH_DECAL_CAP) {
                    scorchDecals.pollFirst();
                }
            }
        }

        // --- Spawn Explosion ---
        // Check if target exists AND if explosion textures were loaded successfully
        if (targetTank != null && !explosionFrameTextures.isEmpty()) {
            logger.info("Spawning explosion at target {}'s location: ({}, {})", targetId, targetTank.getPosition().x(), targetTank.getPosition().y());
            try {
                ExplosionEffect explosion = new ExplosionEffect(
                        targetTank.getPosition(),
                        EXPLOSION_DURATION_MS,
                        explosionFrameTextures, // Pass the whole list
                        EXPLOSION_RENDER_SIZE
                );
                activeExplosions.add(explosion);
            } catch (Exception e) {
                // Catch potential errors from ExplosionEffect constructor (e.g., empty list if loading failed)
                logger.error("Failed to create ExplosionEffect instance", e);
            }
        } else {
            if (targetTank == null) logger.warn("Cannot spawn explosion, target tank {} not found.", targetId);
            if (explosionFrameTextures.isEmpty()) logger.warn("Cannot spawn explosion, frame textures not loaded or list is empty.");
        }
        // ---------------------

        // --- Spawn Smoke Effect ---
        // Check if target exists AND if smoke textures were loaded
        if (targetTank != null && !smokeFrameTextures.isEmpty()) {
            // Remove existing smoke for this player first, just in case (shouldn't happen often)
            SmokeEffect existingSmoke = activeSmokeEffects.remove(targetId);
            if (existingSmoke != null) {
                logger.warn("Removed existing smoke effect for player {} before creating new one.", targetId);
                // Note: Existing effect object is now orphaned and will be garbage collected.
            }

            logger.info("Spawning smoke effect for player {} at ({}, {})", targetId, targetTank.getPosition().x(), targetTank.getPosition().y());
            try {
                SmokeEffect smoke = new SmokeEffect(
                        targetTank.getPosition(),
                        targetId, // Store the player ID with the effect
                        smokeFrameTextures,
                        SMOKE_FRAME_DURATION_MS,
                        SMOKE_RENDER_SIZE
                );
                activeSmokeEffects.put(targetId, smoke); // Put in map using player ID as key
            } catch (Exception e) {
                logger.error("Failed to create SmokeEffect instance for player {}", targetId, e);
            }
        } else {
            if (targetTank == null) logger.warn("Cannot spawn smoke, target tank {} not found.", targetId);
            if (smokeFrameTextures.isEmpty()) logger.warn("Cannot spawn smoke, frame textures not loaded.");
        }
        // ------------------------

        // shooterId -1 means an environmental kill (burning terrain)
        String shooterName = (shooterId == -1) ? "THE FIRE"
                : (shooterTank != null) ? shooterTank.getName() : ("Player " + shooterId);
        String targetName = (targetTank != null) ? targetTank.getName() : ("Player " + targetId);

        // Construct the message
        String killMessage = shooterName + " KILLED " + targetName;
        addStatusMessage(StatusMessageKind.PlayerKilled, killMessage);
    }

    // Called when PLAYER_LIVES is received
    public void updatePlayerLives(int playerId, int lives) {
        // TODO: Handle player lives update logic
        // This is a placeholder for the actual logic to update player lives on the UI.
        // The server has the actual number of lives or respawns allowed. The client will just display it.
        ClientTank tank = tanks.get(playerId);

        if (tank != null) {
            logger.info("Player {} lives set to: {}", tank.getName(), lives);

            if (playerId == localPlayerId) {
                // TODO: Move this logic to spectate to its own NetworkProtocol command
                /*
                if (lives <= 0 && !isSpectating) {
                    logger.info("Local player defeated. Enabling spectator mode.");
                    isSpectating = true;
                    // localTank reference remains but rendering/input handles isSpectating
                } else if (lives > 0 && isSpectating) {
                    // This case handled in addOrUpdateTank generally
                    logger.info("Local player has lives again, spectator mode should disable.");
                    isSpectating = false;
                }
                */
            }
        } else {
            logger.warn("Received updatePlayerLives for unknown ID: {}", playerId);
        }
    }

    // Called when GAME_STATE is received
    public void setGameState(GameState state, long timeData) {
        logger.info("Game state changed to: {} with time data: {}", state, timeData);

        this.currentGameState = state;

        // Clear announcements on most state changes, except maybe within countdown?
        if (state != GameState.COUNTDOWN) { // Keep announcements during countdown? Optional.
            announcements.clear();
            lastAnnouncementTime = 0;
        }

        if (state == GameState.COUNTDOWN) {
            this.countdownEndTimeMillis = timeData; // server sends the countdown end time
        } else {
            this.countdownEndTimeMillis = 0;
        }

        switch (state) {
            case PLAYING:
                this.lobbyReadyByPlayerId.clear();
                this.selectionConfirmed = false;
                this.roundStartTimeMillis = timeData;
                this.finalElapsedTimeMillis = -1;
                // Clear bullets from previous round? Server should dictate respawn/reset.
                // Maybe clear client-side bullets optimistically.
                bullets.clear();
                // Update spectator status based on current lives
                if (localTank != null) {
                    logger.info("Local tank object created. Local tank: {}", localTank.getName());
                    isSpectating = localTank.isDestroyed();
                } else {
                    logger.info("Local tank object not found. Spectating.");
                    isSpectating = true; // Spectate if local tank doesn't exist yet
                }
                playerKills = 0; // Reset player kills on new round
                this.killFeedMessages.clear();
                logger.info("Game state PLAYING. Spectating: {}", isSpectating);
                break;
            case ROUND_OVER:
                this.finalElapsedTimeMillis = timeData;
                bullets.clear(); // Clear bullets at round end
                isSpectating = true; // Force spectate at round over
                logger.info("Game state ROUND_OVER. Spectating forced.");
                break;
            case WAITING:
            case COUNTDOWN:
                // Lobby / tank selection is its own mode, NOT spectating: the round
                // simply hasn't started yet. Keep the normal HUD (health bar, armor
                // diagram, type label) visible behind the selection screen.
                this.roundStartTimeMillis = 0;
                this.finalElapsedTimeMillis = -1;
                bullets.clear(); // Clear bullets while not actively playing
                isSpectating = false;
                logger.info("Game state {}. In lobby (not spectating).", state);
                break;
            case CONNECTING:
                this.roundStartTimeMillis = 0;
                this.finalElapsedTimeMillis = -1;
                bullets.clear();
                isSpectating = true; // No tank exists yet while connecting
                logger.info("Game state CONNECTING. Spectating forced.");
                break;
        }
    }

    // Called when ANNOUNCE is received
    public void addAnnouncement(String message) {
        logger.info("Announcement: {}", message);

        announcements.add(message);

        if (announcements.size() == 1) { // Start timer only if it was empty
            lastAnnouncementTime = System.currentTimeMillis();
        }
    }

    // Called by GameClient on connection failure
    public void connectionFailed(String reason) {
        logger.error("Connection failed: {}", reason);

        currentGameState = GameState.ERROR; // Set an error state
        announcements.clear();

        announcements.add("CONNECTION FAILED");
        announcements.add(reason);
        lastAnnouncementTime = System.currentTimeMillis();

        // Show message and signal window close
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Connection failed: " + reason + "\nExiting.", "Connection Error", JOptionPane.ERROR_MESSAGE));

        if (windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
    }

    // Called by GameClient on graceful disconnect from server
    public void disconnected() {
        logger.warn("Disconnected from server.");

        currentGameState = GameState.ERROR; // Set an error state or specific disconnected state
        announcements.clear();
        announcements.add("DISCONNECTED");
        lastAnnouncementTime = System.currentTimeMillis();

        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Disconnected from server.\nExiting.", "Disconnected", JOptionPane.INFORMATION_MESSAGE));

        if (windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
    }

    private void addStatusMessage(StatusMessageKind statusMessageKind, String message) {
        // Calculate expiry time
        long expiryTime = System.currentTimeMillis() + KILL_FEED_DISPLAY_TIME_MS;

        // Create and add the message object
        killFeedMessages.add(new KillFeedMessage(statusMessageKind, message, expiryTime));
        logger.trace("Added status message: '{}'", message);
    }
}