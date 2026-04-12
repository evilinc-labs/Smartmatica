package dev.moar.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.moar.MoarMod;
import dev.moar.chest.ChestManager;
import dev.moar.stash.StashManager;
import dev.moar.stash.StashOrganizer;
import dev.moar.util.ChatHelper;
/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*//*?} else {*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
/*?}*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.*;
*//*?} else {*/
import net.minecraft.block.*;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
*//*?} else {*/
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// Registers all /stash client commands.
public final class StashCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    private StashCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            /*? if >=26.1 {*//*
            var root = ClientCommands.literal("stash");
            *//*?} else {*/
            var root = ClientCommandManager.literal("stash");
            /*?}*/

            // Root help
            root.executes(ctx -> {
                ChatHelper.labelled("Stash", "§7Available subcommands:");
                ChatHelper.labelled("Stash", "  §f/stash pos1 §7[x y z] §8— set corner 1 (default: player pos)");
                ChatHelper.labelled("Stash", "  §f/stash pos2 §7[x y z] §8— set corner 2");
                ChatHelper.labelled("Stash", "  §f/stash scan §8— scan containers in the defined region");
                ChatHelper.labelled("Stash", "  §f/stash organize §8— auto-sort items into columns");
                ChatHelper.labelled("Stash", "  §f/stash organize stop §8— abort organizing");
                ChatHelper.labelled("Stash", "  §f/stash stop §8— abort scanning");
                ChatHelper.labelled("Stash", "  §f/stash status §8— show index summary");
                ChatHelper.labelled("Stash", "  §f/stash export §8— export CSV report");
                ChatHelper.labelled("Stash", "  §f/stash clear §8— clear the index");
                ChatHelper.labelled("Stash", "  §f/stash dump add §7[x y z] §8— mark dump chest for mined items");
                ChatHelper.labelled("Stash", "  §f/stash dump remove §8— unmark nearest dump chest");
                ChatHelper.labelled("Stash", "  §f/stash dump list §8— show all dump chests");
                ChatHelper.labelled("Stash", "  §f/stash dump clear §8— clear all dump chests");
                ChatHelper.labelled("Stash", "§7Scans chests, barrels, shulker boxes, and hoppers.");
                ChatHelper.labelled("Stash", "§7Uses incremental waypoints for regions beyond render distance.");
                return 1;
            });

            // /stash pos1 [x y z]
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("pos1")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("pos1")
            /*?}*/
                    .executes(ctx -> {
                        /*? if >=26.1 {*//*
                        Minecraft mc = Minecraft.getInstance();
                        *//*?} else {*/
                        MinecraftClient mc = MinecraftClient.getInstance();
                        /*?}*/
                        if (mc.player == null) return 0;
                        /*? if >=26.1 {*//*
                        BlockPos pos = mc.player.blockPosition();
                        *//*?} else {*/
                        BlockPos pos = mc.player.getBlockPos();
                        /*?}*/
                        getManager().setCorner1(pos);
                        ChatHelper.labelled("Stash", "§aCorner 1 set to §f"
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        return 1;
                    })
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                    *//*?} else {*/
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                    /*?}*/
                            /*? if >=26.1 {*//*
                            .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                            *//*?} else {*/
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                            /*?}*/
                                    /*? if >=26.1 {*//*
                                    .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                    *//*?} else {*/
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    /*?}*/
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
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("pos2")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("pos2")
            /*?}*/
                    .executes(ctx -> {
                        /*? if >=26.1 {*//*
                        Minecraft mc = Minecraft.getInstance();
                        *//*?} else {*/
                        MinecraftClient mc = MinecraftClient.getInstance();
                        /*?}*/
                        if (mc.player == null) return 0;
                        /*? if >=26.1 {*//*
                        BlockPos pos = mc.player.blockPosition();
                        *//*?} else {*/
                        BlockPos pos = mc.player.getBlockPos();
                        /*?}*/
                        getManager().setCorner2(pos);
                        ChatHelper.labelled("Stash", "§aCorner 2 set to §f"
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        return 1;
                    })
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                    *//*?} else {*/
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                    /*?}*/
                            /*? if >=26.1 {*//*
                            .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                            *//*?} else {*/
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                            /*?}*/
                                    /*? if >=26.1 {*//*
                                    .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                    *//*?} else {*/
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    /*?}*/
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
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("scan")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("scan")
            /*?}*/
                    .executes(ctx -> {
                        getManager().start();
                        return 1;
                    })
            );

            // /stash organize [stop]
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("organize")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("organize")
            /*?}*/
                    .executes(ctx -> {
                        getManager().getOrganizer().start();
                        return 1;
                    })
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.literal("stop")
                    *//*?} else {*/
                    .then(ClientCommandManager.literal("stop")
                    /*?}*/
                            .executes(ctx -> {
                                getManager().getOrganizer().stop();
                                return 1;
                            })
                    )
            );

            // /stash stop
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("stop")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("stop")
            /*?}*/
                    .executes(ctx -> {
                        getManager().stop();
                        return 1;
                    })
            );

            // /stash status
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("status")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("status")
            /*?}*/
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
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("export")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("export")
            /*?}*/
                    .executes(ctx -> {
                        var path = getManager().exportCsv();
                        if (path != null) {
                            ChatHelper.labelled("Stash", "§7File: §f" + path.toAbsolutePath());
                        }
                        return 1;
                    })
            );

            // /stash clear
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("clear")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("clear")
            /*?}*/
                    .executes(ctx -> {
                        getManager().clearIndex();
                        return 1;
                    })
            );

            // /stash dump (add, remove, list, clear)
            root.then(buildDumpSubcommand());

            dispatcher.register(root);
            LOGGER.info("StashCommand: /stash registered");
        });
    }

    /** Build the /stash dump sub-tree (add, remove, list, clear). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> buildDumpSubcommand() {
        /*? if >=26.1 {*//*
        var dump = ClientCommands.literal("dump");
        *//*?} else {*/
        var dump = ClientCommandManager.literal("dump");
        /*?}*/

        // /stash dump  (help)
        dump.executes(ctx -> {
            ChatHelper.labelled("Stash", "§7Dump chests store mined items during area clearing.");
            ChatHelper.labelled("Stash", "  §f/stash dump add §7[x y z] §8— mark a dump chest");
            ChatHelper.labelled("Stash", "  §f/stash dump remove §8— unmark nearest dump chest");
            ChatHelper.labelled("Stash", "  §f/stash dump list §8— show all dump chests");
            ChatHelper.labelled("Stash", "  §f/stash dump clear §8— clear all dump chests");
            return 1;
        });

        // /stash dump add [x y z]
        /*? if >=26.1 {*//*
        dump.then(ClientCommands.literal("add")
        *//*?} else {*/
        dump.then(ClientCommandManager.literal("add")
        /*?}*/
                .executes(ctx -> {
                    /*? if >=26.1 {*//*
                    Minecraft mc = Minecraft.getInstance();
                    *//*?} else {*/
                    MinecraftClient mc = MinecraftClient.getInstance();
                    /*?}*/
                    if (mc.player == null) return 0;
                    BlockPos pos = findTargetContainer(mc);
                    if (pos == null) {
                        ChatHelper.labelled("Stash",
                                "§cNo chest, barrel, or shulker box found. Look at one or stand next to it.");
                        return 0;
                    }
                    if (MoarMod.getChestManager().addDumpChest(pos)) {
                        ChatHelper.labelled("Stash", "§aMarked dump chest at §e"
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                    } else {
                        ChatHelper.labelled("Stash", "§eThat position is already marked.");
                    }
                    return 1;
                })
                /*? if >=26.1 {*//*
                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                *//*?} else {*/
                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                /*?}*/
                        /*? if >=26.1 {*//*
                        .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                        *//*?} else {*/
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                        /*?}*/
                                /*? if >=26.1 {*//*
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                *//*?} else {*/
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                /*?}*/
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            BlockPos pos = new BlockPos(x, y, z);
                                            if (MoarMod.getChestManager().addDumpChest(pos)) {
                                                ChatHelper.labelled("Stash",
                                                        "§aMarked dump chest at §e" + x + " " + y + " " + z);
                                            } else {
                                                ChatHelper.labelled("Stash",
                                                        "§eThat position is already marked.");
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
        );

        // /stash dump remove
        /*? if >=26.1 {*//*
        dump.then(ClientCommands.literal("remove")
        *//*?} else {*/
        dump.then(ClientCommandManager.literal("remove")
        /*?}*/
                .executes(ctx -> {
                    /*? if >=26.1 {*//*
                    Minecraft mc = Minecraft.getInstance();
                    *//*?} else {*/
                    MinecraftClient mc = MinecraftClient.getInstance();
                    /*?}*/
                    if (mc.player == null) return 0;
                    BlockPos pos = findTargetContainer(mc);
                    if (pos == null) {
                        ChatHelper.labelled("Stash",
                                "§cNo dump chest found nearby to remove.");
                        return 0;
                    }
                    if (MoarMod.getChestManager().removeDumpChest(pos)) {
                        ChatHelper.labelled("Stash", "§aRemoved dump chest at §e"
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                    } else {
                        ChatHelper.labelled("Stash",
                                "§cThat container is not marked as a dump chest.");
                    }
                    return 1;
                })
        );

        // /stash dump list
        /*? if >=26.1 {*//*
        dump.then(ClientCommands.literal("list")
        *//*?} else {*/
        dump.then(ClientCommandManager.literal("list")
        /*?}*/
                .executes(ctx -> {
                    List<BlockPos> chests = MoarMod.getChestManager().getDumpPositions();
                    if (chests.isEmpty()) {
                        ChatHelper.labelled("Stash", "No dump chests designated.");
                        ChatHelper.labelled("Stash",
                                "§7Use §f/stash dump add §7while standing at a chest.");
                    } else {
                        ChatHelper.labelled("Stash", "§lDump chests (" + chests.size() + "):");
                        for (BlockPos pos : chests) {
                            ChatHelper.labelled("Stash",
                                    " §7- §e" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        }
                    }
                    return 1;
                })
        );

        // /stash dump clear
        /*? if >=26.1 {*//*
        dump.then(ClientCommands.literal("clear")
        *//*?} else {*/
        dump.then(ClientCommandManager.literal("clear")
        /*?}*/
                .executes(ctx -> {
                    MoarMod.getChestManager().clearDumpChests();
                    ChatHelper.labelled("Stash", "§aAll dump chest designations cleared.");
                    return 1;
                })
        );

        return dump;
    }

    /** Find the container block the player is targeting (crosshair > feet > below). */
    /*? if >=26.1 {*//*
    private static BlockPos findTargetContainer(Minecraft mc) {
    *//*?} else {*/
    private static BlockPos findTargetContainer(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return null;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return null;
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.hitResult instanceof BlockHitResult bhr
                && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos lookPos = bhr.getBlockPos();
            if (isContainer(mc.level.getBlockState(lookPos).getBlock())) return lookPos;
        }
        BlockPos feet = mc.player.blockPosition();
        if (isContainer(mc.level.getBlockState(feet).getBlock())) return feet;
        BlockPos below = feet.below();
        if (isContainer(mc.level.getBlockState(below).getBlock())) return below;
        *//*?} else {*/
        if (mc.crosshairTarget instanceof BlockHitResult bhr
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockPos lookPos = bhr.getBlockPos();
            if (isContainer(mc.world.getBlockState(lookPos).getBlock())) return lookPos;
        }
        BlockPos feet = mc.player.getBlockPos();
        if (isContainer(mc.world.getBlockState(feet).getBlock())) return feet;
        BlockPos below = feet.down();
        if (isContainer(mc.world.getBlockState(below).getBlock())) return below;
        /*?}*/
        return null;
    }

    private static boolean isContainer(Block block) {
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock;
    }

    private static StashManager getManager() {
        return MoarMod.getStashManager();
    }
}
