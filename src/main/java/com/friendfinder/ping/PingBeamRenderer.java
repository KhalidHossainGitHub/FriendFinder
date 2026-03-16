package com.friendfinder.ping;

import com.friendfinder.config.FriendFinderConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class PingBeamRenderer {

    private static final float BEAM_WIDTH = 0.45f;
    private static final float BEAM_HEIGHT = 256.0f;

    public void init() {
        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            long now = System.currentTimeMillis();
            long durMs = FriendFinderConfig.getInstance().pingDurationSeconds * 1000L;

            List<PingManager.Ping> pings = PingManager.getInstance().getActivePings();
            if (pings.isEmpty()) return;

            Camera camera = context.camera();
            MatrixStack matrices = context.matrixStack();
            Vec3d camPos = camera.getPos();

            renderBeams(matrices, camPos, pings, now, durMs);
            renderLabels(matrices, camera, camPos, pings, now, durMs, mc);
        });
    }

    // ── beacon beams ───────────────────────────────────────────────────

    private void renderBeams(MatrixStack matrices, Vec3d camPos,
                             List<PingManager.Ping> pings, long now, long durMs) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();

        for (PingManager.Ping ping : pings) {
            float alpha = ping.getAlpha(now, durMs);
            if (alpha <= 0) continue;

            float[] rgb = PingManager.getPlayerColorF(ping.sender());
            float r = rgb[0], g = rgb[1], b = rgb[2];

            double dx = ping.x() + 0.5 - camPos.x;
            double dy = ping.y() - camPos.y;
            double dz = ping.z() + 0.5 - camPos.z;

            matrices.push();
            matrices.translate(dx, dy, dz);
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float baseA = 0.55f * alpha;
            float topA = 0.0f;
            float midA = 0.25f * alpha;
            float midH = BEAM_HEIGHT * 0.4f;

            BufferBuilder buf = tessellator.begin(
                    VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            // Two crossing quads, each split into lower + upper segment
            // for a nicer gradient that fades toward the sky

            // ── Quad pair 1 (X-aligned) ──
            quad(buf, matrix, -BEAM_WIDTH, 0, 0, BEAM_WIDTH, 0, 0,
                    BEAM_WIDTH, midH, 0, -BEAM_WIDTH, midH, 0,
                    r, g, b, baseA, midA);
            quad(buf, matrix, -BEAM_WIDTH, midH, 0, BEAM_WIDTH, midH, 0,
                    BEAM_WIDTH, BEAM_HEIGHT, 0, -BEAM_WIDTH, BEAM_HEIGHT, 0,
                    r, g, b, midA, topA);

            // ── Quad pair 2 (Z-aligned) ──
            quad(buf, matrix, 0, 0, -BEAM_WIDTH, 0, 0, BEAM_WIDTH,
                    0, midH, BEAM_WIDTH, 0, midH, -BEAM_WIDTH,
                    r, g, b, baseA, midA);
            quad(buf, matrix, 0, midH, -BEAM_WIDTH, 0, midH, BEAM_WIDTH,
                    0, BEAM_HEIGHT, BEAM_WIDTH, 0, BEAM_HEIGHT, -BEAM_WIDTH,
                    r, g, b, midA, topA);

            BufferRenderer.drawWithGlobalProgram(buf.end());
            matrices.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buf, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float r, float g, float b,
                             float bottomA, float topA) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, bottomA);
        buf.vertex(m, x2, y2, z2).color(r, g, b, bottomA);
        buf.vertex(m, x3, y3, z3).color(r, g, b, topA);
        buf.vertex(m, x4, y4, z4).color(r, g, b, topA);
    }

    // ── billboarded sender labels ──────────────────────────────────────

    private void renderLabels(MatrixStack matrices, Camera camera, Vec3d camPos,
                              List<PingManager.Ping> pings, long now, long durMs,
                              MinecraftClient mc) {
        VertexConsumerProvider.Immediate immediate =
                mc.getBufferBuilders().getEntityVertexConsumers();

        for (PingManager.Ping ping : pings) {
            float alpha = ping.getAlpha(now, durMs);
            if (alpha <= 0) continue;

            double dx = ping.x() + 0.5 - camPos.x;
            double dy = ping.y() + 2.0 - camPos.y;
            double dz = ping.z() + 0.5 - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist > 500) continue;

            matrices.push();
            matrices.translate(dx, dy, dz);
            matrices.multiply(camera.getRotation());

            float scale = 0.025f;
            if (dist > 8) scale = (float) (dist * 0.003);
            matrices.scale(-scale, -scale, scale);

            TextRenderer tr = mc.textRenderer;
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            int pingColor = PingManager.getPlayerColor(ping.sender());
            int textColor = (((int) (alpha * 255)) << 24) | (pingColor & 0x00FFFFFF);
            int bgColor = ((int) (alpha * 100)) << 24;

            String label = ping.sender() + " pinged here";
            float halfW = tr.getWidth(label) / 2.0f;

            tr.draw(label, -halfW, 0, textColor, false, matrix, immediate,
                    TextRenderer.TextLayerType.SEE_THROUGH, bgColor, 0xF000F0);

            matrices.pop();
        }

        immediate.draw();
    }
}
