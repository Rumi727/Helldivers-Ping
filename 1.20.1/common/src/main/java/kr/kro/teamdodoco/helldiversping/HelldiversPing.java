package kr.kro.teamdodoco.helldiversping;

import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import kr.kro.teamdodoco.helldiversping.network.PingNetwork;
import kr.kro.teamdodoco.helldiversping.server.ServerPingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HelldiversPing {
    public static final String MOD_ID = "helldivers_ping";
    public static final Logger LOGGER = LoggerFactory.getLogger("Helldivers Ping");

    private HelldiversPing() {
    }

    public static void init() {
        PingConfig.load();
        PingNetwork.init();
        ServerPingManager.init();
        EnvExecutor.runInEnv(Env.CLIENT, () -> kr.kro.teamdodoco.helldiversping.client.PingClient::init);
    }
}
