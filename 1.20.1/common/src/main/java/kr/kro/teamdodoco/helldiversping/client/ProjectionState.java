package kr.kro.teamdodoco.helldiversping.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Camera matrices of the last rendered world frame, captured by {@code LevelRendererMixin}.
 * Used to project world positions onto the HUD.
 */
public final class ProjectionState {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static float partialTick;
    private static boolean valid;

    private ProjectionState() {
    }

    public static void capture(Matrix4f view, Matrix4f projection, Vec3 camera, float tickDelta) {
        VIEW.set(view);
        PROJECTION.set(projection);
        cameraPos = camera;
        partialTick = tickDelta;
        valid = true;
    }

    public static boolean isValid() {
        return valid;
    }

    public static Vec3 cameraPos() {
        return cameraPos;
    }

    public static float partialTick() {
        return partialTick;
    }

    /**
     * Projects a world position to normalized device coordinates.
     *
     * @return {@code {x, y, behind}} with x/y in [-1, 1] when on screen (y up); behind is 1 when the point is behind the camera
     */
    public static float[] project(Vec3 world) {
        Vector4f v = new Vector4f((float) (world.x - cameraPos.x), (float) (world.y - cameraPos.y), (float) (world.z - cameraPos.z), 1f);
        VIEW.transform(v);
        Vector4f view = new Vector4f(v);
        PROJECTION.transform(v);
        if (v.w <= 0.05f) {
            // Behind the camera: use the view space direction so the edge indicator points the right way.
            float x = view.x;
            float y = view.y;
            float len = (float) Math.sqrt(x * x + y * y);
            if (len < 1e-4f) {
                return new float[]{0f, -1f, 1f};
            }
            return new float[]{x / len * 10f, y / len * 10f, 1f};
        }
        return new float[]{v.x / v.w, v.y / v.w, 0f};
    }
}
