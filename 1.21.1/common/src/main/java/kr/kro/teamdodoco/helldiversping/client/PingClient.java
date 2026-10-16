package kr.kro.teamdodoco.helldiversping.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import kr.kro.teamdodoco.helldiversping.network.PingNetwork;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class PingClient {
    public static final KeyMapping PING_KEY = new KeyMapping("key.helldivers_ping.ping", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z, "key.categories.helldivers_ping");

    private PingClient() {
    }

    public static void init() {
        KeyMappingRegistry.register(PING_KEY);
        PingNetwork.initClient();

        ClientTickEvent.CLIENT_POST.register(mc -> {
            if (mc.level == null) {
                return;
            }
            ClientPingState.tick();
            while (PING_KEY.consumeClick()) {
                PingTargeting.ping(mc);
            }
        });
        ClientGuiEvent.RENDER_HUD.register((graphics, tickDelta) -> PingHudRenderer.render(graphics));
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ClientPingState.clear());
    }
}
