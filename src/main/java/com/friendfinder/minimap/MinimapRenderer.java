package com.friendfinder.minimap;

import com.friendfinder.config.FriendFinderConfig;
import com.friendfinder.ping.PingManager;
import com.friendfinder.util.PlayerTracker;
import com.friendfinder.waypoint.Waypoint;
import com.friendfinder.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.List;

public class MinimapRenderer {

    private static final int BLOCK_PX = 2;

    private int[][] terrainCache;
    private int cacheBlockX = Integer.MIN_VALUE;
    private int cacheBlockZ = Integer.MIN_VALUE;

    public void init() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    // ── main render entry ──────────────────────────────────────────────

    private void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        FriendFinderConfig cfg = FriendFinderConfig.getInstance();
        int diameter = cfg.minimapSize;
        int margin   = cfg.minimapMargin;
        int radius   = diameter / 2;
        int blockR   = radius / BLOCK_PX;

        int screenW = mc.getWindow().getScaledWidth();
        int cx = screenW - margin - radius;
        int cy = margin + radius;

        int pBlockX = (int) Math.floor(mc.player.getX());
        int pBlockZ = (int) Math.floor(mc.player.getZ());
        float yaw   = mc.player.getYaw();

        if (terrainCache == null || pBlockX != cacheBlockX || pBlockZ != cacheBlockZ) {
            rebuildTerrainCache(mc.world, pBlockX, pBlockZ, blockR);
            cacheBlockX = pBlockX;
            cacheBlockZ = pBlockZ;
        }

        float yawRad = (float) Math.toRadians(yaw);
        float cosY   = (float) Math.cos(yawRad);
        float sinY   = (float) Math.sin(yawRad);

        drawCircleFill(ctx, cx, cy, radius + 2, 0xFF333333);
        drawCircleFill(ctx, cx, cy, radius,     0xFF1A1A2E);

        drawTerrain(ctx, cx, cy, radius, blockR, cosY, sinY);
        drawWaypoints(ctx, mc, cx, cy, radius, cosY, sinY);
        drawPlayers(ctx, mc, cx, cy, radius, cosY, sinY);
        drawPings(ctx, mc, cx, cy, radius, cosY, sinY);
        drawCompass(ctx, mc.textRenderer, cx, cy, radius, yaw);
        drawPlayerArrow(ctx, cx, cy);
    }

    // ── terrain cache (height-shaded like vanilla maps) ────────────────

    private void rebuildTerrainCache(World world, int originX, int originZ, int blockR) {
        int margin = 1;
        int fullR = blockR + margin;
        int fullSize = fullR * 2 + 1;
        int cacheSize = blockR * 2 + 1;

        int[][] rawColor = new int[fullSize][fullSize];
        int[][] rawHeight = new int[fullSize][fullSize];

        for (int dx = -fullR; dx <= fullR; dx++) {
            for (int dz = -fullR; dz <= fullR; dz++) {
                sampleBlock(world, originX + dx, originZ + dz,
                        rawColor, rawHeight, dx + fullR, dz + fullR);
            }
        }

        terrainCache = new int[cacheSize][cacheSize];
        for (int dx = -blockR; dx <= blockR; dx++) {
            for (int dz = -blockR; dz <= blockR; dz++) {
                int fi = dx + fullR;
                int fj = dz + fullR;
                int color = rawColor[fi][fj];
                if (color == 0) {
                    terrainCache[dx + blockR][dz + blockR] = 0;
                    continue;
                }

                int h = rawHeight[fi][fj];
                int hNorth = rawHeight[fi][fj - 1];
                int hWest = rawHeight[fi - 1][fj];
                int heightDiff = h - (hNorth + hWest) / 2;

                float shade;
                if (heightDiff > 0) {
                    shade = Math.min(1.3f, 1.0f + heightDiff * 0.06f);
                } else if (heightDiff < 0) {
                    shade = Math.max(0.65f, 1.0f + heightDiff * 0.06f);
                } else {
                    shade = 1.0f;
                }

                terrainCache[dx + blockR][dz + blockR] = applyShade(color, shade);
            }
        }
    }

    private void sampleBlock(World world, int x, int z,
                             int[][] colorOut, int[][] heightOut, int ci, int cj) {
        try {
            int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            if (topY < world.getBottomY()) {
                colorOut[ci][cj] = 0;
                heightOut[ci][cj] = world.getBottomY();
                return;
            }

            BlockPos pos = new BlockPos(x, topY, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                colorOut[ci][cj] = 0;
                heightOut[ci][cj] = topY;
                return;
            }

            heightOut[ci][cj] = topY;

            if (!state.getFluidState().isEmpty()) {
                int base = 0xFF000000 | BiomeColors.getWaterColor(world, pos);

                int depth = 0;
                for (int dy = topY - 1; dy >= world.getBottomY() && depth < 24; dy--) {
                    BlockState below = world.getBlockState(new BlockPos(x, dy, z));
                    if (below.getFluidState().isEmpty() && !below.isAir()) break;
                    depth++;
                }
                float depthShade = Math.max(0.5f, 1.0f - depth * 0.03f);
                colorOut[ci][cj] = applyShade(base, depthShade);
                return;
            }

            MapColor mc = state.getMapColor(world, pos);
            if (mc == MapColor.CLEAR) { colorOut[ci][cj] = 0; return; }

            if (state.getBlock() == Blocks.GRASS_BLOCK || state.getBlock() == Blocks.SHORT_GRASS
                    || state.getBlock() == Blocks.TALL_GRASS || state.getBlock() == Blocks.FERN) {
                BlockState below = world.getBlockState(new BlockPos(x, topY - 1, z));
                if (below.isAir() || !below.getFluidState().isEmpty()) {
                    int realSurface = topY - 1;
                    while (realSurface > world.getBottomY()) {
                        BlockState s = world.getBlockState(new BlockPos(x, realSurface, z));
                        if (!s.isAir() && s.getFluidState().isEmpty()
                                && s.getBlock() != Blocks.SHORT_GRASS
                                && s.getBlock() != Blocks.TALL_GRASS
                                && s.getBlock() != Blocks.FERN) {
                            break;
                        }
                        realSurface--;
                    }
                    heightOut[ci][cj] = realSurface;
                    BlockState surfaceState = world.getBlockState(new BlockPos(x, realSurface, z));
                    MapColor surfaceMc = surfaceState.getMapColor(world, new BlockPos(x, realSurface, z));
                    if (surfaceMc != MapColor.CLEAR) {
                        colorOut[ci][cj] = 0xFF000000 | surfaceMc.color;
                        return;
                    }
                }
                colorOut[ci][cj] = 0xFF000000 | BiomeColors.getGrassColor(world, pos);
                return;
            }

            colorOut[ci][cj] = 0xFF000000 | mc.color;
        } catch (Exception e) {
            colorOut[ci][cj] = 0;
            heightOut[ci][cj] = 0;
        }
    }

    private static int applyShade(int argb, float factor) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── terrain drawing (with yaw rotation) ────────────────────────────

    private void drawTerrain(DrawContext ctx, int cx, int cy, int radius,
                             int blockR, float cosY, float sinY) {
        int cacheSize = blockR * 2 + 1;
        int radiusSq  = radius * radius;

        for (int sx = -blockR; sx <= blockR; sx++) {
            for (int sy = -blockR; sy <= blockR; sy++) {
                int px = sx * BLOCK_PX;
                int py = sy * BLOCK_PX;
                if (px * px + py * py > radiusSq) continue;

                // screen → world rotation
                int wdx = Math.round(-sx * cosY + sy * sinY);
                int wdz = Math.round(-sx * sinY - sy * cosY);

                int ci = wdx + blockR;
                int cj = wdz + blockR;
                if (ci < 0 || ci >= cacheSize || cj < 0 || cj >= cacheSize) continue;

                int color = terrainCache[ci][cj];
                if (color == 0) continue;

                ctx.fill(cx + px, cy + py,
                         cx + px + BLOCK_PX, cy + py + BLOCK_PX, color);
            }
        }
    }

    // ── waypoint markers on minimap ────────────────────────────────────

    private void drawWaypoints(DrawContext ctx, MinecraftClient mc,
                               int cx, int cy, int radius,
                               float cosY, float sinY) {
        if (!FriendFinderConfig.getInstance().waypointVisible) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        String dim = mc.world.getRegistryKey().getValue().toString();
        int radiusSq = radius * radius;

        for (Waypoint wp : WaypointManager.getInstance().getWaypoints()) {
            if (!wp.getDimension().equals(dim)) continue;

            double dx = wp.getX() - px;
            double dz = wp.getZ() - pz;

            int sx = (int) ((-cosY * dx - sinY * dz) * BLOCK_PX);
            int sy = (int) (( sinY * dx - cosY * dz) * BLOCK_PX);

            if (sx * sx + sy * sy > radiusSq) {
                // clamp to circle edge
                double dist = Math.sqrt(sx * sx + sy * sy);
                double scale = (radius - 4.0) / dist;
                sx = (int) (sx * scale);
                sy = (int) (sy * scale);
            }

            drawDot(ctx, cx + sx, cy + sy, 5, 0xFF00FFAA);

            String label = wp.getName();
            int tw = mc.textRenderer.getWidth(label);
            ctx.drawText(mc.textRenderer, label,
                    cx + sx - tw / 2, cy + sy - 8, 0xFFFFFFFF, true);
        }
    }

    // ── other player dots ──────────────────────────────────────────────

    private void drawPlayers(DrawContext ctx, MinecraftClient mc,
                             int cx, int cy, int radius,
                             float cosY, float sinY) {
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        int radiusSq = radius * radius;

        for (AbstractClientPlayerEntity player : PlayerTracker.getInstance().getNearbyPlayers()) {
            double dx = player.getX() - px;
            double dz = player.getZ() - pz;

            int sx = (int) ((-cosY * dx - sinY * dz) * BLOCK_PX);
            int sy = (int) (( sinY * dx - cosY * dz) * BLOCK_PX);

            if (sx * sx + sy * sy > radiusSq) {
                double dist = Math.sqrt(sx * sx + sy * sy);
                double scale = (radius - 4.0) / dist;
                sx = (int) (sx * scale);
                sy = (int) (sy * scale);
            }

            drawDot(ctx, cx + sx, cy + sy, 4, 0xFF55FF55);

            String name = player.getName().getString();
            int tw = mc.textRenderer.getWidth(name);
            ctx.drawText(mc.textRenderer, name,
                    cx + sx - tw / 2, cy + sy - 7, 0xFFFFFFFF, true);
        }
    }

    // ── ping markers on minimap ────────────────────────────────────────

    private void drawPings(DrawContext ctx, MinecraftClient mc,
                           int cx, int cy, int radius,
                           float cosY, float sinY) {
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        int radiusSq = radius * radius;
        long now = System.currentTimeMillis();
        long durMs = FriendFinderConfig.getInstance().pingDurationSeconds * 1000L;

        for (PingManager.Ping ping : PingManager.getInstance().getActivePings()) {
            float alpha = ping.getAlpha(now, durMs);
            if (alpha <= 0) continue;

            double dx = ping.x() - px;
            double dz = ping.z() - pz;

            int sx = (int) ((-cosY * dx - sinY * dz) * BLOCK_PX);
            int sy = (int) (( sinY * dx - cosY * dz) * BLOCK_PX);

            if (sx * sx + sy * sy > radiusSq) {
                double dist = Math.sqrt(sx * sx + sy * sy);
                double scale = (radius - 4.0) / dist;
                sx = (int) (sx * scale);
                sy = (int) (sy * scale);
            }

            int a = (int) (alpha * 255) << 24;
            int pingRgb = PingManager.getPlayerColor(ping.sender()) & 0x00FFFFFF;
            drawDot(ctx, cx + sx, cy + sy, 5, a | pingRgb);
        }
    }

    // ── compass labels ─────────────────────────────────────────────────

    private void drawCompass(DrawContext ctx, TextRenderer tr,
                             int cx, int cy, int radius, float yaw) {
        float labelR = radius - 8;
        drawCardinal(ctx, tr, cx, cy, labelR, yaw, "N", 180,  0xFFFF5555);
        drawCardinal(ctx, tr, cx, cy, labelR, yaw, "S", 0,    0xFFFFFFFF);
        drawCardinal(ctx, tr, cx, cy, labelR, yaw, "E", -90,  0xFFFFFFFF);
        drawCardinal(ctx, tr, cx, cy, labelR, yaw, "W", 90,   0xFFFFFFFF);
    }

    private void drawCardinal(DrawContext ctx, TextRenderer tr,
                              int cx, int cy, float labelR, float yaw,
                              String letter, float worldYaw, int color) {
        float angle = (float) Math.toRadians(worldYaw - yaw);
        int lx = cx + (int) (labelR * Math.sin(angle));
        int ly = cy - (int) (labelR * Math.cos(angle));
        int tw = tr.getWidth(letter);
        ctx.drawText(tr, letter, lx - tw / 2, ly - tr.fontHeight / 2, color, true);
    }

    // ── player arrow at center ─────────────────────────────────────────

    private void drawPlayerArrow(DrawContext ctx, int cx, int cy) {
        // small upward-pointing arrow
        ctx.fill(cx - 1, cy - 4, cx + 2, cy - 2, 0xFFFFFFFF);
        ctx.fill(cx - 2, cy - 2, cx + 3, cy,     0xFFFFFFFF);
        ctx.fill(cx - 1, cy,     cx + 2, cy + 2,  0xFFDDDDDD);
    }

    // ── drawing helpers ────────────────────────────────────────────────

    private void drawCircleFill(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.sqrt((long) r * r - (long) y * y);
            ctx.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
    }

    private void drawDot(DrawContext ctx, int x, int y, int size, int color) {
        int h = size / 2;
        ctx.fill(x - h, y - h, x - h + size, y - h + size, color);
    }
}
