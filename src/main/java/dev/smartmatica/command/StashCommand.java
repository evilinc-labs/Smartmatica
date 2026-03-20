package dev.smartmatica.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.smartmatica.SmartmaticaMod;
import dev.smartmatica.stash.StashManager;
import dev.smartmatica.util.ChatHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers all /stash client commands.
 *
 * /stash                  -- show help
 * /stash pos1 [x y z]    -- set corner 1 of stash region
 * /stash pos2 [x y z]    -- set corner 2 of stash region
 * /stash scan             -- scan all containers in the defined region
 * /stash stop             -- abort scanning
 * /stash status           -- show index summary
 * /stash export           -- export CSV report
 * /stash clear            -- clear the index
 */
public final class StashCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("Smartmatica");

    private StashCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("stash");

            // Root help
            root.executes(ctx -> {
                ChatHelper.labelled("Stash", "§7Available subcommands:");
                ChatHelper.labelled("Stash", "  §f/stash pos1 §7[x y z] §8— set corner 1 (default: player pos)");
                ChatHelper.labelled("Stash", "  §f/stash pos2 §7[x y z] §8— set corner 2");
                ChatHelper.labelled("Stash", "  §f/stash scan §8— scan containers in the defined region");
                ChatHelper.labelled("Stash", "  §f/stash stop §8— abort scanning");
                ChatHelper.labelled("Stash", "  §f/stash status §8— show index summary");
                ChatHelper.labelled("Stash", "  §f/stash export §8— export CSV report");
                ChatHelper.labelled("Stash", "  §f/stash clear §8— clear the index");
                ChatHelper.labelled("Stash", "§7Scans chests, barrels, shulker boxes, and hoppers.");
                ChatHelper.labelled("Stash", "§7Uses incremental waypoints for regions beyond render distance.");
                return 1;
            });

            // /stash pos1 [x y z]
            root.then(ClientCommandManager.literal("pos1")
                    .executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null) return 0;
                        BlockPos pos = mc.player.getBlockPos();
                        getManager().setCorner1(pos);
                        ChatHelper.labelled("Stash", "§aCorner 1 set to §f"
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        return 1;
                    })
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> {
                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                BlockPos pos = new BlockPos(x, y, z);
                                                getManager().setCorner1(pos);
                                                ChatHelper.labelled("Stash",
                                                        "§aCorner 1 set to §f" + x + " " + y + " " + z);
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /stash pos2 [x y z]
            root.then(ClientCommandManager.literal("pos2")
                    .executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null) return 0;
                        BlockPos pos = mc.player.getBlockPos();
                        getManager().setCorner2(pos);
                        ChatHelper.labelled("Stash", "§aCorner 2 set to §f"
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        return 1;
                    })
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> {
                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                BlockPos pos = new BlockPos(x, y, z);
                                                getManager().setCorner2(pos);
                                                ChatHelper.labelled("Stash",
                                                        "§aCorner 2 set to §f" + x + " " + y + " " + z);
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /stash scan
            root.then(ClientCommandManager.literal("scan")
                    .executes(ctx -> {
                        getManager().start();
                        return 1;
                    })
            );

            // /stash stop
            root.then(ClientCommandManager.literal("stop")
                    .executes(ctx -> {
                        getManager().stop();
                        return 1;
                    })
            );

            // /stash status
            root.then(ClientCommandManager.literal("status")
                    .executes(ctx -> {
                        StashManager mgr = getManager();
                        ChatHelper.labelled("Stash", "§7State: §f" + mgr.getStatus());
                        ChatHelper.labelled("Stash", "§7Region: §f" + mgr.getRegionInfo());
                        if (mgr.getIndexedCount() > 0) {
                            ChatHelper.labelled("Stash", "§7Index: §f" + mgr.getDetailedSummary());
                        }
                        return 1;
                    })
            );

            // /stash export
            root.then(ClientCommandManager.literal("export")
                    .executes(ctx -> {
                        var path = getManager().exportCsv();
                        if (path != null) {
                            ChatHelper.labelled("Stash", "§7File: §f" + path.toAbsolutePath());
                        }
                        return 1;
                    })
            );

            // /stash clear
            root.then(ClientCommandManager.literal("clear")
                    .executes(ctx -> {
                        getManager().clearIndex();
                        return 1;
                    })
            );

            dispatcher.register(root);
            LOGGER.info("StashCommand: /stash registered");
        });
    }

    private static StashManager getManager() {
        return SmartmaticaMod.getStashManager();
    }
}
