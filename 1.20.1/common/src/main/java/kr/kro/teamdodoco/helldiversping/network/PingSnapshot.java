package kr.kro.teamdodoco.helldiversping.network;

import kr.kro.teamdodoco.helldiversping.PingType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Full state of one ping, sent server -> client whenever it is created or changes.
 */
public record PingSnapshot(
        int id,
        UUID owner,
        String ownerName,
        PingType type,
        Vec3 pos,
        int entityId,
        ItemStack icon,
        Component name,
        int count,
        List<UUID> ackerIds,
        List<String> ackerNames,
        int ticksLeft
) {
}
