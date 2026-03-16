package com.friendfinder.worldmap;

import com.friendfinder.config.FriendFinderConfig;
import com.friendfinder.ping.PingManager;
import com.friendfinder.util.PlayerTracker;
import com.friendfinder.waypoint.Waypoint;
import com.friendfinder.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public class WorldMapScreen extends Screen {

    private static final int MIN_ZOOM = 1;
    private static final int MAX_ZOOM = 16;

    private static final int MARGIN = 10;
    private static final int BORDER = 12;

    private static final int COL_SHADOW       = 0x55000000;
    private static final int COL_OUTER_EDGE   = 0xFF1E140A;
    private static final int COL_PARCHMENT    = 0xFFC2AD82;
    private static final int COL_PARCH_HI     = 0xFFD6C9A4;
    private static final int COL_PARCH_LO     = 0xFFA89160;
    private static final int COL_INNER_EDGE   = 0xFF4A3828;
    private static final int COL_CORNER       = 0xFF7A6543;
    private static final int COL_MAP_BG       = 0xFF0D0D1A;
    private static final int COL_TEXT          = 0xFFE8DCC8;
    private static final int COL_TEXT_DIM      = 0xFF9B8B72;
    private static final int COL_CROSSHAIR    = 0x30FFFFFF;
    private static final int COL_GRID         = 0x14FFFFFF;

    private double viewX, viewZ;
    private int zoom = 2;
    private boolean dragging;
    private double dragStartX, dragStartY;
    private double dragViewX, dragViewZ;

    private int mapL, mapT, mapR, mapB, mapCX, mapCY;

    public WorldMapScreen() {
        super(Text.literal("World Map"));
    }

    @Override
    protected void init() {
        super.init();
        if (client != null && client.player != null) {
            viewX = client.player.getX();
            viewZ = client.player.getZ();
        }
        computeBounds();
    }

    private void computeBounds() {
        int inset = MARGIN + BORDER + 1;
        mapL = inset;
        mapT = inset;
        mapR = width - inset;
        mapB = height - inset;
        mapCX = (mapL + mapR) / 2;
        mapCY = (mapT + mapB) / 2;
    }

    // ── input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double hAmt, double vAmt) {
        int old = zoom;
        if (vAmt > 0 && zoom > MIN_ZOOM) zoom--;
        else if (vAmt < 0 && zoom < MAX_ZOOM) zoom++;

        if (zoom != old && inMap(mx, my)) {
            double wx = viewX + (mx - mapCX) * old;
            double wz = viewZ + (my - mapCY) * old;
            viewX = wx - (mx - mapCX) * zoom;
            viewZ = wz - (my - mapCY) * zoom;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && inMap(mx, my)) {
            dragging = true;
            dragStartX = mx;
            dragStartY = my;
            dragViewX = viewX;
            dragViewZ = viewZ;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) { dragging = false; return true; }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn,
                                double dx, double dy) {
        if (dragging && btn == 0) {
            viewX = dragViewX - (mx - dragStartX) * zoom;
            viewZ = dragViewZ - (my - dragStartY) * zoom;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_M ||
            key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    private boolean inMap(double mx, double my) {
        return mx >= mapL && mx <= mapR && my >= mapT && my <= mapB;
    }

    // ── rendering ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        if (client == null || client.player == null || client.world == null) return;

        computeBounds();

        drawFrame(ctx);

        ctx.enableScissor(mapL, mapT, mapR, mapB);
        drawTerrain(ctx);
        drawGrid(ctx);
        drawWaypoints(ctx);
        drawPings(ctx);
        drawPlayers(ctx);
        drawPlayerMarker(ctx);
        drawCrosshair(ctx);
        drawCardinals(ctx);
        ctx.disableScissor();

        drawInfoBar(ctx, mouseX, mouseY);
        drawTitle(ctx);
    }

    // ── map frame (Minecraft map-item inspired parchment border) ─────────

    private void drawFrame(DrawContext ctx) {
        int m = MARGIN;
        int b = BORDER;
        int W = width, H = height;

        // drop shadow
        ctx.fill(m + 4, m + 4, W - m + 4, H - m + 4, COL_SHADOW);

        // outer dark edge
        ctx.fill(m, m, W - m, H - m, COL_OUTER_EDGE);

        // parchment body
        ctx.fill(m + 2, m + 2, W - m - 2, H - m - 2, COL_PARCHMENT);

        // bevel: top & left lighter, bottom & right darker
        ctx.fill(m + 2, m + 2, W - m - 2, m + 4, COL_PARCH_HI);
        ctx.fill(m + 2, m + 4, m + 4, H - m - 4, COL_PARCH_HI);
        ctx.fill(m + 4, H - m - 4, W - m - 2, H - m - 2, COL_PARCH_LO);
        ctx.fill(W - m - 4, m + 4, W - m - 2, H - m - 4, COL_PARCH_LO);

        // corner accents
        cornerDeco(ctx, m + 3, m + 3);
        cornerDeco(ctx, W - m - 3 - 6, m + 3);
        cornerDeco(ctx, m + 3, H - m - 3 - 6);
        cornerDeco(ctx, W - m - 3 - 6, H - m - 3 - 6);

        // inner edge
        ctx.fill(m + b, m + b, W - m - b, H - m - b, COL_INNER_EDGE);

        // map background
        ctx.fill(mapL, mapT, mapR, mapB, COL_MAP_BG);
    }

    private void cornerDeco(DrawContext ctx, int x, int y) {
        ctx.fill(x, y, x + 6, y + 2, COL_CORNER);
        ctx.fill(x, y + 2, x + 2, y + 6, COL_CORNER);
        ctx.fill(x + 4, y + 2, x + 6, y + 6, COL_CORNER);
        ctx.fill(x, y + 4, x + 6, y + 6, COL_CORNER);
    }

    // ── terrain (reads from MapDataManager's persistent explored cache) ──

    private void drawTerrain(DrawContext ctx) {
        World world = client.world;
        MapDataManager mapData = MapDataManager.getInstance();
        int halfW = ((mapR - mapL) / 2 + 1) * zoom;
        int halfH = ((mapB - mapT) / 2 + 1) * zoom;

        int startX = (int) viewX - halfW;
        int startZ = (int) viewZ - halfH;
        int endX   = (int) viewX + halfW;
        int endZ   = (int) viewZ + halfH;
        int step   = zoom;

        int lastCX = Integer.MIN_VALUE, lastCZ = Integer.MIN_VALUE;
        int[] lastColors = null;

        for (int wx = startX; wx < endX; wx += step) {
            for (int wz = startZ; wz < endZ; wz += step) {
                int cx = wx >> 4, cz = wz >> 4;
                if (cx != lastCX || cz != lastCZ) {
                    lastCX = cx;
                    lastCZ = cz;
                    lastColors = mapData.getOrSample(world, cx, cz);
                }
                if (lastColors == null) continue;

                int color = lastColors[(wx & 15) + (wz & 15) * 16];
                if (color == 0) continue;

                int sx = mapCX + (int) ((wx - viewX) / zoom);
                int sy = mapCY + (int) ((wz - viewZ) / zoom);
                ctx.fill(sx, sy, sx + 1, sy + 1, color);
            }
        }
    }

    // ── subtle grid lines at round coordinates ───────────────────────────

    private void drawGrid(DrawContext ctx) {
        int gridWorld = 64;
        if (zoom >= 4)  gridWorld = 128;
        if (zoom >= 8)  gridWorld = 256;
        if (zoom >= 12) gridWorld = 512;

        // vertical lines (constant world-X)
        int gStartX = roundDown((int)(viewX - (mapCX - mapL) * zoom), gridWorld);
        int gEndX   = (int)(viewX + (mapR - mapCX) * zoom);
        for (int wx = gStartX; wx <= gEndX; wx += gridWorld) {
            int sx = mapCX + (int) ((wx - viewX) / zoom);
            if (sx >= mapL && sx <= mapR)
                ctx.fill(sx, mapT, sx + 1, mapB, COL_GRID);
        }

        // horizontal lines (constant world-Z)
        int gStartZ = roundDown((int)(viewZ - (mapCY - mapT) * zoom), gridWorld);
        int gEndZ   = (int)(viewZ + (mapB - mapCY) * zoom);
        for (int wz = gStartZ; wz <= gEndZ; wz += gridWorld) {
            int sy = mapCY + (int) ((wz - viewZ) / zoom);
            if (sy >= mapT && sy <= mapB)
                ctx.fill(mapL, sy, mapR, sy + 1, COL_GRID);
        }
    }

    private static int roundDown(int value, int step) {
        return Math.floorDiv(value, step) * step;
    }

    // ── overlays: waypoints, pings, players ──────────────────────────────

    private void drawWaypoints(DrawContext ctx) {
        if (!FriendFinderConfig.getInstance().waypointVisible) return;
        String dim = client.world.getRegistryKey().getValue().toString();

        for (Waypoint wp : WaypointManager.getInstance().getWaypoints()) {
            if (!wp.getDimension().equals(dim)) continue;

            int sx = mapCX + (int) ((wp.getX() - viewX) / zoom);
            int sy = mapCY + (int) ((wp.getZ() - viewZ) / zoom);

            // diamond marker
            ctx.fill(sx - 3, sy - 1, sx + 4, sy + 2, 0xFF00FFAA);
            ctx.fill(sx - 1, sy - 3, sx + 2, sy + 4, 0xFF00FFAA);
            ctx.fill(sx - 2, sy, sx + 3, sy + 1, 0xFFFFFFFF);

            String label = wp.getName();
            int tw = textRenderer.getWidth(label);
            ctx.drawText(textRenderer, label, sx - tw / 2, sy - 13, 0xFFFFFFFF, true);
        }
    }

    private void drawPings(DrawContext ctx) {
        long now = System.currentTimeMillis();
        long durMs = FriendFinderConfig.getInstance().pingDurationSeconds * 1000L;

        for (PingManager.Ping ping : PingManager.getInstance().getActivePings()) {
            float alpha = ping.getAlpha(now, durMs);
            if (alpha <= 0) continue;

            int sx = mapCX + (int) ((ping.x() - viewX) / zoom);
            int sy = mapCY + (int) ((ping.z() - viewZ) / zoom);

            int a = (int) (alpha * 255) << 24;
            int rgb = PingManager.getPlayerColor(ping.sender()) & 0x00FFFFFF;
            int col = a | rgb;

            // pulsing ring + filled center
            ctx.fill(sx - 4, sy - 1, sx + 5, sy + 2, col);
            ctx.fill(sx - 1, sy - 4, sx + 2, sy + 5, col);
            ctx.fill(sx - 2, sy - 2, sx + 3, sy + 3, col);

            ctx.drawText(textRenderer, ping.sender(), sx + 6, sy - 4, col, true);
        }
    }

    private void drawPlayers(DrawContext ctx) {
        for (AbstractClientPlayerEntity player : PlayerTracker.getInstance().getNearbyPlayers()) {
            int sx = mapCX + (int) ((player.getX() - viewX) / zoom);
            int sy = mapCY + (int) ((player.getZ() - viewZ) / zoom);

            // green dot with dark outline
            ctx.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xFF1B3D1B);
            ctx.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF55FF55);

            String name = player.getName().getString();
            int tw = textRenderer.getWidth(name);
            ctx.drawText(textRenderer, name, sx - tw / 2, sy - 12, 0xFFFFFFFF, true);
        }
    }

    private void drawPlayerMarker(DrawContext ctx) {
        int px = mapCX + (int) ((client.player.getX() - viewX) / zoom);
        int py = mapCY + (int) ((client.player.getZ() - viewZ) / zoom);

        // direction indicator: rotate a small triangle based on yaw
        float yaw = client.player.getYaw();
        float rad = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        // outer dark ring
        ctx.fill(px - 4, py - 4, px + 5, py + 5, 0xFF000000);
        ctx.fill(px - 3, py - 3, px + 4, py + 4, 0xFFFFFFFF);
        ctx.fill(px - 2, py - 2, px + 3, py + 3, 0xFF4488FF);

        // heading tick: a small 3px line extending from center in yaw direction
        int tx = px + (int) (sin * 5);
        int ty = py - (int) (cos * 5);
        ctx.fill(Math.min(px, tx), Math.min(py, ty),
                 Math.max(px, tx) + 1, Math.max(py, ty) + 1, 0xFFFFFFFF);
    }

    // ── crosshair & compass ──────────────────────────────────────────────

    private void drawCrosshair(DrawContext ctx) {
        ctx.fill(mapCX - 10, mapCY, mapCX + 11, mapCY + 1, COL_CROSSHAIR);
        ctx.fill(mapCX, mapCY - 10, mapCX + 1, mapCY + 11, COL_CROSSHAIR);
    }

    private void drawCardinals(DrawContext ctx) {
        int pad = 8;
        drawLabel(ctx, "N", mapCX, mapT + pad, 0xFFFF5555);
        drawLabel(ctx, "S", mapCX, mapB - pad - textRenderer.fontHeight, 0xFFCCCCCC);
        drawLabel(ctx, "W", mapL + pad, mapCY - textRenderer.fontHeight / 2, 0xFFCCCCCC);

        String e = "E";
        int ew = textRenderer.getWidth(e);
        ctx.drawText(textRenderer, e, mapR - pad - ew,
                mapCY - textRenderer.fontHeight / 2, 0xFFCCCCCC, true);
    }

    private void drawLabel(DrawContext ctx, String s, int x, int y, int color) {
        int tw = textRenderer.getWidth(s);
        ctx.drawText(textRenderer, s, x - tw / 2, y, color, true);
    }

    // ── info overlays ────────────────────────────────────────────────────

    private void drawTitle(DrawContext ctx) {
        String title = "W O R L D   M A P";
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, title,
                width / 2 - tw / 2, MARGIN + 3, COL_INNER_EDGE, false);
    }

    private void drawInfoBar(DrawContext ctx, int mouseX, int mouseY) {
        int barH = 16;
        int barY = mapB - barH;

        // semi-transparent bar at bottom of map
        ctx.fill(mapL, barY, mapR, mapB, 0xBB000000);
        ctx.fill(mapL, barY, mapR, barY + 1, 0x33FFFFFF);

        int ty = barY + 4;

        // left: player position
        String pos = String.format("X: %d  Z: %d",
                (int) client.player.getX(), (int) client.player.getZ());
        ctx.drawText(textRenderer, pos, mapL + 6, ty, COL_TEXT, true);

        // center: cursor world-coordinates (when hovering the map)
        if (inMap(mouseX, mouseY)) {
            int wx = (int) (viewX + (mouseX - mapCX) * zoom);
            int wz = (int) (viewZ + (mouseY - mapCY) * zoom);
            String cur = String.format("Cursor: %d, %d", wx, wz);
            int cw = textRenderer.getWidth(cur);
            ctx.drawText(textRenderer, cur, mapCX - cw / 2, ty, COL_TEXT_DIM, true);
        }

        // right: zoom level
        String zoomStr = "Zoom 1:" + zoom;
        int zw = textRenderer.getWidth(zoomStr);
        ctx.drawText(textRenderer, zoomStr, mapR - zw - 6, ty, COL_TEXT, true);

        // top-right hint (inside map, very subtle)
        String hint = "Scroll: Zoom | Drag: Pan | M: Close";
        int hw = textRenderer.getWidth(hint);
        ctx.drawText(textRenderer, hint, mapR - hw - 4, mapT + 4, 0x55FFFFFF, true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

}
