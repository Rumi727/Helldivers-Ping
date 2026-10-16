package kr.kro.teamdodoco.helldiversping.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import kr.kro.teamdodoco.helldiversping.HelldiversPing;
import kr.kro.teamdodoco.helldiversping.PingType;
import kr.kro.teamdodoco.helldiversping.client.ClientPingState;
import kr.kro.teamdodoco.helldiversping.server.ServerPingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 1.20.1 networking (Architectury 9, raw buffers). */
public final class PingNetwork {
    private static final ResourceLocation REQUEST = id("request");
    private static final ResourceLocation UPSERT = id("upsert");
    private static final ResourceLocation REMOVE = id("remove");
    private static final ResourceLocation MOVE = id("move");

    private PingNetwork() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(HelldiversPing.MOD_ID, path);
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST, (buf, context) -> {
            PingRequest request = new PingRequest(buf.readVarInt(), buf.readBlockPos(), readVec3(buf), buf.readVarInt());
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    ServerPingManager.handleRequest(player, request);
                }
            });
        });
    }

    /** Client-only receivers. */
    public static void initClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, UPSERT, (buf, context) -> {
            PingSnapshot snapshot = readSnapshot(buf);
            context.queue(() -> ClientPingState.upsert(snapshot));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, REMOVE, (buf, context) -> {
            int id = buf.readVarInt();
            context.queue(() -> ClientPingState.remove(id));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, MOVE, (buf, context) -> {
            int id = buf.readVarInt();
            Vec3 pos = readVec3(buf);
            context.queue(() -> ClientPingState.move(id, pos));
        });
    }

    // --- sending --------------------------------------------------------------------------------------------------

    /** False when the server doesn't have this mod (or runs a different loader), so requests would be dropped. */
    public static boolean canSendRequest() {
        return NetworkManager.canServerReceive(REQUEST);
    }

    public static void sendRequest(PingRequest request) {
        if (!canSendRequest()) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(request.action());
        buf.writeBlockPos(request.blockPos());
        writeVec3(buf, request.hit());
        buf.writeVarInt(request.targetId());
        NetworkManager.sendToServer(REQUEST, buf);
    }

    public static void sendUpsert(ServerPlayer player, PingSnapshot snapshot) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writeSnapshot(buf, snapshot);
        NetworkManager.sendToPlayer(player, UPSERT, buf);
    }

    public static void sendRemove(ServerPlayer player, int id) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        NetworkManager.sendToPlayer(player, REMOVE, buf);
    }

    public static void sendMove(ServerPlayer player, int id, Vec3 pos) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        writeVec3(buf, pos);
        NetworkManager.sendToPlayer(player, MOVE, buf);
    }

    // --- codec ----------------------------------------------------------------------------------------------------

    private static void writeSnapshot(FriendlyByteBuf buf, PingSnapshot s) {
        buf.writeVarInt(s.id());
        buf.writeUUID(s.owner());
        buf.writeUtf(s.ownerName());
        buf.writeVarInt(s.type().ordinal());
        writeVec3(buf, s.pos());
        buf.writeVarInt(s.entityId());
        buf.writeItem(s.icon());
        buf.writeComponent(s.name());
        buf.writeVarInt(s.count());
        buf.writeVarInt(s.ackerIds().size());
        for (int i = 0; i < s.ackerIds().size(); i++) {
            buf.writeUUID(s.ackerIds().get(i));
            buf.writeUtf(s.ackerNames().get(i));
        }
        buf.writeVarInt(s.ticksLeft());
    }

    private static PingSnapshot readSnapshot(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        UUID owner = buf.readUUID();
        String ownerName = buf.readUtf();
        PingType type = PingType.byId(buf.readVarInt());
        Vec3 pos = readVec3(buf);
        int entityId = buf.readVarInt();
        var icon = buf.readItem();
        var name = buf.readComponent();
        int count = buf.readVarInt();
        int ackers = buf.readVarInt();
        List<UUID> ackerIds = new ArrayList<>(ackers);
        List<String> ackerNames = new ArrayList<>(ackers);
        for (int i = 0; i < ackers; i++) {
            ackerIds.add(buf.readUUID());
            ackerNames.add(buf.readUtf());
        }
        int ticksLeft = buf.readVarInt();
        return new PingSnapshot(id, owner, ownerName, type, pos, entityId, icon, name, count, ackerIds, ackerNames, ticksLeft);
    }

    private static void writeVec3(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
