package kr.kro.teamdodoco.helldiversping.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Client -> server request.
 *
 * @param action   one of {@link #BLOCK}, {@link #ENTITY}, {@link #MARKER}
 * @param blockPos pinged block (BLOCK only)
 * @param hit      exact hit location (BLOCK only)
 * @param targetId entity id (ENTITY) or ping id (MARKER)
 */
public record PingRequest(int action, BlockPos blockPos, Vec3 hit, int targetId) {
    public static final int BLOCK = 0;
    public static final int ENTITY = 1;
    /** The player aimed at an existing ping marker: acknowledge it, or cancel it if it is their own. */
    public static final int MARKER = 2;

    public static PingRequest block(BlockPos pos, Vec3 hit) {
        return new PingRequest(BLOCK, pos, hit, -1);
    }

    public static PingRequest entity(int entityId) {
        return new PingRequest(ENTITY, BlockPos.ZERO, Vec3.ZERO, entityId);
    }

    public static PingRequest marker(int pingId) {
        return new PingRequest(MARKER, BlockPos.ZERO, Vec3.ZERO, pingId);
    }
}
