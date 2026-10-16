package kr.kro.teamdodoco.helldiversping.client;

import kr.kro.teamdodoco.helldiversping.PingConfig;
import kr.kro.teamdodoco.helldiversping.network.PingNetwork;
import kr.kro.teamdodoco.helldiversping.network.PingRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Works out what the player is pointing at when they press the ping key. */
public final class PingTargeting {
    private PingTargeting() {
    }

    public static void ping(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null || mc.player == null) {
            return;
        }
        if (!PingNetwork.canSendRequest()) {
            // Pings only exist on the server, so without the mod there nothing would show up.
            mc.player.displayClientMessage(Component.translatable("hud.helldivers_ping.server_missing"), true);
            return;
        }
        float partialTick = ProjectionState.partialTick();
        Vec3 eye = camera.getEyePosition(partialTick);
        Vec3 look = camera.getViewVector(partialTick);

        // 1. Aiming at an existing marker: acknowledge it (or cancel it if it is ours).
        ClientPing aimed = ClientPingState.findAimed(eye, look, partialTick);
        if (aimed != null) {
            PingNetwork.sendRequest(PingRequest.marker(aimed.data.id()));
            return;
        }

        double range = PingConfig.maxPingDistance;
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(eye);

        // 2. Entities near the crosshair snap the ping to them.
        Entity entity = findMagnetEntity(mc, camera, eye, look, range, blockDistance);
        if (entity != null) {
            PingNetwork.sendRequest(PingRequest.entity(entity.getId()));
            return;
        }

        // 3. Otherwise the block we are looking at.
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            PingNetwork.sendRequest(PingRequest.block(blockHit.getBlockPos(), blockHit.getLocation()));
        }
    }

    private static Entity findMagnetEntity(Minecraft mc, Entity camera, Vec3 eye, Vec3 look, double range, double blockDistance) {
        Vec3 end = eye.add(look.scale(range));
        double magnet = Math.toRadians(PingConfig.entityMagnetDegrees);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == camera || entity == mc.player || entity.isSpectator() || !entity.isAlive()) {
                continue;
            }
            if (!(entity instanceof ItemEntity) && !entity.isPickable()) {
                continue;
            }
            if (entity.isInvisibleTo(mc.player) || mc.player.getVehicle() == entity) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            Vec3 center = box.getCenter();
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.3) {
                continue;
            }

            double score;
            var direct = box.inflate(0.15).clip(eye, end);
            if (direct.isPresent()) {
                // The crosshair is right on the entity. It still loses if a wall is in front of it.
                if (direct.get().distanceTo(eye) > blockDistance + 0.1) {
                    continue;
                }
                score = 0;
            } else {
                double angle = Math.acos(Math.max(-1, Math.min(1, to.scale(1 / dist).dot(look))));
                double radius = Math.max(box.getXsize(), box.getYsize()) * 0.5;
                double angularRadius = Math.atan(radius / dist);
                score = Math.max(0, angle - angularRadius);
                if (score > magnet) {
                    continue;
                }
                if (!hasLineOfSight(mc, camera, eye, box)) {
                    continue;
                }
            }
            // Prefer mobs over dropped items when both are close to the crosshair, then closer targets.
            if (entity instanceof LivingEntity) {
                score -= Math.toRadians(0.5);
            }
            score += dist * 1e-5;
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    private static boolean hasLineOfSight(Minecraft mc, Entity camera, Vec3 eye, AABB box) {
        Vec3 center = box.getCenter();
        Vec3[] points = {center, new Vec3(center.x, box.maxY - 0.05, center.z), new Vec3(center.x, box.minY + 0.05, center.z)};
        for (Vec3 point : points) {
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= point.distanceToSqr(eye) - 0.01) {
                return true;
            }
        }
        return false;
    }
}
