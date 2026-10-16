package kr.kro.teamdodoco.helldiversping.client;

import kr.kro.teamdodoco.helldiversping.PingConfig;
import kr.kro.teamdodoco.helldiversping.network.PingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** All pings this client currently knows about. Only touched on the client main thread. */
public final class ClientPingState {
    private static final Map<Integer, ClientPing> PINGS = new LinkedHashMap<>();

    private ClientPingState() {
    }

    public static Collection<ClientPing> all() {
        return PINGS.values();
    }

    public static void upsert(PingSnapshot snapshot) {
        ClientPing existing = PINGS.get(snapshot.id());
        if (existing == null) {
            ClientPing ping = new ClientPing(snapshot);
            PINGS.put(snapshot.id(), ping);
            playSound(SoundEvents.NOTE_BLOCK_BELL.value(), ping.isOwn() ? 1.5f : 1.3f);
        } else {
            int before = existing.data.ackerIds().size();
            existing.update(snapshot);
            if (snapshot.ackerIds().size() > before) {
                playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.8f);
            }
        }
    }

    public static void remove(int id) {
        PINGS.remove(id);
    }

    public static void move(int id, Vec3 pos) {
        ClientPing ping = PINGS.get(id);
        if (ping != null) {
            ping.move(pos);
        }
    }

    public static void clear() {
        PINGS.clear();
    }

    static void tick() {
        for (Iterator<ClientPing> it = PINGS.values().iterator(); it.hasNext(); ) {
            ClientPing ping = it.next();
            ping.tick();
            // The server removes pings itself; this is only a safety net in case a packet got lost.
            if (ping.ticksLeft < -40) {
                it.remove();
            }
        }
    }

    /**
     * Returns the ping marker closest to the crosshair, if one is within {@link PingConfig#markerHoverDegrees}.
     */
    public static ClientPing findAimed(Vec3 eye, Vec3 look, float partialTick) {
        ClientPing best = null;
        double bestAngle = Double.MAX_VALUE;
        double base = Math.toRadians(PingConfig.markerHoverDegrees);
        for (ClientPing ping : PINGS.values()) {
            if (ping.ticksLeft <= 0) {
                continue;
            }
            Vec3 to = ping.position(partialTick).subtract(eye);
            double dist = to.length();
            if (dist < 0.01) {
                continue;
            }
            // Close markers are big on screen, so allow a little more slack for them.
            double allowed = Math.max(base, Math.min(Math.toRadians(8), Math.atan(0.5 / dist)));
            double angle = Math.acos(Math.max(-1, Math.min(1, to.scale(1 / dist).dot(look))));
            if (angle < allowed && angle < bestAngle) {
                best = ping;
                bestAngle = angle;
            }
        }
        return best;
    }

    private static void playSound(SoundEvent sound, float pitch) {
        if (PingConfig.soundVolume <= 0) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, PingConfig.soundVolume));
    }
}
