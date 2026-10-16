package kr.kro.teamdodoco.helldiversping.client;

import kr.kro.teamdodoco.helldiversping.network.PingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Client copy of a ping. */
public final class ClientPing {
    public PingSnapshot data;
    /** Last position the server reported. */
    private Vec3 serverPos;
    private Vec3 prevPos;
    private Vec3 currentPos;
    public int ticksLeft;
    public int age;
    /** Ticks since the last acknowledgement change, used for a small flash animation. */
    public int ackAge = 1000;

    ClientPing(PingSnapshot data) {
        this.data = data;
        this.serverPos = data.pos();
        this.prevPos = data.pos();
        this.currentPos = data.pos();
        this.ticksLeft = data.ticksLeft();
    }

    void update(PingSnapshot data) {
        if (data.ackerIds().size() != this.data.ackerIds().size()) {
            ackAge = 0;
        }
        this.data = data;
        this.ticksLeft = data.ticksLeft();
        this.serverPos = data.pos();
    }

    void move(Vec3 pos) {
        this.serverPos = pos;
    }

    void tick() {
        age++;
        ackAge++;
        ticksLeft--;
        prevPos = currentPos;
        // Smoothly follow server updates for entities that aren't loaded on this client.
        currentPos = currentPos.lerp(serverPos, 0.5);
    }

    public boolean isOwn() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(data.owner());
    }

    public boolean isAcknowledged() {
        return !data.ackerIds().isEmpty();
    }

    public boolean isAcknowledgedBy(UUID uuid) {
        return data.ackerIds().contains(uuid);
    }

    /** World position of the marker for this frame. Uses the local entity when available for perfectly smooth tracking. */
    public Vec3 position(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (data.entityId() >= 0 && mc.level != null) {
            Entity entity = mc.level.getEntity(data.entityId());
            if (entity != null) {
                Vec3 feet = entity.getPosition(partialTick);
                return feet.add(0, entity.getBbHeight() * 0.5, 0);
            }
        }
        return prevPos.lerp(currentPos, partialTick);
    }
}
