package kr.kro.teamdodoco.helldiversping.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import kr.kro.teamdodoco.helldiversping.HelldiversPing;
import kr.kro.teamdodoco.helldiversping.PingType;
import kr.kro.teamdodoco.helldiversping.client.ClientPingState;
import kr.kro.teamdodoco.helldiversping.server.ServerPingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 1.21.1 networking (Architectury 13, custom payloads). */
public final class PingNetwork {
    private PingNetwork() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(HelldiversPing.MOD_ID, path);
    }

    // --- payloads -------------------------------------------------------------------------------------------------

    public record RequestPayload(PingRequest request) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(rl("request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.request.action());
                    buf.writeBlockPos(p.request.blockPos());
                    writeVec3(buf, p.request.hit());
                    buf.writeVarInt(p.request.targetId());
                },
                buf -> new RequestPayload(new PingRequest(buf.readVarInt(), buf.readBlockPos(), readVec3(buf), buf.readVarInt())));

        @Override
        public Type<RequestPayload> type() {
            return TYPE;
        }
    }

    public record UpsertPayload(PingSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<UpsertPayload> TYPE = new Type<>(rl("upsert"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpsertPayload> CODEC = StreamCodec.of(
                (buf, p) -> writeSnapshot(buf, p.snapshot), buf -> new UpsertPayload(readSnapshot(buf)));

        @Override
        public Type<UpsertPayload> type() {
            return TYPE;
        }
    }

    public record RemovePayload(int id) implements CustomPacketPayload {
        public static final Type<RemovePayload> TYPE = new Type<>(rl("remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemovePayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.id), buf -> new RemovePayload(buf.readVarInt()));

        @Override
        public Type<RemovePayload> type() {
            return TYPE;
        }
    }

    public record MovePayload(int id, Vec3 pos) implements CustomPacketPayload {
        public static final Type<MovePayload> TYPE = new Type<>(rl("move"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MovePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    writeVec3(buf, p.pos);
                },
                buf -> new MovePayload(buf.readVarInt(), readVec3(buf)));

        @Override
        public Type<MovePayload> type() {
            return TYPE;
        }
    }

    // --- registration ---------------------------------------------------------------------------------------------

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RequestPayload.TYPE, RequestPayload.CODEC, (payload, context) ->
                context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerPingManager.handleRequest(player, payload.request());
                    }
                }));
        if (Platform.getEnvironment() == Env.SERVER) {
            // Dedicated servers still need to know the server -> client payload types to be able to send them.
            NetworkManager.registerS2CPayloadType(UpsertPayload.TYPE, UpsertPayload.CODEC);
            NetworkManager.registerS2CPayloadType(RemovePayload.TYPE, RemovePayload.CODEC);
            NetworkManager.registerS2CPayloadType(MovePayload.TYPE, MovePayload.CODEC);
        }
    }

    /** Client-only receivers. */
    public static void initClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, UpsertPayload.TYPE, UpsertPayload.CODEC, (payload, context) ->
                context.queue(() -> ClientPingState.upsert(payload.snapshot())));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RemovePayload.TYPE, RemovePayload.CODEC, (payload, context) ->
                context.queue(() -> ClientPingState.remove(payload.id())));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, MovePayload.TYPE, MovePayload.CODEC, (payload, context) ->
                context.queue(() -> ClientPingState.move(payload.id(), payload.pos())));
    }

    // --- sending --------------------------------------------------------------------------------------------------

    /** False when the server doesn't have this mod (or runs a different loader), so requests would be dropped. */
    public static boolean canSendRequest() {
        return NetworkManager.canServerReceive(RequestPayload.TYPE);
    }

    public static void sendRequest(PingRequest request) {
        if (canSendRequest()) {
            NetworkManager.sendToServer(new RequestPayload(request));
        }
    }

    public static void sendUpsert(ServerPlayer player, PingSnapshot snapshot) {
        NetworkManager.sendToPlayer(player, new UpsertPayload(snapshot));
    }

    public static void sendRemove(ServerPlayer player, int id) {
        NetworkManager.sendToPlayer(player, new RemovePayload(id));
    }

    public static void sendMove(ServerPlayer player, int id, Vec3 pos) {
        NetworkManager.sendToPlayer(player, new MovePayload(id, pos));
    }

    // --- codec ----------------------------------------------------------------------------------------------------

    private static void writeSnapshot(RegistryFriendlyByteBuf buf, PingSnapshot s) {
        buf.writeVarInt(s.id());
        buf.writeUUID(s.owner());
        buf.writeUtf(s.ownerName());
        buf.writeVarInt(s.type().ordinal());
        writeVec3(buf, s.pos());
        buf.writeVarInt(s.entityId());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.icon());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, s.name());
        buf.writeVarInt(s.count());
        buf.writeVarInt(s.ackerIds().size());
        for (int i = 0; i < s.ackerIds().size(); i++) {
            buf.writeUUID(s.ackerIds().get(i));
            buf.writeUtf(s.ackerNames().get(i));
        }
        buf.writeVarInt(s.ticksLeft());
    }

    private static PingSnapshot readSnapshot(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        UUID owner = buf.readUUID();
        String ownerName = buf.readUtf();
        PingType type = PingType.byId(buf.readVarInt());
        Vec3 pos = readVec3(buf);
        int entityId = buf.readVarInt();
        ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        var name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
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
