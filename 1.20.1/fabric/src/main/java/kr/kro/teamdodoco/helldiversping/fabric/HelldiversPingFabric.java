package kr.kro.teamdodoco.helldiversping.fabric;

import kr.kro.teamdodoco.helldiversping.HelldiversPing;
import net.fabricmc.api.ModInitializer;

public final class HelldiversPingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HelldiversPing.init();
    }
}
