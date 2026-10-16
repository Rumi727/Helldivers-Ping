package kr.kro.teamdodoco.helldiversping;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Simple properties based config stored in {@code config/helldivers_ping.properties}.
 * Server values only matter on the server (or integrated server), client values only on the client.
 */
public final class PingConfig {
    // --- server ---
    /** How long a fresh ping lives, in ticks. */
    public static int pingDurationTicks = 160;
    /** How long a ping lives after a teammate acknowledged it, in ticks (counted from the acknowledgement). */
    public static int acknowledgedDurationTicks = 600;
    /** Maximum ping distance in blocks. */
    public static double maxPingDistance = 256.0;
    /** Minimum ticks between two ping actions of one player. */
    public static int pingCooldownTicks = 4;
    /** If true, players without a scoreboard team share pings with each other. If false they only see their own. */
    public static boolean shareWithoutTeam = true;
    /** Maximum active pings per player, oldest is removed first. 0 = unlimited. */
    public static int maxPingsPerPlayer = 0;
    /** Remove block pings when the pinged block is broken (becomes air). */
    public static boolean removeWhenBlockBroken = true;

    // --- client ---
    /** Angle (degrees) around the crosshair in which entities get snapped to. */
    public static double entityMagnetDegrees = 3.5;
    /** Angle (degrees) around the crosshair in which an existing ping counts as "aimed at" (acknowledge / cancel). */
    public static double markerHoverDegrees = 2.5;
    /** Show ping names in upper case like Helldivers 2 (off by default). */
    public static boolean uppercaseNames = false;
    /** Volume of ping sounds, 0 disables. */
    public static float soundVolume = 0.6f;
    /** Overall HUD marker scale. */
    public static float markerScale = 1.0f;
    /** Opacity of the dark backdrop behind markers and text, 0 disables it. */
    public static float backgroundOpacity = 0.45f;

    private PingConfig() {
    }

    public static void load() {
        Path file = Platform.getConfigFolder().resolve(HelldiversPing.MOD_ID + ".properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                HelldiversPing.LOGGER.warn("Failed to read {}", file, e);
            }
        }

        pingDurationTicks = getInt(props, "pingDurationTicks", pingDurationTicks);
        acknowledgedDurationTicks = getInt(props, "acknowledgedDurationTicks", acknowledgedDurationTicks);
        maxPingDistance = getDouble(props, "maxPingDistance", maxPingDistance);
        pingCooldownTicks = getInt(props, "pingCooldownTicks", pingCooldownTicks);
        shareWithoutTeam = getBool(props, "shareWithoutTeam", shareWithoutTeam);
        maxPingsPerPlayer = getInt(props, "maxPingsPerPlayer", maxPingsPerPlayer);
        removeWhenBlockBroken = getBool(props, "removeWhenBlockBroken", removeWhenBlockBroken);
        entityMagnetDegrees = getDouble(props, "entityMagnetDegrees", entityMagnetDegrees);
        markerHoverDegrees = getDouble(props, "markerHoverDegrees", markerHoverDegrees);
        uppercaseNames = getBool(props, "uppercaseNames", uppercaseNames);
        soundVolume = (float) getDouble(props, "soundVolume", soundVolume);
        markerScale = (float) getDouble(props, "markerScale", markerScale);
        backgroundOpacity = (float) getDouble(props, "backgroundOpacity", backgroundOpacity);

        save(file);
    }

    private static void save(Path file) {
        Properties props = new Properties();
        props.setProperty("pingDurationTicks", Integer.toString(pingDurationTicks));
        props.setProperty("acknowledgedDurationTicks", Integer.toString(acknowledgedDurationTicks));
        props.setProperty("maxPingDistance", Double.toString(maxPingDistance));
        props.setProperty("pingCooldownTicks", Integer.toString(pingCooldownTicks));
        props.setProperty("shareWithoutTeam", Boolean.toString(shareWithoutTeam));
        props.setProperty("maxPingsPerPlayer", Integer.toString(maxPingsPerPlayer));
        props.setProperty("removeWhenBlockBroken", Boolean.toString(removeWhenBlockBroken));
        props.setProperty("entityMagnetDegrees", Double.toString(entityMagnetDegrees));
        props.setProperty("markerHoverDegrees", Double.toString(markerHoverDegrees));
        props.setProperty("uppercaseNames", Boolean.toString(uppercaseNames));
        props.setProperty("soundVolume", Float.toString(soundVolume));
        props.setProperty("markerScale", Float.toString(markerScale));
        props.setProperty("backgroundOpacity", Float.toString(backgroundOpacity));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, "Helldivers Ping config. Durations are in ticks (20 ticks = 1 second).\n"
                        + "Server: pingDurationTicks, acknowledgedDurationTicks, maxPingDistance, pingCooldownTicks, shareWithoutTeam, maxPingsPerPlayer, removeWhenBlockBroken\n"
                        + "Client: entityMagnetDegrees, markerHoverDegrees, uppercaseNames, soundVolume, markerScale, backgroundOpacity");
            }
        } catch (IOException e) {
            HelldiversPing.LOGGER.warn("Failed to write {}", file, e);
        }
    }

    private static int getInt(Properties props, String key, int def) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double getDouble(Properties props, String key, double def) {
        try {
            return Double.parseDouble(props.getProperty(key, Double.toString(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean getBool(Properties props, String key, boolean def) {
        String value = props.getProperty(key);
        return value == null ? def : Boolean.parseBoolean(value.trim());
    }
}
