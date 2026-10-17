package org.chrisgruber.nettank.client.game.effects;

import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Visual effect spawned when a tank is destroyed: the turret is blown off the hull,
 * flies spinning through the air along a parabolic arc with its own ground shadow,
 * and crashes into the earth.
 */
public class FlyingTurretEffect {

    private final Vector2f position;
    private final Vector2f velocity;
    private final Texture turretTexture;
    private final Texture turretMaskTexture;
    private final Vector3f teamColor;
    private final float renderSize;
    private final long startTimeMillis;
    private final long durationMillis;
    private float currentRotation;
    private final float spinSpeed; // degrees per second
    private final float maxAltitude; // peak height in world units

    public FlyingTurretEffect(Vector2f startPos, float initialRotation, Texture texture,
                              Texture maskTexture, Vector3f teamColor, float renderSize) {
        this.position = new Vector2f(startPos);
        double angle = Math.random() * Math.PI * 2;
        float speed = (float) (40.0 + Math.random() * 50.0);
        this.velocity = new Vector2f((float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed);
        this.turretTexture = texture;
        this.turretMaskTexture = maskTexture;
        this.teamColor = teamColor != null ? teamColor : new Vector3f(0.6f, 0.6f, 0.6f);
        this.renderSize = renderSize;
        this.startTimeMillis = System.currentTimeMillis();
        this.durationMillis = 850L;
        this.currentRotation = initialRotation;
        this.spinSpeed = (float) ((Math.random() > 0.5 ? 1 : -1) * (360.0 + Math.random() * 360.0));
        this.maxAltitude = (float) (28.0 + Math.random() * 20.0);
    }

    public boolean isFinished() {
        return System.currentTimeMillis() - startTimeMillis >= durationMillis;
    }

    public void update(float deltaTime) {
        if (isFinished()) return;
        position.add(velocity.x * deltaTime, velocity.y * deltaTime);
        currentRotation += spinSpeed * deltaTime;
    }

    public void renderShadow(Renderer renderer, Shader shadowShader) {
        if (isFinished() || turretTexture == null) return;
        turretTexture.bind();
        // Shadow stays at ground level
        renderer.drawQuad(position.x + 3.0f, position.y - 4.0f,
                renderSize * 0.9f, renderSize * 0.9f, currentRotation, shadowShader);
    }

    public void render(Renderer renderer, Shader tankShader) {
        if (isFinished() || turretTexture == null) return;
        long elapsed = System.currentTimeMillis() - startTimeMillis;
        float t = Math.min(1.0f, elapsed / (float) durationMillis);

        // Parabolic arc: 4 * t * (1 - t) has peak 1.0 at t = 0.5
        float altitude = 4.0f * t * (1.0f - t) * maxAltitude;

        float visualY = position.y + altitude;
        float scale = 1.0f + 0.2f * (altitude / maxAltitude);

        turretTexture.bind();
        if (tankShader != null) {
            tankShader.setUniform4f("u_teamColor", teamColor.x, teamColor.y, teamColor.z, 1.0f);
            tankShader.setUniform4f("u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            tankShader.setUniform1i("u_hasTeamMask", 0);
        }
        renderer.drawQuad(position.x, visualY,
                renderSize * scale, renderSize * scale, currentRotation, tankShader);
    }
}
