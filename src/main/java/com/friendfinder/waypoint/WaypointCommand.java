package com.friendfinder.waypoint;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;

public class WaypointCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("waypoint")
                .then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(WaypointCommand::executeAdd)))
                .then(ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(WaypointCommand::executeRemove)))
                .then(ClientCommandManager.literal("list")
                    .executes(WaypointCommand::executeList))
                .then(ClientCommandManager.literal("teleport")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(WaypointCommand::executeTeleport)))
            )
        );
    }

    private static int executeAdd(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        FabricClientCommandSource source = context.getSource();
        MinecraftClient client = source.getClient();

        if (client.player == null || client.world == null) return 0;

        int x = (int) client.player.getX();
        int y = (int) client.player.getY();
        int z = (int) client.player.getZ();
        String dimension = client.world.getRegistryKey().getValue().toString();

        WaypointManager.getInstance().addWaypoint(new Waypoint(name, dimension, x, y, z));

        source.sendFeedback(Text.literal(
                String.format("Waypoint '%s' saved at %d, %d, %d [%s]", name, x, y, z, dimension))
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int executeRemove(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        FabricClientCommandSource source = context.getSource();

        if (WaypointManager.getInstance().removeWaypoint(name)) {
            source.sendFeedback(Text.literal("Waypoint '" + name + "' removed.")
                    .formatted(Formatting.YELLOW));
        } else {
            source.sendError(Text.literal("Waypoint '" + name + "' not found."));
        }
        return 1;
    }

    private static int executeList(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        MinecraftClient client = source.getClient();
        List<Waypoint> waypoints = WaypointManager.getInstance().getWaypoints();

        if (waypoints.isEmpty()) {
            source.sendFeedback(Text.literal("No waypoints saved.").formatted(Formatting.GRAY));
            return 1;
        }

        source.sendFeedback(Text.literal("=== Waypoints ===").formatted(Formatting.GOLD));
        for (Waypoint wp : waypoints) {
            double dist = 0;
            if (client.player != null) {
                dist = wp.distanceTo(client.player.getX(), client.player.getY(), client.player.getZ());
            }
            source.sendFeedback(Text.literal(
                    String.format("  %s [%s] (%d, %d, %d) — %.0fm",
                            wp.getName(), wp.getDimension(),
                            wp.getX(), wp.getY(), wp.getZ(), dist))
                    .formatted(Formatting.AQUA));
        }
        return 1;
    }

    private static int executeTeleport(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        FabricClientCommandSource source = context.getSource();
        MinecraftClient client = source.getClient();

        Optional<Waypoint> opt = WaypointManager.getInstance().getWaypoint(name);
        if (opt.isEmpty()) {
            source.sendError(Text.literal("Waypoint '" + name + "' not found."));
            return 0;
        }

        Waypoint wp = opt.get();
        if (client.getNetworkHandler() != null && client.player != null) {
            String cmd = String.format("tp %s %d %d %d",
                    client.player.getName().getString(),
                    wp.getX(), wp.getY(), wp.getZ());
            client.getNetworkHandler().sendCommand(cmd);
            source.sendFeedback(Text.literal("Teleporting to '" + name + "'...")
                    .formatted(Formatting.GREEN));
        }
        return 1;
    }
}
