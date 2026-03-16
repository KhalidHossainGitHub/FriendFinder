package com.friendfinder.teleport;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TeleportMenuScreen extends Screen {

    private static final int ENTRY_HEIGHT = 30;
    private static final int LIST_WIDTH = 260;
    private static final int HEADER_HEIGHT = 45;
    private static final int FOOTER_MARGIN = 35;

    private final List<PlayerListEntry> players = new ArrayList<>();
    private int scrollOffset = 0;

    public TeleportMenuScreen() {
        super(Text.literal("Teleport to Player"));
    }

    @Override
    protected void init() {
        super.init();
        players.clear();

        if (client != null && client.getNetworkHandler() != null && client.player != null) {
            client.getNetworkHandler().getPlayerList().stream()
                    .filter(e -> !e.getProfile().getId().equals(client.player.getUuid()))
                    .sorted(Comparator.comparing(e -> e.getProfile().getName()))
                    .forEach(players::add);
        }

        scrollOffset = 0;
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearChildren();

        int listX = (width - LIST_WIDTH) / 2;
        int maxVisible = (height - HEADER_HEIGHT - FOOTER_MARGIN) / ENTRY_HEIGHT;

        for (int i = 0; i < maxVisible && scrollOffset + i < players.size(); i++) {
            PlayerListEntry player = players.get(scrollOffset + i);
            int y = HEADER_HEIGHT + i * ENTRY_HEIGHT;

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Teleport"),
                    btn -> {
                        if (client != null && client.getNetworkHandler() != null) {
                            client.getNetworkHandler().sendCommand(
                                    "tp " + player.getProfile().getName());
                        }
                        close();
                    }
            ).dimensions(listX + LIST_WIDTH - 68, y + 4, 62, 20).build());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        int maxVisible = (height - HEADER_HEIGHT - FOOTER_MARGIN) / ENTRY_HEIGHT;
        int maxScroll = Math.max(0, players.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount, maxScroll));
        rebuildButtons();
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int listX = (width - LIST_WIDTH) / 2;
        int maxVisible = (height - HEADER_HEIGHT - FOOTER_MARGIN) / ENTRY_HEIGHT;

        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);

        // Player count
        String countText = players.size() + " player" + (players.size() != 1 ? "s" : "") + " online";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(countText),
                width / 2, 32, 0xAAAAAA);

        // Entry backgrounds, heads, and names
        for (int i = 0; i < maxVisible && scrollOffset + i < players.size(); i++) {
            PlayerListEntry player = players.get(scrollOffset + i);
            int y = HEADER_HEIGHT + i * ENTRY_HEIGHT;

            // Entry background
            boolean hovered = mouseX >= listX && mouseX <= listX + LIST_WIDTH
                    && mouseY >= y && mouseY < y + ENTRY_HEIGHT - 2;
            int bgColor = hovered ? 0x60444444 : 0x40000000;
            context.fill(listX, y, listX + LIST_WIDTH, y + ENTRY_HEIGHT - 2, bgColor);

            // Player head
            drawPlayerHead(context, player, listX + 6, y + 5);

            // Player name
            context.drawText(textRenderer, player.getProfile().getName(),
                    listX + 28, y + 9, 0xFFFFFF, true);
        }

        // Scroll indicator
        if (players.size() > maxVisible) {
            String scrollText = (scrollOffset + 1) + "-"
                    + Math.min(scrollOffset + maxVisible, players.size())
                    + " of " + players.size();
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(scrollText),
                    width / 2, height - 22, 0x888888);
        }

        // Render buttons on top of entry backgrounds
        for (var child : children()) {
            if (child instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void drawPlayerHead(DrawContext context, PlayerListEntry player, int x, int y) {
        try {
            Identifier skin = player.getSkinTextures().texture();
            // Face layer (8x8 at UV 8,8 in 64x64 skin)
            context.drawTexture(skin, x, y, 18, 18, 8.0f, 8.0f, 8, 8, 64, 64);
            // Hat overlay layer (8x8 at UV 40,8 in 64x64 skin)
            context.drawTexture(skin, x, y, 18, 18, 40.0f, 8.0f, 8, 8, 64, 64);
        } catch (Exception e) {
            // Fallback: draw a colored square if skin unavailable
            context.fill(x, y, x + 18, y + 18, 0xFF888888);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
