package kr.kro.teamdodoco.helldiversping.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.kro.teamdodoco.helldiversping.client.ProjectionState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    /** Remember the camera matrices so the HUD can project ping positions onto the screen. */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void helldiversPing$captureMatrices(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                                Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                                Matrix4f projectionMatrix, CallbackInfo ci) {
        ProjectionState.capture(poseStack.last().pose(), projectionMatrix, camera.getPosition(), partialTick);
    }
}
