package dev.moar.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.moar.MoarMod;
import dev.moar.chest.ChestManager;
import dev.moar.printer.SchematicPrinter;
import dev.moar.schematic.LitematicaDetector;
import dev.moar.schematic.PrinterCheckpoint;
import dev.moar.schematic.PrinterResourceManager;
import dev.moar.util.ChatHelper;
/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*//*?} else {*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
/*?}*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.BarrelBlock;
*//*?} else {*/
import net.minecraft.block.BarrelBlock;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.Block;
*//*?} else {*/
import net.minecraft.block.Block;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.ChestBlock;
*//*?} else {*/
import net.minecraft.block.ChestBlock;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.ShulkerBoxBlock;
*//*?} else {*/
import net.minecraft.block.ShulkerBoxBlock;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.BlockHitResult;
*//*?} else {*/
import net.minecraft.util.hit.BlockHitResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.HitResult;
*//*?} else {*/
import net.minecraft.util.hit.HitResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// Registers all /printer client commands.
public final class PrinterCommand {

    private PrinterCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            /*? if >=26.1 {*//*
            var root = ClientCommands.literal("printer");
            *//*?} else {*/
            var root = ClientCommandManager.literal("printer");
            /*?}*/

            // /printer load <file>
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("load")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("load")
            /*?}*/
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.argument("file", StringArgumentType.greedyString())
                    *//*?} else {*/
                    .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                    /*?}*/
                            .suggests((ctx, builder) -> {
                                for (String name : SchematicPrinter.listSchematics()) {
                                    builder.suggest(name);
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String filename = StringArgumentType.getString(ctx, "file");
                                return loadSchematic(filename);
                            })
                    )
            );

            // /printer unload
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("unload")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("unload")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        if (!printer.isLoaded()) {
                            ChatHelper.info("No schematic loaded.");
                            return 0;
                        }
                        printer.unload();
                        ChatHelper.info("Schematic unloaded.");
                        return 1;
                    })
            );

            // /printer here
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("here")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("here")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        /*? if >=26.1 {*//*
                        Minecraft mc = Minecraft.getInstance();
                        *//*?} else {*/
                        MinecraftClient mc = MinecraftClient.getInstance();
                        /*?}*/
                        if (mc.player == null) return 0;

                        if (!printer.isLoaded()) {
                            ChatHelper.info("§cNo schematic loaded.");
                            return 0;
                        }

                        List<LitematicaDetector.DetectedPlacement> placements =
                                SchematicPrinter.detectAllPlacements();
                        LitematicaDetector.DetectedPlacement bestMatch =
                                findClosestPlacement(placements, mc.player.getX(), mc.player.getZ(), null, false);
                        boolean nearbyUnsupportedPlacement = bestMatch != null
                                && bestMatch.hasUnsupportedTransform()
                                && horizontalDistance(bestMatch, mc.player.getX(), mc.player.getZ()) < 200;

                        BlockPos pos = null;
                        if (!nearbyUnsupportedPlacement) {
                            pos = LitematicaDetector.detectAnchorFromSchematicWorld(
                                    printer.getSchematic());
                            if (pos != null) {
                                ChatHelper.info("§aAligned anchor from hologram blocks.");
                            }
                        } else {
                            warnUnsupportedPlacement(bestMatch);
                            ChatHelper.info("§7Skipping hologram alignment because MOAR can't map"
                                    + " transformed Litematica placements yet.");
                        }

                        if (pos == null) {
                            bestMatch = findClosestPlacement(placements, mc.player.getX(), mc.player.getZ(), null, true);
                            if (bestMatch != null && horizontalDistance(bestMatch, mc.player.getX(), mc.player.getZ()) < 200) {
                                pos = new BlockPos(
                                        bestMatch.originX() + printer.getSchematic().getOriginOffsetX(),
                                        bestMatch.originY() + printer.getSchematic().getOriginOffsetY(),
                                        bestMatch.originZ() + printer.getSchematic().getOriginOffsetZ());
                                ChatHelper.info("§aSnapped to Litematica placement origin.");
                            } else {
                                /*? if >=26.1 {*//*
                                pos = mc.player.blockPosition();
                                *//*?} else {*/
                                pos = mc.player.getBlockPos();
                                /*?}*/
                            }
                        }

                        printer.setAnchor(pos);
                        ChatHelper.info("Anchor set to §e" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        return 1;
                    })
            );

            // /printer pos <x> <y> <z>
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("pos")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("pos")
            /*?}*/
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
                                                SchematicPrinter printer = getPrinter();
                                                if (!printer.isLoaded()) {
                                                    ChatHelper.info("§cNo schematic loaded.");
                                                    return 0;
                                                }

                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                printer.setAnchor(new BlockPos(x, y, z));
                                                ChatHelper.info("Anchor set to §e" + x + " " + y + " " + z);
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /printer status
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("status")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("status")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        if (!printer.isLoaded()) {
                            ChatHelper.info("No schematic loaded.");
                            return 1;
                        }

                        ChatHelper.info("§lSchematic Printer Status");
                        ChatHelper.info("File: §b" + printer.getSchematic().getName());
                        ChatHelper.info("Author: §7" + printer.getSchematic().getAuthor());
                        ChatHelper.info("Size: §e"
                                + printer.getSchematic().getSizeX() + "x"
                                + printer.getSchematic().getSizeY() + "x"
                                + printer.getSchematic().getSizeZ());
                        ChatHelper.info("Total blocks: §e" + printer.getSchematic().getTotalNonAir());

                        BlockPos anchor = printer.getAnchor();
                        ChatHelper.info("Anchor: §e" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());
                        ChatHelper.info("Placed (session): §a" + printer.getBlocksPlaced());
                        ChatHelper.info("Printing: " + (printer.isEnabled() ? "§aON" : "§cOFF"));

                        int remaining = printer.countRemaining();
                        if (remaining >= 0) {
                            int total = printer.getSchematic().getTotalNonAir();
                            int done = total - remaining;
                            int pct = total > 0 ? (done * 100 / total) : 100;
                            ChatHelper.info("Progress: §e" + done + "/" + total
                                    + " §7(§a" + pct + "%§7)");
                        }

                        return 1;
                    })
            );

            // /printer list
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("list")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("list")
            /*?}*/
                    .executes(ctx -> {
                        List<String> schematics = SchematicPrinter.listSchematics();
                        if (schematics.isEmpty()) {
                            ChatHelper.info("No schematics found in §7"
                                    + SchematicPrinter.getSchematicsDir().toString());
                        } else {
                            ChatHelper.info("§lAvailable schematics (" + schematics.size() + "):");
                            for (String name : schematics) {
                                ChatHelper.info(" §7- §f" + name);
                            }
                        }
                        return 1;
                    })
            );

            // /printer detect
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("detect")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("detect")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();

                        List<LitematicaDetector.DetectedPlacement> placements =
                                SchematicPrinter.detectAllPlacements();

                        if (placements.isEmpty()) {
                            if (printer.isLoaded()) {
                                BlockPos pos = LitematicaDetector.detectAnchorFromSchematicWorld(
                                        printer.getSchematic());
                                if (pos != null) {
                                    printer.setAnchor(pos);
                                    ChatHelper.info("§eNo active placement list found, but hologram blocks were detected.");
                                    ChatHelper.info("§aAnchor aligned from hologram blocks.");
                                    ChatHelper.info("§7Use §f/printer toggle §7to start printing.");
                                    return 1;
                                }
                            }
                            ChatHelper.info("§cNo active Litematica placements detected.");
                            ChatHelper.info("§7Make sure you have a schematic loaded and placed in Litematica.");
                            return 0;
                        }

                        ChatHelper.info("§lDetected Litematica placements (" + placements.size() + "):");
                        for (int i = 0; i < placements.size(); i++) {
                            LitematicaDetector.DetectedPlacement p = placements.get(i);
                            ChatHelper.info(" §7" + (i + 1) + ". §f" + p.schematicPath().getFileName()
                                    + " §7at §e" + p.originX() + " " + p.originY() + " " + p.originZ()
                                    + " §7(\"" + p.name() + "\")"
                                    + (p.hasUnsupportedTransform()
                                    ? " §c[" + p.unsupportedTransformSummary() + "]"
                                    : ""));
                        }

                        if (printer.tryAutoDetect()) {
                            ChatHelper.info("§aLoaded: §f" + printer.getSchematic().getName()
                                    + " §7(" + printer.getSchematic().getTotalNonAir() + " blocks)");
                            BlockPos a = printer.getAnchor();
                            ChatHelper.info("Anchored at §e" + a.getX() + " " + a.getY() + " " + a.getZ());
                            ChatHelper.info("§7Use §f/printer toggle §7to start printing.");
                        }

                        return 1;
                    })
            );

            // /printer resume
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("resume")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("resume")
            /*?}*/
                    .executes(ctx -> {
                        if (!PrinterCheckpoint.exists()) {
                            ChatHelper.info("§cNo checkpoint found. Nothing to resume.");
                            return 0;
                        }

                        PrinterCheckpoint.CheckpointData data = PrinterCheckpoint.load();
                        if (data == null) {
                            ChatHelper.info("§cCheckpoint file is corrupt or empty.");
                            return 0;
                        }

                        SchematicPrinter printer = getPrinter();

                        Path file = SchematicPrinter.getSchematicsDir()
                                .resolve(data.schematicFile);
                        if (!Files.exists(file)) {
                            ChatHelper.info("§cSchematic file not found: §7" + data.schematicFile);
                            return 0;
                        }

                        try {
                            printer.restoreFromCheckpoint(data, file);
                            ChatHelper.info("§aResumed §f" + printer.getSchematic().getName());
                            ChatHelper.info("Anchor: §e" + data.anchorX + " " + data.anchorY + " " + data.anchorZ);
                            ChatHelper.info("Previously placed: §e" + data.blocksPlaced + " §7blocks");
                            ChatHelper.info("Checkpoint saved: §7" + data.timeSince());
                            ChatHelper.info("§7Use §f/printer toggle §7to continue.");
                            return 1;
                        } catch (IOException e) {
                            ChatHelper.info("§cFailed to resume: " + e.getMessage());
                            return 0;
                        }
                    })
            );

            // /printer materials
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("materials")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("materials")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();

                        if (!printer.isLoaded()) {
                            ChatHelper.info("§cNo schematic loaded.");
                            return 0;
                        }

                        ChatHelper.info("§7Analyzing materials... (may take a moment)");

                        PrinterResourceManager.MaterialsReport report = printer.analyzeMaterials();
                        if (report.totalBlocks() == 0) {
                            ChatHelper.info("§cSchematic has no placeable blocks.");
                            return 0;
                        }

                        ChatHelper.info("§l§6Materials Report");
                        ChatHelper.info("Total blocks: §e" + report.totalBlocks());
                        ChatHelper.info("Placed: §a" + report.placedBlocks()
                                + " §7(§a" + report.percentComplete() + "%§7)");
                        ChatHelper.info("Remaining: §c" + report.missingCount());

                        // show top 10 most-needed items
                        List<Map.Entry<String, Integer>> top = report.missing().entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(10)
                                .toList();

                        if (!top.isEmpty()) {
                            ChatHelper.info("§7Top missing items:");
                            for (var entry : top) {
                                String pretty = PrinterResourceManager.MaterialsReport.prettyName(entry.getKey());
                                int need = entry.getValue();
                                ChatHelper.info(" §7- §f" + pretty + "§7: §c" + need);
                            }
                        }

                        if (report.hasUnknownBlocks()) {
                            ChatHelper.info("§e⚠ Unrecognized blocks (newer MC version): §c"
                                    + report.unknownCount() + "§e blocks across §c"
                                    + report.unknownBlocks().size() + "§e type(s)");
                            report.unknownBlocks().entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .limit(5)
                                    .forEach(e -> ChatHelper.info("  §7- §c" + e.getKey()
                                            + " §7(×" + e.getValue() + ")"));
                            ChatHelper.info("§7These blocks cannot be placed on this MC version.");
                        }

                        return 1;
                    })
            );

            // /printer toggle
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("toggle")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("toggle")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        printer.toggle();
                        return 1;
                    })
            );

            // /printer autobuild
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("autobuild")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("autobuild")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        boolean newValue = !printer.isAutoBuild();
                        printer.setAutoBuild(newValue);
                        ChatHelper.info("AutoBuild: " + (newValue ? "§aON" : "§cOFF"));
                        return 1;
                    })
            );

            // /printer air — toggle air placement (place blocks without adjacent support)
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("air")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("air")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        boolean newValue = !printer.isPrintInAir();
                        printer.setPrintInAir(newValue);
                        ChatHelper.info("Air placement: " + (newValue ? "§aON" : "§cOFF"));
                        return 1;
                    })
            );
            // /printer speed [1-20] -- set placement speed (blocks per second)
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("speed")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("speed")
            /*?}*/
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        ChatHelper.info("Current speed: §e" + printer.getBps() + " §7blocks/sec (range: 1-20)");
                        return 1;
                    })
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.argument("bps", IntegerArgumentType.integer(1, 20))
                    *//*?} else {*/
                    .then(ClientCommandManager.argument("bps", IntegerArgumentType.integer(1, 20))
                    /*?}*/
                            .executes(ctx -> {
                                SchematicPrinter printer = getPrinter();
                                int value = IntegerArgumentType.getInteger(ctx, "bps");
                                printer.setBps(value);
                                ChatHelper.info("Placement speed: §e" + value + " §7blocks/sec");
                                return 1;
                            })
                    )
            );
            // /printer sort [mode] — set build order (bottom_up, top_down, nearest)
            /*? if >=26.1 {*//*
            root.then(ClientCommands.literal("sort")
            *//*?} else {*/
            root.then(ClientCommandManager.literal("sort")
            /*?}*/
                    .executes(ctx -> {
                        // No argument — cycle through modes
                        SchematicPrinter printer = getPrinter();
                        SchematicPrinter.SortMode current = printer.getSortMode();
                        SchematicPrinter.SortMode next = switch (current) {
                            case BOTTOM_UP -> SchematicPrinter.SortMode.TOP_DOWN;
                            case TOP_DOWN  -> SchematicPrinter.SortMode.NEAREST;
                            case NEAREST   -> SchematicPrinter.SortMode.BOTTOM_UP;
                        };
                        printer.setSortMode(next);
                        ChatHelper.info("Sort mode: §e" + next.name());
                        return 1;
                    })
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.argument("mode", StringArgumentType.word())
                    *//*?} else {*/
                    .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                    /*?}*/
                            .suggests((ctx, builder) -> {
                                builder.suggest("bottom_up");
                                builder.suggest("top_down");
                                builder.suggest("nearest");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                SchematicPrinter printer = getPrinter();
                                String mode = StringArgumentType.getString(ctx, "mode").toUpperCase();
                                try {
                                    SchematicPrinter.SortMode sm = SchematicPrinter.SortMode.valueOf(mode);
                                    printer.setSortMode(sm);
                                    ChatHelper.info("Sort mode: §e" + sm.name());
                                    return 1;
                                } catch (IllegalArgumentException e) {
                                    ChatHelper.info("§cUnknown sort mode: §7" + mode
                                            + " §c(use bottom_up, top_down, or nearest)");
                                    return 0;
                                }
                            })
                    )
            );

            // /printer supply

            /*? if >=26.1 {*//*
            var supply = ClientCommands.literal("supply");
            *//*?} else {*/
            var supply = ClientCommandManager.literal("supply");
            /*?}*/

            // /printer supply add
            /*? if >=26.1 {*//*
            supply.then(ClientCommands.literal("add")
            *//*?} else {*/
            supply.then(ClientCommandManager.literal("add")
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
                            ChatHelper.info("§cNo chest, barrel, or shulker box found. Look at one or stand next to it.");
                            return 0;
                        }
                        if (PrinterResourceManager.addSupplyChest(pos)) {
                            ChatHelper.info("§aMarked supply chest at §e"
                                    + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        } else {
                            ChatHelper.info("§eThat position is already marked.");
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
                                                if (PrinterResourceManager.addSupplyChest(pos)) {
                                                    ChatHelper.info("§aMarked supply chest at §e"
                                                            + x + " " + y + " " + z);
                                                } else {
                                                    ChatHelper.info("§eThat position is already marked.");
                                                }
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /printer supply remove
            /*? if >=26.1 {*//*
            supply.then(ClientCommands.literal("remove")
            *//*?} else {*/
            supply.then(ClientCommandManager.literal("remove")
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
                            ChatHelper.info("§cNo supply chest found nearby to remove.");
                            return 0;
                        }
                        if (PrinterResourceManager.removeSupplyChest(pos)) {
                            ChatHelper.info("§aRemoved supply chest at §e"
                                    + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        } else {
                            ChatHelper.info("§cThat container is not marked as a supply chest.");
                        }
                        return 1;
                    })
            );

            // /printer supply list
            /*? if >=26.1 {*//*
            supply.then(ClientCommands.literal("list")
            *//*?} else {*/
            supply.then(ClientCommandManager.literal("list")
            /*?}*/
                    .executes(ctx -> {
                        List<BlockPos> chests = PrinterResourceManager.getSupplyChests();
                        if (chests.isEmpty()) {
                            ChatHelper.info("No supply chests designated.");
                            ChatHelper.info("§7Use §f/printer supply add §7while standing at a chest.");
                        } else {
                            ChatHelper.info("§lSupply chests (" + chests.size() + "):");
                            for (BlockPos pos : chests) {
                                ChatHelper.info(" §7- §e" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                            }
                        }
                        return 1;
                    })
            );

            // /printer supply clear
            /*? if >=26.1 {*//*
            supply.then(ClientCommands.literal("clear")
            *//*?} else {*/
            supply.then(ClientCommandManager.literal("clear")
            /*?}*/
                    .executes(ctx -> {
                        PrinterResourceManager.clearSupplyChests();
                        ChatHelper.info("§aAll supply chest designations cleared.");
                        return 1;
                    })
            );

            // /printer supply scan
            /*? if >=26.1 {*//*
            supply.then(ClientCommands.literal("scan")
            *//*?} else {*/
            supply.then(ClientCommandManager.literal("scan")
            /*?}*/
                    .executes(ctx -> {
                        List<BlockPos> chests = PrinterResourceManager.getSupplyChests();
                        if (chests.isEmpty()) {
                            ChatHelper.info("§cNo supply chests designated.");
                            ChatHelper.info("§7Use §f/printer supply add §7while standing at a chest.");
                            return 0;
                        }

                        ChestManager.ChestIndexSummary summary = MoarMod.getChestManager().getIndexSummary();
                        ChatHelper.info("§l§6Supply Index Summary");
                        ChatHelper.info("Chests: §e" + summary.totalChests()
                                + " §7(§a" + summary.indexedChests() + " indexed§7, §c"
                                + summary.unindexedChests() + " unscanned§7)");

                        if (summary.indexedChests() > 0) {
                            ChatHelper.info("Shulker boxes found: §d" + summary.totalShulkers());
                            ChatHelper.info("Item types indexed: §e" + summary.totalItemTypes());
                            ChatHelper.info("Total items available: §a" + summary.totalItems());

                            // Show top 10 items in supply
                            Map<String, Integer> combined = MoarMod.getChestManager().getCombinedInventory();
                            List<Map.Entry<String, Integer>> top = combined.entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .limit(10)
                                    .toList();

                            if (!top.isEmpty()) {
                                ChatHelper.info("§7Top items in supply:");
                                for (var entry : top) {
                                    String pretty = PrinterResourceManager.MaterialsReport.prettyName(entry.getKey());
                                    ChatHelper.info(" §7- §f" + pretty + "§7: §a" + entry.getValue());
                                }
                                if (combined.size() > 10) {
                                    ChatHelper.info(" §7... and " + (combined.size() - 10) + " more types");
                                }
                            }
                        }

                        if (summary.unindexedChests() > 0) {
                            ChatHelper.info("§7Open unscanned chests to index them, or they'll be");
                            ChatHelper.info("§7auto-indexed during restocking.");
                        }

                        return 1;
                    })
            );

            root.then(supply);

            // /printer dump sub-tree

            /*? if >=26.1 {*//*
            var dump = ClientCommands.literal("dump");
            *//*?} else {*/
            var dump = ClientCommandManager.literal("dump");
            /*?}*/

            // /printer dump  (help)
            dump.executes(ctx -> {
                ChatHelper.info("§7Dump chests store mined items during area clearing.");
                ChatHelper.info("  §f/printer dump add §7[x y z] §8— mark a dump chest");
                ChatHelper.info("  §f/printer dump remove §8— unmark nearest dump chest");
                ChatHelper.info("  §f/printer dump list §8— show all dump chests");
                ChatHelper.info("  §f/printer dump clear §8— clear all dump chests");
                return 1;
            });

            // /printer dump add [x y z]
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
                            ChatHelper.info("§cNo chest, barrel, or shulker box found. Look at one or stand next to it.");
                            return 0;
                        }
                        if (MoarMod.getChestManager().addDumpChest(pos)) {
                            ChatHelper.info("§aMarked dump chest at §e"
                                    + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        } else {
                            ChatHelper.info("§eThat position is already marked.");
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
                                                    ChatHelper.info("§aMarked dump chest at §e" + x + " " + y + " " + z);
                                                } else {
                                                    ChatHelper.info("§eThat position is already marked.");
                                                }
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /printer dump remove
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
                            ChatHelper.info("§cNo dump chest found nearby to remove.");
                            return 0;
                        }
                        if (MoarMod.getChestManager().removeDumpChest(pos)) {
                            ChatHelper.info("§aRemoved dump chest at §e"
                                    + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        } else {
                            ChatHelper.info("§cThat container is not marked as a dump chest.");
                        }
                        return 1;
                    })
            );

            // /printer dump list
            /*? if >=26.1 {*//*
            dump.then(ClientCommands.literal("list")
            *//*?} else {*/
            dump.then(ClientCommandManager.literal("list")
            /*?}*/
                    .executes(ctx -> {
                        List<BlockPos> chests = MoarMod.getChestManager().getDumpPositions();
                        if (chests.isEmpty()) {
                            ChatHelper.info("No dump chests designated.");
                            ChatHelper.info("§7Use §f/printer dump add §7while standing at a chest.");
                        } else {
                            ChatHelper.info("§lDump chests (" + chests.size() + "):");
                            for (BlockPos pos : chests) {
                                ChatHelper.info(" §7- §e" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                            }
                        }
                        return 1;
                    })
            );

            // /printer dump clear
            /*? if >=26.1 {*//*
            dump.then(ClientCommands.literal("clear")
            *//*?} else {*/
            dump.then(ClientCommandManager.literal("clear")
            /*?}*/
                    .executes(ctx -> {
                        MoarMod.getChestManager().clearDumpChests();
                        ChatHelper.info("§aAll dump chest designations cleared.");
                        return 1;
                    })
            );

            root.then(dump);

            dispatcher.register(root);
        });
    }

    // helpers

    private static int loadSchematic(String filename) {
        SchematicPrinter printer = getPrinter();
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return 0;

        Path dir = SchematicPrinter.getSchematicsDir();
        Path file = dir.resolve(filename.endsWith(".litematic") ? filename : filename + ".litematic");

        if (!Files.exists(file)) {
            ChatHelper.info("§cFile not found: §7" + file.getFileName());
            ChatHelper.info("§7Use §f/printer list §7to see available schematics.");
            return 0;
        }

        try {
            // Start at the player and align later if possible.
            /*? if >=26.1 {*//*
            BlockPos anchor = mc.player.blockPosition();
            *//*?} else {*/
            BlockPos anchor = mc.player.getBlockPos();
            /*?}*/
            printer.loadSchematic(file, anchor);

            // Prefer the nearest matching placement.
            String loadedFile = file.getFileName().toString();
            boolean matchedPlacement = false;
            boolean sawUnsupportedPlacement = false;
            List<LitematicaDetector.DetectedPlacement> placements =
                    SchematicPrinter.detectAllPlacements();
            LitematicaDetector.DetectedPlacement unsupportedMatch =
                    findClosestPlacement(placements, mc.player.getX(), mc.player.getZ(), loadedFile, false);
            LitematicaDetector.DetectedPlacement bestMatch =
                    findClosestPlacement(placements, mc.player.getX(), mc.player.getZ(), loadedFile, true);
            if (bestMatch != null) {
                // Warn on default-origin placements.
                if (bestMatch.originX() == 0 && bestMatch.originY() == 0 && bestMatch.originZ() == 0) {
                    ChatHelper.info("§e⚠ Litematica placement origin is (0, 0, 0)"
                            + " — did you move it to the build site?");
                    ChatHelper.info("§7The anchor will use your current position instead."
                            + " If this is wrong, move the placement in Litematica"
                            + " and run §f/printer load §7again.");
                } else {
                    anchor = new BlockPos(
                            bestMatch.originX() + printer.getSchematic().getOriginOffsetX(),
                            bestMatch.originY() + printer.getSchematic().getOriginOffsetY(),
                            bestMatch.originZ() + printer.getSchematic().getOriginOffsetZ());
                    printer.setAnchor(anchor);
                    matchedPlacement = true;
                    ChatHelper.info("§aMatched Litematica placement.");
                }
            } else if (unsupportedMatch != null) {
                sawUnsupportedPlacement = true;
                warnUnsupportedPlacement(unsupportedMatch);
                ChatHelper.info("§7Keeping the schematic anchored to your position instead.");
            }

            if (!matchedPlacement) {
                if (!sawUnsupportedPlacement) {
                    ChatHelper.info("§7No Litematica placement found — anchored at your position."
                            + " Use §f/printer here§7 to re-anchor.");
                }
            }

            // Use hologram correlation for a final anchor fix.
            if (!sawUnsupportedPlacement) {
                BlockPos correlated = LitematicaDetector.detectAnchorFromSchematicWorld(
                        printer.getSchematic());
                if (correlated != null) {
                    anchor = correlated;
                    printer.setAnchor(anchor);
                    matchedPlacement = true;
                    ChatHelper.info("§aAnchor aligned from hologram blocks.");
                }
            }

            ChatHelper.info("§aLoaded §f" + printer.getSchematic().getName()
                    + " §7by " + printer.getSchematic().getAuthor());
            ChatHelper.info("Size: §e"
                    + printer.getSchematic().getSizeX() + "x"
                    + printer.getSchematic().getSizeY() + "x"
                    + printer.getSchematic().getSizeZ()
                    + " §7(" + printer.getSchematic().getTotalNonAir() + " blocks)");
            ChatHelper.info("Anchored at §e"
                    + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());

            if (printer.getSchematic().isFromFuture()) {
                ChatHelper.info("§e⚠ This schematic was created in a newer Minecraft version.");
            }
            if (printer.getSchematic().hasUnknownBlocks()) {
                var unknown = printer.getSchematic().getUnknownBlocks();
                int totalUnknown = unknown.values().stream().mapToInt(Integer::intValue).sum();
                ChatHelper.info("§e⚠ " + unknown.size() + " block type(s) not recognized ("
                        + totalUnknown + " blocks will be skipped):");
                unknown.entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(8)
                        .forEach(e -> ChatHelper.info("  §7- §c" + e.getKey() + " §7(×" + e.getValue() + ")"));
                if (unknown.size() > 8) {
                    ChatHelper.info("  §7... and " + (unknown.size() - 8) + " more");
                }
            }

            ChatHelper.info("§7Use §f/printer toggle §7to start printing.");

            return 1;
        } catch (IOException e) {
            ChatHelper.info("§cFailed to load: " + e.getMessage());
            return 0;
        }
    }

    private static SchematicPrinter getPrinter() {
        return MoarMod.getPrinter();
    }

    private static LitematicaDetector.DetectedPlacement findClosestPlacement(
            List<LitematicaDetector.DetectedPlacement> placements,
            double playerX,
            double playerZ,
            String requiredFile,
            boolean supportedOnly) {
        LitematicaDetector.DetectedPlacement bestMatch = null;
        double bestDist = Double.MAX_VALUE;

        for (LitematicaDetector.DetectedPlacement placement : placements) {
            if (requiredFile != null
                    && !placement.schematicPath().getFileName().toString().equals(requiredFile)) {
                continue;
            }
            if (supportedOnly && placement.hasUnsupportedTransform()) {
                continue;
            }

            double dist = horizontalDistance(placement, playerX, playerZ);
            if (dist < bestDist) {
                bestDist = dist;
                bestMatch = placement;
            }
        }

        return bestMatch;
    }

    private static double horizontalDistance(LitematicaDetector.DetectedPlacement placement,
                                             double playerX, double playerZ) {
        double dx = placement.originX() - playerX;
        double dz = placement.originZ() - playerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void warnUnsupportedPlacement(LitematicaDetector.DetectedPlacement placement) {
        ChatHelper.info("§e⚠ Matching Litematica placement uses an unsupported transform.");
        ChatHelper.info("§7Details: §f" + placement.unsupportedTransformSummary());
        ChatHelper.info("§7Reset the placement to no rotation/mirror and default sub-region placement"
                + " if you want MOAR to align to it automatically.");
    }

    // Find the targeted container: crosshair, feet, then below.
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

        // Check the crosshair target.
        /*? if >=26.1 {*//*
        if (mc.hitResult instanceof BlockHitResult bhr
        *//*?} else {*/
        if (mc.crosshairTarget instanceof BlockHitResult bhr
        /*?}*/
                /*? if >=26.1 {*//*
                && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                *//*?} else {*/
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                /*?}*/
            /*? if >=26.1 {*//*
            BlockPos lookPos = bhr.getBlockPos();
            *//*?} else {*/
            BlockPos lookPos = bhr.getBlockPos();
            /*?}*/
            /*? if >=26.1 {*//*
            if (isContainer(mc.level.getBlockState(lookPos).getBlock())) {
            *//*?} else {*/
            if (isContainer(mc.world.getBlockState(lookPos).getBlock())) {
            /*?}*/
                return lookPos;
            }
        }

        // Check the feet block.
        /*? if >=26.1 {*//*
        BlockPos feet = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos feet = mc.player.getBlockPos();
        /*?}*/
        /*? if >=26.1 {*//*
        if (isContainer(mc.level.getBlockState(feet).getBlock())) {
        *//*?} else {*/
        if (isContainer(mc.world.getBlockState(feet).getBlock())) {
        /*?}*/
            return feet;
        }

        // Check below the feet.
        /*? if >=26.1 {*//*
        BlockPos below = feet.below();
        *//*?} else {*/
        BlockPos below = feet.down();
        /*?}*/
        /*? if >=26.1 {*//*
        if (isContainer(mc.level.getBlockState(below).getBlock())) {
        *//*?} else {*/
        if (isContainer(mc.world.getBlockState(below).getBlock())) {
        /*?}*/
            return below;
        }

        return null;
    }

    /** Whether the block is a supported container type. */
    private static boolean isContainer(Block block) {
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock;
    }
}
