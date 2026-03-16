package com.friendfinder.waypoint;

import com.friendfinder.config.FriendFinderConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class WaypointRenderer {

    private static final float BASE_SCALE = 0.025f;
    private static final double MAX_RENDER_DIST = 500.0;

    public void init() {
        WorldRenderEvents.LAST.register(context -> {
            if (!FriendFinderConfig.getInstance().waypointVisible) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            Camera camera = context.camera();
            MatrixStack matrices = context.matrixStack();
            Vec3d camPos = camera.getPos();
            String dim = mc.world.getRegistryKey().getValue().toString();

            VertexConsumerProvider.Immediate immediate =
                    mc.getBufferBuilders().getEntityVertexConsumers();

            for (Waypoint wp : WaypointManager.getInstance().getWaypoints()) {
                if (!wp.getDimension().equals(dim)) continue;
                renderLabel(matrices, immediate, camera, camPos, wp, mc);
            }

            immediate.draw();
        });
    }

    private void renderLabel(MatrixStack matrices, VertexConsumerProvider vcp,
                             Camera camera, Vec3d camPos,
                             Waypoint wp, MinecraftClient mc) {
        double dx = wp.getX() + 0.5 - camPos.x;
        double dy = wp.getY() + 1.5 - camPos.y;
        double dz = wp.getZ() + 0.5 - camPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > MAX_RENDER_DIST) return;

        matrices.push();
        matrices.translate(dx, dy, dz);
        matrices.multiply(camera.getRotation());

        float scale = BASE_SCALE;
        if (distance > 8) {
            scale = (float) (distance * 0.003);
        }
        matrices.scale(-scale, -scale, scale);

        TextRenderer tr = mc.textRenderer;
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        String name = wp.getName();
        String dist = "[" + (int) distance + "m]";

        float nameHalf = tr.getWidth(name) / 2.0f;
        float distHalf = tr.getWidth(dist) / 2.0f;

        tr.draw(name, -nameHalf, -tr.fontHeight, 0xFF00FFAA, false, matrix, vcp,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);

        tr.draw(dist, -distHalf, 1, 0xFFAAAAAA, false, matrix, vcp,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);

        matrices.pop();
    }
}
