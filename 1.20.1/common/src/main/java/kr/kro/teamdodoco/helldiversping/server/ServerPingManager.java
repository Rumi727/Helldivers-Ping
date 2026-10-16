package kr.kro.teamdodoco.helldiversping.server;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import kr.kro.teamdodoco.helldiversping.PingConfig;
import kr.kro.teamdodoco.helldiversping.PingType;
import kr.kro.teamdodoco.helldiversping.network.PingNetwork;
import kr.kro.teamdodoco.helldiversping.network.PingRequest;
import kr.kro.teamdodoco.helldiversping.network.PingSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every active ping. Clients only ever display what this class sends them.
 */
public final class ServerPingManager {
    /** Re-check who should see which ping this often (team changes, dimension changes, joins). */
    private static final int VISIBILITY_INTERVAL = 10;
    /** Send position updates for entity pings this often. */
    private static final int MOVE_INTERVAL = 2;
    /** Block pings within this distance of each other count as "the same spot". */
    private static final double SAME_SPOT_DISTANCE = 1.5;

    private static final Map<Integer, ServerPing> PINGS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    private static int nextId = 1;
    private static long tickCounter;

    private ServerPingManager() {
    }

    public static void init() {
        TickEvent.SERVER_POST.register(ServerPingManager::tick);
        PlayerEvent.PLAYER_JOIN.register(player -> syncVisibility(player.getServer()));
        PlayerEvent.CHANGE_DIMENSION.register((player, oldLevel, newLevel) -> syncVisibility(player.getServer()));
        PlayerEvent.PLAYER_QUIT.register(player -> {
            for (ServerPing ping : PINGS.values()) {
                ping.recipients.remove(player.getUUID());
            }
            LAST_ACTION.remove(player.getUUID());
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            PINGS.clear();
            LAST_ACTION.clear();
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // Requests

    public static void handleRequest(ServerPlayer player, PingRequest request) {
        if (player.isSpectator() && request.action() != PingRequest.MARKER) {
            return;
        }
        long now = player.getServer().getTickCount();
        Long last = LAST_ACTION.get(player.getUUID());
        if (last != null && now - last < PingConfig.pingCooldownTicks) {
            return;
        }
        LAST_ACTION.put(player.getUUID(), now);

        switch (request.action()) {
            case PingRequest.MARKER -> {
                ServerPing ping = PINGS.get(request.targetId());
                if (ping != null && canSee(ping, player)) {
                    interact(ping, player);
                }
            }
            case PingRequest.ENTITY -> pingEntity(player, request.targetId());
            case PingRequest.BLOCK -> pingBlock(player, request.blockPos(), request.hit());
            default -> {
            }
        }
    }

    private static void pingEntity(ServerPlayer player, int entityId) {
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(entityId);
        if (entity == null || entity == player || !entity.isAlive()) {
            return;
        }
        if (entity.distanceTo(player) > PingConfig.maxPingDistance + 8) {
            return;
        }

        ServerPing existing = findMatching(player, ping -> ping.entity == entity);
        if (existing != null) {
            interact(existing, player);
            return;
        }

        ServerPing ping;
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            ItemStack icon = stack.copy();
            icon.setCount(1);
            ping = new ServerPing(nextId++, player, PingType.ITEM, level, entityCenter(entity), BlockPos.ZERO, icon,
                    stack.getHoverName(), stack.getCount());
        } else {
            ping = new ServerPing(nextId++, player, PingType.ENTITY, level, entityCenter(entity), BlockPos.ZERO, ItemStack.EMPTY,
                    entity.getDisplayName(), 1);
        }
        ping.entity = entity;
        ping.entityId = entity.getId();
        create(player, ping);
    }

    private static void pingBlock(ServerPlayer player, BlockPos pos, Vec3 hit) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) {
            return;
        }
        if (player.getEyePosition().distanceTo(hit) > PingConfig.maxPingDistance + 8) {
            return;
        }
        // Keep the marker on the block the client says it hit.
        Vec3 center = Vec3.atCenterOf(pos);
        if (hit.distanceToSqr(center) > 1.0) {
            hit = center;
        }
        Vec3 finalHit = hit;

        ServerPing existing = findMatching(player, ping -> ping.type == PingType.BLOCK
                && (ping.blockPos.equals(pos) || ping.pos.distanceTo(finalHit) < SAME_SPOT_DISTANCE));
        if (existing != null) {
            interact(existing, player);
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        ItemStack icon = new ItemStack(state.getBlock().asItem());
        Component name = state.getBlock().getName();
        if (icon.isEmpty()) {
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                icon = new ItemStack(fluid.getType().getBucket());
            }
        }
        create(player, new ServerPing(nextId++, player, PingType.BLOCK, level, hit, pos.immutable(), icon, name, 1));
    }

    private static ServerPing findMatching(ServerPlayer player, java.util.function.Predicate<ServerPing> predicate) {
        for (ServerPing ping : PINGS.values()) {
            if (canSee(ping, player) && predicate.test(ping)) {
                return ping;
            }
        }
        return null;
    }

    /** The player re-pinged an existing ping: their own one gets cancelled, a teammate's one acknowledged. */
    private static void interact(ServerPing ping, ServerPlayer player) {
        if (ping.owner.equals(player.getUUID())) {
            remove(ping);
            return;
        }
        if (ping.ackers.remove(player.getUUID()) == null) {
            ping.ackers.put(player.getUUID(), player.getGameProfile().getName());
            ping.ticksLeft = Math.max(ping.ticksLeft, PingConfig.acknowledgedDurationTicks);
        } else if (ping.ackers.isEmpty()) {
            ping.ticksLeft = Math.min(ping.ticksLeft, PingConfig.pingDurationTicks);
        }
        broadcastUpsert(ping);
    }

    private static void create(ServerPlayer owner, ServerPing ping) {
        // Unlimited by default. With a cap, the owner's oldest ping makes room for the new one.
        if (PingConfig.maxPingsPerPlayer > 0) {
            List<ServerPing> owned = new ArrayList<>();
            for (ServerPing existing : PINGS.values()) {
                if (existing.owner.equals(owner.getUUID())) {
                    owned.add(existing);
                }
            }
            for (int i = 0; i <= owned.size() - PingConfig.maxPingsPerPlayer; i++) {
                remove(owned.get(i));
            }
        }
        PINGS.put(ping.id, ping);
        syncPing(ping, owner.getServer());
    }

    private static void remove(ServerPing ping) {
        PINGS.remove(ping.id);
        MinecraftServer server = ping.level.getServer();
        for (UUID uuid : ping.recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                PingNetwork.sendRemove(player, ping.id);
            }
        }
        ping.recipients.clear();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Ticking

    private static void tick(MinecraftServer server) {
        tickCounter++;
        if (PINGS.isEmpty()) {
            return;
        }
        for (Iterator<ServerPing> it = new ArrayList<>(PINGS.values()).iterator(); it.hasNext(); ) {
            ServerPing ping = it.next();
            ping.ticksLeft--;
            if (ping.ticksLeft <= 0 || isTargetGone(ping)) {
                remove(ping);
                continue;
            }
            if (ping.entity != null && tickCounter % MOVE_INTERVAL == 0) {
                Vec3 pos = entityCenter(ping.entity);
                if (pos.distanceToSqr(ping.pos) > 0.0004) {
                    ping.pos = pos;
                    for (UUID uuid : ping.recipients) {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null) {
                            PingNetwork.sendMove(player, ping.id, pos);
                        }
                    }
                }
            }
            if (ping.type == PingType.ITEM && ping.entity instanceof ItemEntity item && item.getItem().getCount() != ping.count) {
                ping.count = item.getItem().getCount();
                broadcastUpsert(ping);
            }
        }
        if (tickCounter % VISIBILITY_INTERVAL == 0) {
            syncVisibility(server);
        }
    }

    private static boolean isTargetGone(ServerPing ping) {
        if (ping.entity != null) {
            Entity entity = ping.entity;
            if (entity.isRemoved() || !entity.isAlive() || entity.level() != ping.level) {
                return true;
            }
            return entity instanceof LivingEntity living && living.isDeadOrDying();
        }
        if (PingConfig.removeWhenBlockBroken && ping.type == PingType.BLOCK && tickCounter % 10 == 0
                && ping.level.isLoaded(ping.blockPos)) {
            return ping.level.getBlockState(ping.blockPos).isAir();
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Visibility / syncing

    private static void syncVisibility(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPing ping : new ArrayList<>(PINGS.values())) {
            syncPing(ping, server);
        }
    }

    /** Sends the ping to players who should see it and haven't got it yet, removes it from those who no longer should. */
    private static void syncPing(ServerPing ping, MinecraftServer server) {
        Set<UUID> shouldSee = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (canSee(ping, player)) {
                shouldSee.add(player.getUUID());
                if (ping.recipients.add(player.getUUID())) {
                    PingNetwork.sendUpsert(player, ping.snapshot());
                }
            }
        }
        for (Iterator<UUID> it = ping.recipients.iterator(); it.hasNext(); ) {
            UUID uuid = it.next();
            if (!shouldSee.contains(uuid)) {
                it.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    PingNetwork.sendRemove(player, ping.id);
                }
            }
        }
    }

    private static void broadcastUpsert(ServerPing ping) {
        MinecraftServer server = ping.level.getServer();
        PingSnapshot snapshot = ping.snapshot();
        for (UUID uuid : ping.recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                PingNetwork.sendUpsert(player, snapshot);
            }
        }
        syncPing(ping, server);
    }

    private static boolean canSee(ServerPing ping, ServerPlayer viewer) {
        if (viewer.level() != ping.level) {
            return false;
        }
        if (viewer.getUUID().equals(ping.owner)) {
            return true;
        }
        String viewerTeam = teamName(viewer);
        if (ping.teamName == null) {
            return viewerTeam == null && PingConfig.shareWithoutTeam;
        }
        return ping.teamName.equals(viewerTeam);
    }

    static String teamName(ServerPlayer player) {
        Team team = player.getTeam();
        return team == null ? null : team.getName();
    }

    private static Vec3 entityCenter(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    // ------------------------------------------------------------------------------------------------------------

    private static final class ServerPing {
        final int id;
        final UUID owner;
        final String ownerName;
        final String teamName;
        final PingType type;
        final ServerLevel level;
        final BlockPos blockPos;
        final ItemStack icon;
        final Component name;
        final LinkedHashMap<UUID, String> ackers = new LinkedHashMap<>();
        final Set<UUID> recipients = new HashSet<>();
        Vec3 pos;
        Entity entity;
        int entityId = -1;
        int count;
        int ticksLeft;

        ServerPing(int id, ServerPlayer owner, PingType type, ServerLevel level, Vec3 pos, BlockPos blockPos,
                   ItemStack icon, Component name, int count) {
            this.id = id;
            this.owner = owner.getUUID();
            this.ownerName = owner.getGameProfile().getName();
            this.teamName = teamName(owner);
            this.type = type;
            this.level = level;
            this.pos = pos;
            this.blockPos = blockPos;
            this.icon = icon;
            this.name = name;
            this.count = count;
            this.ticksLeft = PingConfig.pingDurationTicks;
        }

        PingSnapshot snapshot() {
            return new PingSnapshot(id, owner, ownerName, type, pos, entityId, icon, name, count,
                    new ArrayList<>(ackers.keySet()), new ArrayList<>(ackers.values()), ticksLeft);
        }
    }
}
