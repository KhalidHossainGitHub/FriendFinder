package com.friendfinder.menu;

import com.friendfinder.waypoint.Waypoint;
import com.friendfinder.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FriendFinderMenuScreen extends Screen {

    private enum Tab { WAYPOINTS, FRIENDS }

    private static final int PANEL_W = 320;
    private static final int TAB_W = 110;
    private static final int TAB_H = 22;
    private static final int WP_ENTRY_H = 34;
    private static final int FR_ENTRY_H = 30;

    private Tab activeTab = Tab.WAYPOINTS;

    private TextFieldWidget nameField;
    private int wpScroll;

    private final List<PlayerListEntry> players = new ArrayList<>();
    private int frScroll;

    private int panelL, panelR, contentTop, listBottom;

    public FriendFinderMenuScreen() {
        super(Text.literal("FriendFinder"));
    }

    @Override
    protected void init() {
        super.init();

        panelL = (width - PANEL_W) / 2;
        panelR = panelL + PANEL_W;
        contentTop = 50;
        listBottom = height - 26;

        players.clear();
        if (client != null && client.getNetworkHandler() != null && client.player != null) {
            client.getNetworkHandler().getPlayerList().stream()
                    .filter(e -> !e.getProfile().getId().equals(client.player.getUuid()))
                    .sorted(Comparator.comparing(e -> e.getProfile().getName()))
                    .forEach(players::add);
        }

        rebuild();
    }

    private void rebuild() {
        clearChildren();

        if (activeTab == Tab.WAYPOINTS) {
            int fieldW = PANEL_W - 106;
            nameField = new TextFieldWidget(textRenderer,
                    panelL + 8, contentTop + 6, fieldW, 16, Text.literal("Name"));
            nameField.setMaxLength(32);
            nameField.setPlaceholder(Text.literal("Waypoint name...").formatted(Formatting.DARK_GRAY));
            addDrawableChild(nameField);

            addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Here"),
                    btn -> addWaypointHere())
                    .dimensions(panelR - 94, contentTop + 3, 88, 20).build());

            int listTop = contentTop + 32;
            List<Waypoint> wps = WaypointManager.getInstance().getWaypoints();
            int maxVis = (listBottom - listTop) / WP_ENTRY_H;

            for (int i = 0; i < maxVis && wpScroll + i < wps.size(); i++) {
                Waypoint wp = wps.get(wpScroll + i);
                int ey = listTop + i * WP_ENTRY_H;

                addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"),
                        btn -> teleportToWaypoint(wp))
                        .dimensions(panelR - 114, ey + 6, 56, 20).build());

                addDrawableChild(ButtonWidget.builder(Text.literal("Delete"),
                        btn -> {
                            WaypointManager.getInstance().removeWaypoint(wp.getName());
                            int newMax = Math.max(0, WaypointManager.getInstance().getWaypoints().size()
                                    - (listBottom - (contentTop + 32)) / WP_ENTRY_H);
                            wpScroll = Math.min(wpScroll, newMax);
                            rebuild();
                        })
                        .dimensions(panelR - 54, ey + 6, 48, 20).build());
            }
        } else {
            nameField = null;
            int listTop = contentTop + 6;
            int maxVis = (listBottom - listTop) / FR_ENTRY_H;

            for (int i = 0; i < maxVis && frScroll + i < players.size(); i++) {
                PlayerListEntry player = players.get(frScroll + i);
                int ey = listTop + i * FR_ENTRY_H;

                addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"),
                        btn -> {
                            if (client != null && client.getNetworkHandler() != null)
                                client.getNetworkHandler().sendCommand(
                                        "tp " + player.getProfile().getName());
                            close();
                        })
                        .dimensions(panelR - 74, ey + 4, 68, 20).build());
            }
        }
    }

    // ── input ────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null && nameField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nameField.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                addWaypointHere();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_P) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int tabY = 26;
            int tabsX = (width - TAB_W * 2 - 6) / 2;
            if (my >= tabY && my <= tabY + TAB_H) {
                if (mx >= tabsX && mx <= tabsX + TAB_W && activeTab != Tab.WAYPOINTS) {
                    activeTab = Tab.WAYPOINTS;
                    rebuild();
                    return true;
                }
                if (mx >= tabsX + TAB_W + 6 && mx <= tabsX + TAB_W * 2 + 6
                        && activeTab != Tab.FRIENDS) {
                    activeTab = Tab.FRIENDS;
                    rebuild();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (activeTab == Tab.WAYPOINTS) {
            int listTop = contentTop + 32;
            int maxVis = (listBottom - listTop) / WP_ENTRY_H;
            int total = WaypointManager.getInstance().getWaypoints().size();
            wpScroll = clamp(wpScroll - (int) vAmt, 0, Math.max(0, total - maxVis));
            rebuild();
        } else {
            int listTop = contentTop + 6;
            int maxVis = (listBottom - listTop) / FR_ENTRY_H;
            frScroll = clamp(frScroll - (int) vAmt, 0, Math.max(0, players.size() - maxVis));
            rebuild();
        }
        return true;
    }

    // ── rendering ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        if (client == null || client.player == null) return;

        drawPanel(ctx);
        drawTabs(ctx, mouseX, mouseY);

        ctx.enableScissor(panelL, contentTop, panelR, listBottom);
        if (activeTab == Tab.WAYPOINTS) drawWaypointContent(ctx, mouseX, mouseY);
        else drawFriendContent(ctx, mouseX, mouseY);
        ctx.disableScissor();

        for (var child : children()) {
            if (child instanceof Drawable d) d.render(ctx, mouseX, mouseY, delta);
        }

        drawStatusBar(ctx);
    }

    private void drawPanel(DrawContext ctx) {
        int pad = 6;
        int pL = panelL - pad, pR = panelR + pad;
        int pT = 8, pB = height - 8;

        ctx.fill(pL + 4, pT + 4, pR + 4, pB + 4, 0x44000000);
        ctx.fill(pL, pT, pR, pB, 0xF0101020);

        ctx.fill(pL, pT, pR, pT + 1, 0xFF2A2A55);
        ctx.fill(pL, pB - 1, pR, pB, 0xFF2A2A55);
        ctx.fill(pL, pT, pL + 1, pB, 0xFF2A2A55);
        ctx.fill(pR - 1, pT, pR, pB, 0xFF2A2A55);

        String title = "F R I E N D   F I N D E R";
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, title, width / 2 - tw / 2, 13, 0xFF7799DD, true);
    }

    private void drawTabs(DrawContext ctx, int mx, int my) {
        int y = 26;
        int x = (width - TAB_W * 2 - 6) / 2;
        drawTab(ctx, x, y, "Waypoints", activeTab == Tab.WAYPOINTS, mx, my);
        drawTab(ctx, x + TAB_W + 6, y, "Friends", activeTab == Tab.FRIENDS, mx, my);
    }

    private void drawTab(DrawContext ctx, int x, int y,
                         String label, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx <= x + TAB_W && my >= y && my <= y + TAB_H;
        int bg = active ? 0xFF1E1E38 : (hover ? 0xFF181830 : 0xFF121224);
        int accent = active ? 0xFF5566CC : 0xFF333355;

        ctx.fill(x, y, x + TAB_W, y + TAB_H, bg);
        ctx.fill(x, y, x + TAB_W, y + (active ? 2 : 1), accent);
        if (!active) ctx.fill(x, y + TAB_H - 1, x + TAB_W, y + TAB_H, 0xFF2A2A55);

        int tw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label,
                x + (TAB_W - tw) / 2, y + (TAB_H - 8) / 2,
                active ? 0xFFFFFFFF : 0xFF777788, true);
    }

    // ── waypoints tab content ────────────────────────────────────────────

    private void drawWaypointContent(DrawContext ctx, int mx, int my) {
        ctx.fill(panelL + 4, contentTop + 28, panelR - 4, contentTop + 29, 0xFF2A2A44);

        int listTop = contentTop + 32;
        List<Waypoint> wps = WaypointManager.getInstance().getWaypoints();
        int maxVis = (listBottom - listTop) / WP_ENTRY_H;

        if (wps.isEmpty()) {
            drawCentered(ctx, "No waypoints yet", listTop + 20, 0xFF555577);
            drawCentered(ctx, "Type a name and click \"+ Add Here\"", listTop + 34, 0xFF444466);
            return;
        }

        for (int i = 0; i < maxVis && wpScroll + i < wps.size(); i++) {
            Waypoint wp = wps.get(wpScroll + i);
            int ey = listTop + i * WP_ENTRY_H;

            boolean hover = mx >= panelL && mx <= panelR && my >= ey && my < ey + WP_ENTRY_H - 2;
            ctx.fill(panelL + 2, ey, panelR - 2, ey + WP_ENTRY_H - 2,
                    hover ? 0x28FFFFFF : (i % 2 == 0 ? 0x12FFFFFF : 0x08FFFFFF));

            // diamond marker
            ctx.fill(panelL + 10, ey + 6, panelL + 14, ey + 10, 0xFF00FFAA);
            ctx.fill(panelL + 11, ey + 5, panelL + 13, ey + 11, 0xFF00FFAA);

            ctx.drawText(textRenderer, wp.getName(), panelL + 20, ey + 4, 0xFFFFFFFF, true);

            String coords = String.format("%d, %d, %d  %s",
                    wp.getX(), wp.getY(), wp.getZ(), shortDim(wp.getDimension()));
            ctx.drawText(textRenderer, coords, panelL + 20, ey + 16, 0xFF6688AA, false);
        }
    }

    // ── friends tab content ──────────────────────────────────────────────

    private void drawFriendContent(DrawContext ctx, int mx, int my) {
        int listTop = contentTop + 6;
        int maxVis = (listBottom - listTop) / FR_ENTRY_H;

        if (players.isEmpty()) {
            drawCentered(ctx, "No other players online", listTop + 20, 0xFF555577);
            return;
        }

        for (int i = 0; i < maxVis && frScroll + i < players.size(); i++) {
            PlayerListEntry player = players.get(frScroll + i);
            int ey = listTop + i * FR_ENTRY_H;

            boolean hover = mx >= panelL && mx <= panelR && my >= ey && my < ey + FR_ENTRY_H - 2;
            ctx.fill(panelL + 2, ey, panelR - 2, ey + FR_ENTRY_H - 2,
                    hover ? 0x28FFFFFF : (i % 2 == 0 ? 0x12FFFFFF : 0x08FFFFFF));

            drawPlayerHead(ctx, player, panelL + 8, ey + 5);
            ctx.drawText(textRenderer, player.getProfile().getName(),
                    panelL + 30, ey + 9, 0xFFFFFFFF, true);
        }
    }

    // ── status bar ───────────────────────────────────────────────────────

    private void drawStatusBar(DrawContext ctx) {
        if (activeTab == Tab.WAYPOINTS) {
            int total = WaypointManager.getInstance().getWaypoints().size();
            if (total > 0) {
                int listTop = contentTop + 32;
                int maxVis = (listBottom - listTop) / WP_ENTRY_H;
                int showing = Math.min(maxVis, total - wpScroll);
                String status = (wpScroll + 1) + "–" + (wpScroll + showing) + " of " + total;
                drawCentered(ctx, status, listBottom + 2, 0xFF555577);
            }
        } else {
            String count = players.size() + " player"
                    + (players.size() != 1 ? "s" : "") + " online";
            drawCentered(ctx, count, listBottom + 2, 0xFF555577);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void drawCentered(DrawContext ctx, String text, int y, int color) {
        int tw = textRenderer.getWidth(text);
        ctx.drawText(textRenderer, text, width / 2 - tw / 2, y, color, false);
    }

    private void drawPlayerHead(DrawContext ctx, PlayerListEntry player, int x, int y) {
        try {
            Identifier skin = player.getSkinTextures().texture();
            ctx.drawTexture(skin, x, y, 18, 18, 8.0f, 8.0f, 8, 8, 64, 64);
            ctx.drawTexture(skin, x, y, 18, 18, 40.0f, 8.0f, 8, 8, 64, 64);
        } catch (Exception e) {
            ctx.fill(x, y, x + 18, y + 18, 0xFF888888);
        }
    }

    private void addWaypointHere() {
        if (nameField == null || client == null || client.player == null) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;

        String dim = client.world.getRegistryKey().getValue().toString();
        int x = (int) client.player.getX();
        int y = (int) client.player.getY();
        int z = (int) client.player.getZ();

        WaypointManager.getInstance().addWaypoint(new Waypoint(name, dim, x, y, z));
        nameField.setText("");

        client.player.sendMessage(
                Text.literal("Waypoint '" + name + "' set at " + x + ", " + y + ", " + z)
                        .formatted(Formatting.GREEN), false);
        rebuild();
    }

    private void teleportToWaypoint(Waypoint wp) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) return;
        client.getNetworkHandler().sendCommand(
                "tp " + client.player.getName().getString()
                        + " " + wp.getX() + " " + wp.getY() + " " + wp.getZ());
        close();
    }

    private static String shortDim(String dim) {
        if (dim == null) return "?";
        String name = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        return switch (name) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> name;
        };
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
