package org.chrisgruber.nettank.client.engine.graphics;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class Camera {

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private Vector2f position;
    private float zoom = 1.0f; // Added zoom capability

    private float screenWidth;
    private float screenHeight;

    public Camera(float screenWidth, float screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.position = new Vector2f(0, 0);
        this.projectionMatrix = new Matrix4f();
        this.viewMatrix = new Matrix4f();
        updateProjection();
        updateView();
    }

    private void updateProjection() {
        // Orthographic projection: left, right, bottom, top, near, far
        // Adjust based on zoom level
        float left = -screenWidth / 2.0f * zoom;
        float right = screenWidth / 2.0f * zoom;
        float bottom = -screenHeight / 2.0f * zoom;
        float top = screenHeight / 2.0f * zoom;
        projectionMatrix.setOrtho(left, right, bottom, top, -1.0f, 1.0f);
    }

    private void updateView() {
        // The view matrix transforms world coordinates to camera coordinates.
        // It's the inverse of the camera's transformation.
        // We move the world in the opposite direction of the camera.
        Vector3f cameraPos = new Vector3f(-position.x, -position.y, 0);
        viewMatrix.identity().translate(cameraPos);
        // Apply zoom by scaling the view matrix after translation
        // viewMatrix.scale(1.0f / zoom); // Scaling the view is equivalent to adjusting ortho projection bounds
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
        // No need to update view immediately, call update() explicitly
    }

    public void move(float dx, float dy) {
        this.position.add(dx, dy);
        // No need to update view immediately, call update() explicitly
    }

    public Vector2f getPosition() {
        return position;
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(0.1f, zoom); // Prevent zoom <= 0
        updateProjection(); // Projection depends on zoom
        // View matrix update might also be needed if zoom affects view directly,
        // but here it's handled in projection.
    }

    public float getZoom() {
        return zoom;
    }

    // Call this after changing position or zoom before rendering
    public void update() {
        updateView();
        // Projection updated only when zoom or screen size changes.
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    public float getViewLeft() {
        return position.x - (screenWidth / 2.0f) * zoom;
    }

    public float getViewRight() {
        return position.x + (screenWidth / 2.0f) * zoom;
    }

    public float getViewBottom() {
        return position.y - (screenHeight / 2.0f) * zoom;
    }

    public float getViewTop() {
        return position.y + (screenHeight / 2.0f) * zoom;
    }

    public void clampToWorldBounds(float worldWidth, float worldHeight) {
        float halfViewWidth = (screenWidth / 2.0f) * zoom;
        float halfViewHeight = (screenHeight / 2.0f) * zoom;

        // Clamp X position
        if (halfViewWidth >= worldWidth / 2.0f) {
            // View is wider than world - center on world
            position.x = worldWidth / 2.0f;
        } else {
            position.x = Math.max(halfViewWidth, Math.min(position.x, worldWidth - halfViewWidth));
        }

        // Clamp Y position
        if (halfViewHeight >= worldHeight / 2.0f) {
            // View is taller than world - center on world
            position.y = worldHeight / 2.0f;
        } else {
            position.y = Math.max(halfViewHeight, Math.min(position.y, worldHeight - halfViewHeight));
        }
    }
}
