package com.friendfinder.radar;

import com.friendfinder.util.PlayerTracker;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class FriendRadarRenderer {

    public void init() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    private void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        int screenW = mc.getWindow().getScaledWidth();
        float playerYaw = mc.player.getYaw();

        int padX = 50;
        int topY = 6;

        for (AbstractClientPlayerEntity target : PlayerTracker.getInstance().getNearbyPlayers()) {
            double dx = target.getX() - mc.player.getX();
            double dz = target.getZ() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist < 5) continue;

            double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
            double relAngle = normalizeAngle(angleToTarget - playerYaw);

            // Map relative angle (-180..180) to horizontal position across the top
            double t = relAngle / 180.0;
            int ix = screenW / 2 + (int) (t * (screenW / 2.0 - padX));
            ix = clamp(ix, padX, screenW - padX);

            drawIndicator(ctx, ix, topY + 4);

            String label = target.getName().getString() + " — " + (int) dist + "m";
            TextRenderer tr = mc.textRenderer;
            int tw = tr.getWidth(label);
            int lx = clamp(ix - tw / 2, 4, screenW - tw - 4);
            int ly = topY + 12;

            ctx.fill(lx - 2, ly - 1, lx + tw + 2, ly + 10, 0x90000000);
            ctx.drawText(tr, label, lx, ly, 0xFF55FF55, true);
        }
    }

    private void drawIndicator(DrawContext ctx, int x, int y) {
        // Outer glow
        ctx.fill(x - 4, y - 4, x + 5, y + 5, 0x4055FF55);
        // Inner dot
        ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFF55FF55);
        // Bright center
        ctx.fill(x - 1, y - 1, x + 2, y + 2, 0xFFAAFFAA);
    }

    private static double normalizeAngle(double angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
