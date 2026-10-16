package kr.kro.teamdodoco.helldiversping.client;

import com.mojang.math.Axis;
import kr.kro.teamdodoco.helldiversping.PingConfig;
import kr.kro.teamdodoco.helldiversping.PingType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Draws Helldivers 2 style ping markers on the HUD. */
public final class PingHudRenderer {
    private static final int COLOR_NORMAL = 0xF0F0F0;
    private static final int COLOR_ACKED = 0xFFD43B;
    private static final int COLOR_SUB = 0xB8B8B8;
    private static final int EDGE_MARGIN = 14;
    private static final int FADE_TICKS = 20;

    private PingHudRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ProjectionState.isValid() || ClientPingState.all().isEmpty()) {
            return;
        }
        float partialTick = ProjectionState.partialTick();
        Vec3 camera = ProjectionState.cameraPos();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        Vec3 eye = mc.getCameraEntity() != null ? mc.getCameraEntity().getEyePosition(partialTick) : camera;
        Vec3 look = mc.getCameraEntity() != null ? mc.getCameraEntity().getViewVector(partialTick) : new Vec3(0, 0, 1);
        ClientPing aimed = ClientPingState.findAimed(eye, look, partialTick);

        // Far pings first so close ones are drawn on top.
        List<ClientPing> pings = new ArrayList<>(ClientPingState.all());
        pings.sort(Comparator.comparingDouble((ClientPing p) -> -p.position(partialTick).distanceToSqr(camera)));

        for (ClientPing ping : pings) {
            float alpha = Mth.clamp((ping.ticksLeft - partialTick) / FADE_TICKS, 0f, 1f);
            if (alpha <= 0.02f) {
                continue;
            }
            Vec3 pos = ping.position(partialTick);
            float[] ndc = ProjectionState.project(pos);
            float sx = (ndc[0] * 0.5f + 0.5f) * width;
            float sy = (1f - (ndc[1] * 0.5f + 0.5f)) * height;
            boolean behind = ndc[2] > 0;
            boolean offScreen = behind || sx < EDGE_MARGIN || sx > width - EDGE_MARGIN || sy < EDGE_MARGIN || sy > height - EDGE_MARGIN;
            if (offScreen) {
                // Push the marker onto the screen edge in the direction of the ping.
                float cx = width / 2f;
                float cy = height / 2f;
                float dx = sx - cx;
                float dy = sy - cy;
                float limitX = cx - EDGE_MARGIN;
                float limitY = cy - EDGE_MARGIN;
                float scale = Math.max(Math.abs(dx) / limitX, Math.abs(dy) / limitY);
                if (scale < 1e-4f) {
                    dy = limitY;
                    scale = 1;
                }
                sx = cx + dx / scale;
                sy = cy + dy / scale;
            }

            double distance = pos.distanceTo(camera);
            drawPing(graphics, mc.font, ping, sx, sy, alpha, partialTick, offScreen, distance, ping == aimed);
        }
    }

    private static void drawPing(GuiGraphics graphics, Font font, ClientPing ping, float x, float y, float alpha,
                                 float partialTick, boolean offScreen, double distance, boolean aimed) {
        int rgb = ping.isAcknowledged() ? COLOR_ACKED : COLOR_NORMAL;
        int color = withAlpha(rgb, alpha);
        int subColor = withAlpha(COLOR_SUB, alpha);
        int bg = backdrop(alpha);

        // Small pop-in animation, and a pulse when someone acknowledges.
        float pop = Math.min(1f, (ping.age + partialTick) / 4f);
        float scale = PingConfig.markerScale * (0.6f + 0.4f * easeOutBack(pop));
        if (ping.ackAge < 8) {
            scale *= 1f + 0.25f * (1f - (ping.ackAge + partialTick) / 8f);
        }
        if (offScreen) {
            scale *= 0.75f;
        }

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);

        String distanceText = formatDistance(distance);
        boolean entityStyle = ping.data.type() == PingType.ENTITY;

        if (entityStyle) {
            // Diamond centered on the target, text to the right.
            drawDiamond(graphics, color, bg);
            drawTextWithBackdrop(graphics, font, distanceText, -font.width(distanceText) / 2, 14, color, bg);
            if (!offScreen) {
                drawLabel(graphics, font, ping, 15, -9, color, subColor, bg, aimed);
            }
        } else {
            // Bracket box with the icon, distance below it and a small arrow pointing at the exact spot.
            drawArrow(graphics, color, bg);
            drawTextWithBackdrop(graphics, font, distanceText, -font.width(distanceText) / 2, -15, color, bg);
            pose.pushPose();
            pose.translate(0, -29, 0);
            drawBracketBox(graphics, color, bg);
            if (!ping.data.icon().isEmpty() && alpha > 0.3f) {
                graphics.renderItem(ping.data.icon(), -8, -8);
            } else if (ping.data.icon().isEmpty()) {
                drawEye(graphics, color);
            }
            if (!offScreen) {
                pose.translate(0, 0, 200); // above the item model
                drawLabel(graphics, font, ping, 15, -9, color, subColor, bg, aimed);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    /** Name on the first line, who pinged / who acknowledged below it, and an action hint when aimed at. */
    private static void drawLabel(GuiGraphics graphics, Font font, ClientPing ping, int x, int y, int color, int subColor,
                                  int bg, boolean aimed) {
        String name = ping.data.name().getString();
        if (PingConfig.uppercaseNames) {
            name = name.toUpperCase(Locale.ROOT);
        }
        if (ping.data.count() > 1) {
            name = name + " x" + ping.data.count();
        }

        // Small lines, drawn at 75% scale: (text, color).
        List<Component> lines = new ArrayList<>();
        List<Integer> lineColors = new ArrayList<>();
        if (ping.isAcknowledged()) {
            List<String> names = ping.data.ackerNames();
            String who = names.size() == 1 ? names.get(0) : names.get(0) + " +" + (names.size() - 1);
            lines.add(Component.translatable("hud.helldivers_ping.acknowledged", who));
            lineColors.add(color);
        }
        lines.add(Component.translatable("hud.helldivers_ping.by", ping.data.ownerName()));
        lineColors.add(subColor);

        String keyText = null;
        Component action = null;
        if (aimed) {
            keyText = PingClient.PING_KEY.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
            Minecraft mc = Minecraft.getInstance();
            if (ping.isOwn()) {
                action = Component.translatable("hud.helldivers_ping.cancel");
            } else if (mc.player != null && ping.isAcknowledgedBy(mc.player.getUUID())) {
                action = Component.translatable("hud.helldivers_ping.unacknowledge");
            } else {
                action = Component.translatable("hud.helldivers_ping.acknowledge");
            }
        }

        // Backdrop sized to everything we are about to draw.
        float small = 0.75f;
        int smallWidth = 0;
        for (Component line : lines) {
            smallWidth = Math.max(smallWidth, font.width(line));
        }
        int keyWidth = keyText == null ? 0 : font.width(keyText) + 4;
        if (action != null) {
            smallWidth = Math.max(smallWidth, keyWidth + 3 + font.width(action));
        }
        int smallLines = lines.size() + (action != null ? 1 : 0);
        int width = Math.max(font.width(name), (int) Math.ceil(smallWidth * small));
        int height = 10 + (int) Math.ceil(smallLines * 10 * small);
        if (bg != 0) {
            graphics.fill(x - 3, y - 2, x + width + 3, y + height, bg);
        }

        graphics.drawString(font, name, x, y, color, true);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y + 10, 0);
        pose.scale(small, small, 1);
        int lineY = 0;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), 0, lineY, lineColors.get(i), true);
            lineY += 10;
        }
        if (action != null) {
            lineY += 1;
            graphics.fill(0, lineY - 1, keyWidth, lineY + 9, withAlpha(0xF0F0F0, (color >>> 24) / 255f));
            graphics.drawString(font, keyText, 2, lineY, 0xFF202020, false);
            graphics.drawString(font, action, keyWidth + 3, lineY, color, true);
        }
        pose.popPose();
    }

    private static void drawTextWithBackdrop(GuiGraphics g, Font font, String text, int x, int y, int color, int bg) {
        if (bg != 0) {
            g.fill(x - 2, y - 2, x + font.width(text) + 1, y + 9, bg);
        }
        g.drawString(font, text, x, y, color, true);
    }

    // --- shapes ---------------------------------------------------------------------------------------------------

    /** Corner brackets like the Helldivers 2 marker frame, 22x22 centered at 0,0. */
    private static void drawBracketBox(GuiGraphics g, int color, int bg) {
        int h = 11;
        if (bg != 0) {
            g.fill(-h, -h, h, h, bg);
        }
        int len = 5;
        int t = 1;
        // top-left
        g.fill(-h, -h, -h + len, -h + t, color);
        g.fill(-h, -h, -h + t, -h + len, color);
        // top-right
        g.fill(h - len, -h, h, -h + t, color);
        g.fill(h - t, -h, h, -h + len, color);
        // bottom-left
        g.fill(-h, h - t, -h + len, h, color);
        g.fill(-h, h - len, -h + t, h, color);
        // bottom-right
        g.fill(h - len, h - t, h, h, color);
        g.fill(h - t, h - len, h, h, color);
    }

    /** Diamond outline with a small crosshair inside, centered at 0,0. */
    private static void drawDiamond(GuiGraphics g, int color, int bg) {
        var pose = g.pose();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(45));
        int s = 6;
        if (bg != 0) {
            g.fill(-s - 1, -s - 1, s + 1, s + 1, bg);
        }
        g.fill(-s, -s, s, -s + 1, color);
        g.fill(-s, s - 1, s, s, color);
        g.fill(-s, -s, -s + 1, s, color);
        g.fill(s - 1, -s, s, s, color);
        pose.popPose();
        // crosshair ticks and center dot
        g.fill(-1, -12, 1, -9, color);
        g.fill(-1, 9, 1, 12, color);
        g.fill(-12, -1, -9, 1, color);
        g.fill(9, -1, 12, 1, color);
        g.fill(-1, -1, 1, 1, color);
    }

    /** Small downward triangle whose tip is at 0,0. */
    private static void drawArrow(GuiGraphics g, int color, int bg) {
        if (bg != 0) {
            for (int row = 0; row < 5; row++) {
                g.fill(-row - 2, -row, row + 2, -row + 1, bg);
            }
        }
        for (int row = 0; row < 4; row++) {
            g.fill(-row - 1, -row - 1, row + 1, -row, color);
        }
    }

    /** Fallback icon for blocks without an item (fire, portals...). */
    private static void drawEye(GuiGraphics g, int color) {
        g.fill(-5, -1, 5, 1, color);
        g.fill(-3, -3, 3, -2, color);
        g.fill(-3, 2, 3, 3, color);
        g.fill(-1, -1, 1, 1, 0xFF000000 | (color & 0xFFFFFF));
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private static String formatDistance(double distance) {
        if (distance >= 1000) {
            return String.format(Locale.ROOT, "%.1fkm", distance / 1000.0);
        }
        return (int) Math.round(distance) + "m";
    }

    /** Translucent black, or 0 when backdrops are disabled. */
    private static int backdrop(float alpha) {
        float opacity = PingConfig.backgroundOpacity * alpha;
        if (opacity <= 0.01f) {
            return 0;
        }
        return Math.min(255, (int) (opacity * 255)) << 24;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }
}
