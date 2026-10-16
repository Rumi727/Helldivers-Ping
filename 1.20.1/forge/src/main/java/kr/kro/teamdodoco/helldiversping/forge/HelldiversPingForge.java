package kr.kro.teamdodoco.helldiversping.forge;

import dev.architectury.platform.forge.EventBuses;
import kr.kro.teamdodoco.helldiversping.HelldiversPing;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(HelldiversPing.MOD_ID)
public final class HelldiversPingForge {
    public HelldiversPingForge() {
        EventBuses.registerModEventBus(HelldiversPing.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        HelldiversPing.init();
    }
}
