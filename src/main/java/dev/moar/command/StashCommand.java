package dev.moar.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.moar.MoarMod;
import dev.moar.chest.ChestManager;
import dev.moar.stash.StashDatabase;
import dev.moar.stash.StashDatabase.SearchResult;
import dev.moar.stash.StashManager;
import dev.moar.stash.StashManager.ContainerEntry;
import dev.moar.stash.StashOrganizer;
import dev.moar.stash.StashRetriever;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
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
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.item.ItemStack;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
*//*?} else {*/
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
/*?}*/
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Registers all /stash client commands.
public final class StashCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    private StashCommand() {}

    /** Suggests all Minecraft item IDs (path only, no namespace). */
    private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_ITEMS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        /*? if >=26.1 {*//*
        for (Item item : BuiltInRegistries.ITEM) {
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        *//*?} else {*/
        for (Item item : Registries.ITEM) {
            String path = Registries.ITEM.getId(item).getPath();
        /*?}*/
            if (path.startsWith(remaining)) {
                builder.suggest(path);
            }
        }
        return builder.buildFuture();
    };

    /** Suggests saved kit names from the stash database. */
    private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_KITS = (ctx, builder) -> {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) return builder.buildFuture();
        String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (String name : db.listKits()) {
            if (name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    /** Suggests saved region profile names from the stash database. */
    private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_REGIONS = (ctx, builder) -> {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) return builder.buildFuture();
        String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (String name : db.listRegions()) {
            if (name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

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
                ChatHelper.labelled("Stash", "  §f/stash kit §8— kit management (create, snapshot, load, etc.)");
                ChatHelper.labelled("Stash", "  §f/stash region §8— save/load named region profiles");
                ChatHelper.labelled("Stash", "  §f/stash search <item> §8— find items in scanned containers");
                ChatHelper.labelled("Stash", "  §f/stash get <item> §7[count] §8— retrieve items from stash");
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
                        StashRetriever retriever = getManager().getRetriever();
                        if (retriever.isActive()) retriever.stop();
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

            // /stash kit (create, snapshot, load, add, remove, show, list, delete)
            root.then(buildKitSubcommand());

            // /stash region (save, load, list, delete)
            root.then(buildRegionSubcommand());

            // /stash search <item>
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("search")
                    .then(ClientCommands.argument("item", StringArgumentType.word())
                            .suggests(SUGGEST_ITEMS)
            *//*?} else {*/
            root.then(ClientCommandManager.literal("search")
                    .then(ClientCommandManager.argument("item", StringArgumentType.word())
                            .suggests(SUGGEST_ITEMS)
            /*?}*/
                            .executes(ctx -> {
                                String fragment = StringArgumentType.getString(ctx, "item");
                                StashDatabase db = MoarMod.getDatabase();
                                if (db == null) {
                                    ChatHelper.labelled("Stash", "§cDatabase not available.");
                                    return 0;
                                }
                                List<SearchResult> results = db.searchItem(fragment);
                                if (results.isEmpty()) {
                                    ChatHelper.labelled("Stash", "§cNo results for '§f" + fragment + "§c'.");
                                    return 0;
                                }
                                int totalItems = results.stream()
                                        .mapToInt(SearchResult::totalQuantity).sum();
                                ChatHelper.labelled("Stash", "§lSearch '§e" + fragment
                                        + "§r§l' — " + totalItems + " items in "
                                        + results.size() + " container(s):");
                                int shown = 0;
                                for (SearchResult sr : results) {
                                    if (shown >= 10) {
                                        ChatHelper.labelled("Stash", "§8  ... and "
                                                + (results.size() - shown) + " more.");
                                        break;
                                    }
                                    StringBuilder line = new StringBuilder();
                                    line.append(" §7[§e")
                                        .append(sr.pos().getX()).append(" ")
                                        .append(sr.pos().getY()).append(" ")
                                        .append(sr.pos().getZ()).append("§7] ");
                                    for (var e : sr.matchedItems().entrySet()) {
                                        String shortId = e.getKey().startsWith("minecraft:")
                                                ? e.getKey().substring(10) : e.getKey();
                                        line.append("§f").append(shortId)
                                            .append(" §7x").append(e.getValue()).append("  ");
                                    }
                                    ChatHelper.labelled("Stash", line.toString().trim());
                                    shown++;
                                }
                                return 1;
                            })
                    )
            );

            // /stash get <item> [count]
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("get")
                    .then(ClientCommands.argument("item", StringArgumentType.word())
                            .suggests(SUGGEST_ITEMS)
            *//*?} else {*/
            root.then(ClientCommandManager.literal("get")
                    .then(ClientCommandManager.argument("item", StringArgumentType.word())
                            .suggests(SUGGEST_ITEMS)
            /*?}*/
                            .executes(ctx -> {
                                String item = StringArgumentType.getString(ctx, "item");
                                return handleGet(item, 64);
                            })
                            /*? if >=26.1 {*//*
                            .then(ClientCommands.argument("count", IntegerArgumentType.integer(1))
                            *//*?} else {*/
                            .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                            /*?}*/
                                    .executes(ctx -> {
                                        String item = StringArgumentType.getString(ctx, "item");
                                        int count = IntegerArgumentType.getInteger(ctx, "count");
                                        return handleGet(item, count);
                                    })
                            )
                    )
            );

            dispatcher.register(root);
            LOGGER.info("StashCommand: /stash registered");
        });
    }

    private static int handleGet(String item, int count) {
        StashRetriever retriever = getManager().getRetriever();
        if (retriever.isActive()) {
            ChatHelper.labelled("Stash", "§cRetrieval already in progress. Use §f/stash stop §cto cancel.");
            return 0;
        }
        return retriever.start(item, count) ? 1 : 0;
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

    /** Build the /stash kit sub-tree. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> buildKitSubcommand() {
        /*? if >=26.1 {*//*
        var kit = ClientCommands.literal("kit");
        *//*?} else {*/
        var kit = ClientCommandManager.literal("kit");
        /*?}*/

        // /stash kit  (help)
        kit.executes(ctx -> {
            ChatHelper.labelled("Stash", "§7Kit management — save item loadouts (max 27 slots).");
            ChatHelper.labelled("Stash", "  §f/stash kit create <name> §8— create an empty kit");
            ChatHelper.labelled("Stash", "  §f/stash kit snapshot <name> §8— snapshot inventory into kit");
            ChatHelper.labelled("Stash", "  §f/stash kit load <name> §8— retrieve kit items from stash");
            ChatHelper.labelled("Stash", "  §f/stash kit add <name> <item> [count] §8— add item to kit");
            ChatHelper.labelled("Stash", "  §f/stash kit remove <name> <item> §8— remove item from kit");
            ChatHelper.labelled("Stash", "  §f/stash kit show <name> §8— show kit contents & availability");
            ChatHelper.labelled("Stash", "  §f/stash kit list §8— list all kits");
            ChatHelper.labelled("Stash", "  §f/stash kit delete <name> §8— delete a kit");
            return 1;
        });

        // /stash kit create <name>
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("create")
                .then(ClientCommands.argument("name", StringArgumentType.word())
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("create")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            if (db.kitExists(name)) {
                                ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' already exists.");
                                return 0;
                            }
                            db.createKit(name);
                            ChatHelper.labelled("Stash", "§aCreated kit '§e" + name + "§a'.");
                            return 1;
                        })
                )
        );

        // /stash kit snapshot <name>
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("snapshot")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("snapshot")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            /*? if >=26.1 {*//*
                            Minecraft mc = Minecraft.getInstance();
                            *//*?} else {*/
                            MinecraftClient mc = MinecraftClient.getInstance();
                            /*?}*/
                            if (mc.player == null) return 0;

                            if (!db.kitExists(name)) {
                                db.createKit(name);
                            }

                            Map<String, Integer> items = new LinkedHashMap<>();
                            for (int i = 0; i < 36; i++) {
                                /*? if >=26.1 {*//*
                                ItemStack stack = mc.player.getInventory().getItem(i);
                                *//*?} else {*/
                                ItemStack stack = mc.player.getInventory().getStack(i);
                                /*?}*/
                                if (stack.isEmpty()) continue;
                                String itemId = ItemIdentifier.getItemId(stack);
                                items.merge(itemId, stack.getCount(), Integer::sum);
                            }

                            if (items.isEmpty()) {
                                ChatHelper.labelled("Stash", "§cInventory is empty — nothing to snapshot.");
                                return 0;
                            }

                            int uniqueBefore = items.size();
                            db.snapshotKit(name, items);
                            int slots = db.countKitSlots(name);
                            String msg = "§aSnapshot saved to kit '§e" + name + "§a' (" + slots + " slots).";
                            if (uniqueBefore > StashDatabase.KIT_MAX_SLOTS) {
                                msg += " §e(Truncated from " + uniqueBefore + " unique items to " + StashDatabase.KIT_MAX_SLOTS + ".)";
                            }
                            ChatHelper.labelled("Stash", msg);
                            return 1;
                        })
                )
        );

        // /stash kit add <name> <item> [count]
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("add")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
                        .then(ClientCommands.argument("item", StringArgumentType.word())
                                .suggests(SUGGEST_ITEMS)
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("add")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
                        .then(ClientCommandManager.argument("item", StringArgumentType.word())
                                .suggests(SUGGEST_ITEMS)
        /*?}*/
                                .executes(ctx -> {
                                    return handleKitAdd(ctx, 1);
                                })
                                /*? if >=26.1 {*//*
                                .then(ClientCommands.argument("count", IntegerArgumentType.integer(1))
                                *//*?} else {*/
                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
                                /*?}*/
                                        .executes(ctx -> {
                                            int count = IntegerArgumentType.getInteger(ctx, "count");
                                            return handleKitAdd(ctx, count);
                                        })
                                )
                        )
                )
        );

        // /stash kit remove <name> <item>
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("remove")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
                        .then(ClientCommands.argument("item", StringArgumentType.word())
                                .suggests(SUGGEST_ITEMS)
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("remove")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
                        .then(ClientCommandManager.argument("item", StringArgumentType.word())
                                .suggests(SUGGEST_ITEMS)
        /*?}*/
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    String item = StringArgumentType.getString(ctx, "item");
                                    StashDatabase db = MoarMod.getDatabase();
                                    if (db == null) {
                                        ChatHelper.labelled("Stash", "§cDatabase not available.");
                                        return 0;
                                    }
                                    if (!db.kitExists(name)) {
                                        ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' not found.");
                                        return 0;
                                    }
                                    if (db.removeKitItem(name, item)) {
                                        ChatHelper.labelled("Stash", "§aRemoved '§f" + item + "§a' from kit '§e" + name + "§a'.");
                                    } else {
                                        ChatHelper.labelled("Stash", "§cItem '§f" + item + "§c' not in kit '§e" + name + "§c'.");
                                    }
                                    return 1;
                                })
                        )
                )
        );

        // /stash kit show <name>
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("show")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("show")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            if (!db.kitExists(name)) {
                                ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' not found.");
                                return 0;
                            }
                            Map<String, Integer> kitItems = db.loadKitItems(name);
                            if (kitItems.isEmpty()) {
                                ChatHelper.labelled("Stash", "Kit '§e" + name + "§f' is empty.");
                                return 1;
                            }

                            // Build aggregate item totals from the stash index
                            Map<String, Integer> stashTotals = new LinkedHashMap<>();
                            for (ContainerEntry entry : getManager().getIndex().values()) {
                                for (var e : entry.items().entrySet()) {
                                    stashTotals.merge(e.getKey(), e.getValue(), Integer::sum);
                                }
                            }

                            ChatHelper.labelled("Stash", "§lKit '§e" + name + "§r§l' (" + kitItems.size() + " slots):");
                            int fulfilled = 0;
                            for (var e : kitItems.entrySet()) {
                                String itemId = e.getKey();
                                int needed = e.getValue();
                                int available = stashTotals.getOrDefault(itemId, 0);
                                boolean ok = available >= needed;
                                if (ok) fulfilled++;
                                String symbol = ok ? "§a✓" : "§c✗";
                                String shortId = itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId;
                                ChatHelper.labelled("Stash", " " + symbol + " §f" + shortId
                                        + " §7x" + needed + " §8(stash: " + available + ")");
                            }
                            ChatHelper.labelled("Stash", "§7Fulfilled: " + fulfilled + "/" + kitItems.size());
                            return 1;
                        })
                )
        );

        // /stash kit list
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("list")
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("list")
        /*?}*/
                .executes(ctx -> {
                    StashDatabase db = MoarMod.getDatabase();
                    if (db == null) {
                        ChatHelper.labelled("Stash", "§cDatabase not available.");
                        return 0;
                    }
                    List<String> kits = db.listKits();
                    if (kits.isEmpty()) {
                        ChatHelper.labelled("Stash", "No kits defined. Use §f/stash kit create <name>§7.");
                        return 1;
                    }
                    ChatHelper.labelled("Stash", "§lKits (" + kits.size() + "):");
                    for (String k : kits) {
                        int slots = db.countKitSlots(k);
                        ChatHelper.labelled("Stash", " §7- §e" + k + " §8(" + slots + " slots)");
                    }
                    return 1;
                })
        );

        // /stash kit delete <name>
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("delete")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("delete")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            if (!db.kitExists(name)) {
                                ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' not found.");
                                return 0;
                            }
                            db.deleteKit(name);
                            ChatHelper.labelled("Stash", "§aDeleted kit '§e" + name + "§a'.");
                            return 1;
                        })
                )
        );

        // /stash kit load <name>
        /*? if >=26.1 {*//*
        kit.then(ClientCommands.literal("load")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        *//*?} else {*/
        kit.then(ClientCommandManager.literal("load")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            if (!db.kitExists(name)) {
                                ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' not found.");
                                return 0;
                            }
                            Map<String, Integer> kitItems = db.loadKitItems(name);
                            if (kitItems.isEmpty()) {
                                ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' is empty.");
                                return 0;
                            }
                            StashRetriever retriever = getManager().getRetriever();
                            if (retriever.isActive()) {
                                ChatHelper.labelled("Stash", "§cRetrieval already in progress. Use §f/stash stop §cto cancel.");
                                return 0;
                            }
                            return retriever.startKit(name, kitItems) ? 1 : 0;
                        })
                )
        );

        return kit;
    }

    private static int handleKitAdd(com.mojang.brigadier.context.CommandContext<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx, int count) {
        String name = StringArgumentType.getString(ctx, "name");
        String item = StringArgumentType.getString(ctx, "item");
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) {
            ChatHelper.labelled("Stash", "§cDatabase not available.");
            return 0;
        }
        if (!db.kitExists(name)) {
            ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' not found. Create it first.");
            return 0;
        }
        if (db.addKitItem(name, item, count)) {
            int slots = db.countKitSlots(name);
            ChatHelper.labelled("Stash", "§aAdded §f" + item + " §7x" + count
                    + "§a to kit '§e" + name + "§a'. §8(" + slots + "/" + StashDatabase.KIT_MAX_SLOTS + " slots)");
        } else {
            ChatHelper.labelled("Stash", "§cKit '§e" + name + "§c' is full ("
                    + StashDatabase.KIT_MAX_SLOTS + " slots). Remove an item first.");
        }
        return 1;
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

    /** Build the /stash region sub-tree (save, load, list, delete). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> buildRegionSubcommand() {
        /*? if >=26.1 {*//*
        var region = ClientCommands.literal("region");
        *//*?} else {*/
        var region = ClientCommandManager.literal("region");
        /*?}*/

        // /stash region  (help)
        region.executes(ctx -> {
            ChatHelper.labelled("Stash", "§7Region profiles — save and restore named region corners.");
            ChatHelper.labelled("Stash", "  §f/stash region save <name> §8— save current pos1/pos2 as a profile");
            ChatHelper.labelled("Stash", "  §f/stash region load <name> §8— restore a saved profile");
            ChatHelper.labelled("Stash", "  §f/stash region list §8— list all saved profiles");
            ChatHelper.labelled("Stash", "  §f/stash region delete <name> §8— delete a profile");
            return 1;
        });

        // /stash region save <name>
        /*? if >=26.1 {*//*
        region.then(ClientCommands.literal("save")
                .then(ClientCommands.argument("name", StringArgumentType.word())
        *//*?} else {*/
        region.then(ClientCommandManager.literal("save")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashManager mgr = getManager();
                            if (mgr.getCorner1() == null || mgr.getCorner2() == null) {
                                ChatHelper.labelled("Stash", "§cSet both corners first: §f/stash pos1 §cand §f/stash pos2");
                                return 0;
                            }
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            db.saveRegion(name, mgr.getCorner1(), mgr.getCorner2());
                            ChatHelper.labelled("Stash", "§aSaved region '§e" + name + "§a' (" +
                                    mgr.getCorner1().getX() + " " + mgr.getCorner1().getY() + " " + mgr.getCorner1().getZ() +
                                    " → " +
                                    mgr.getCorner2().getX() + " " + mgr.getCorner2().getY() + " " + mgr.getCorner2().getZ() + ").");
                            return 1;
                        })
                )
        );

        // /stash region load <name>
        /*? if >=26.1 {*//*
        region.then(ClientCommands.literal("load")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_REGIONS)
        *//*?} else {*/
        region.then(ClientCommandManager.literal("load")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_REGIONS)
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            BlockPos[] corners = db.loadRegion(name);
                            if (corners == null) {
                                ChatHelper.labelled("Stash", "§cRegion '§e" + name + "§c' not found.");
                                return 0;
                            }
                            StashManager mgr = getManager();
                            mgr.setCorner1(corners[0]);
                            mgr.setCorner2(corners[1]);
                            ChatHelper.labelled("Stash", "§aLoaded region '§e" + name + "§a' (" +
                                    corners[0].getX() + " " + corners[0].getY() + " " + corners[0].getZ() +
                                    " → " +
                                    corners[1].getX() + " " + corners[1].getY() + " " + corners[1].getZ() + ").");
                            return 1;
                        })
                )
        );

        // /stash region list
        /*? if >=26.1 {*//*
        region.then(ClientCommands.literal("list")
        *//*?} else {*/
        region.then(ClientCommandManager.literal("list")
        /*?}*/
                .executes(ctx -> {
                    StashDatabase db = MoarMod.getDatabase();
                    if (db == null) {
                        ChatHelper.labelled("Stash", "§cDatabase not available.");
                        return 0;
                    }
                    Map<String, BlockPos[]> regions = db.loadAllRegions();
                    if (regions.isEmpty()) {
                        ChatHelper.labelled("Stash", "§7No saved regions.");
                        return 1;
                    }
                    ChatHelper.labelled("Stash", "§lSaved regions (" + regions.size() + "):");
                    for (var entry : regions.entrySet()) {
                        BlockPos[] c = entry.getValue();
                        ChatHelper.labelled("Stash", " §7- §e" + entry.getKey() + " §8(" +
                                c[0].getX() + " " + c[0].getY() + " " + c[0].getZ() +
                                " → " +
                                c[1].getX() + " " + c[1].getY() + " " + c[1].getZ() + ")");
                    }
                    return 1;
                })
        );

        // /stash region delete <name>
        /*? if >=26.1 {*//*
        region.then(ClientCommands.literal("delete")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_REGIONS)
        *//*?} else {*/
        region.then(ClientCommandManager.literal("delete")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests(SUGGEST_REGIONS)
        /*?}*/
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            StashDatabase db = MoarMod.getDatabase();
                            if (db == null) {
                                ChatHelper.labelled("Stash", "§cDatabase not available.");
                                return 0;
                            }
                            if (db.deleteRegion(name)) {
                                ChatHelper.labelled("Stash", "§aDeleted region '§e" + name + "§a'.");
                            } else {
                                ChatHelper.labelled("Stash", "§cRegion '§e" + name + "§c' not found.");
                            }
                            return 1;
                        })
                )
        );

        return region;
    }
}
