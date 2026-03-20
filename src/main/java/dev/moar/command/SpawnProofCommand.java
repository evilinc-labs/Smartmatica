package dev.moar.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.moar.MoarMod;
import dev.moar.chest.ChestManager;
import dev.moar.spawnproof.SpawnProofer;
import dev.moar.util.ChatHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Registers all /spawnproof client commands.
 *
 * /spawnproof pos1                    -- set corner 1 to current position
 * /spawnproof pos2                    -- set corner 2 to current position
 * /spawnproof pos1 x y z             -- set corner 1 to specific coords
 * /spawnproof pos2 x y z             -- set corner 2 to specific coords
 * /spawnproof lightsrc [block]        -- set the light source block to use
 * /spawnproof embed                   -- toggle embed-in-ground mode
 * /spawnproof start                   -- start spawnproofing
 * /spawnproof stop                    -- stop spawnproofing
 * /spawnproof pause                   -- pause spawnproofing
 * /spawnproof resume                  -- resume spawnproofing
 * /spawnproof status                  -- show current status
 * /spawnproof scan                    -- scan area and show dark spot count
 * /spawnproof supply add              -- add chest at crosshair as supply
 * /spawnproof supply remove           -- remove supply chest at crosshair
 * /spawnproof supply list             -- list all supply chests
 * /spawnproof chest add               -- add supply chest at crosshair (alias)
 * /spawnproof chest remove            -- remove supply chest at crosshair
 * /spawnproof chest list              -- list supply chests
 * /spawnproof chest clear             -- clear all supply chests
 */
public final class SpawnProofCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    private SpawnProofCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("spawnproof");

            // Root-level help — shown when user types /spawnproof
            // without a subcommand.  Also ensures Brigadier always
            // lists this command in tab-completion suggestions.
            root.executes(ctx -> {
                ChatHelper.labelled("SpawnProof", "§7Available subcommands:");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof pos1 §7[x y z] §8— set corner 1");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof pos2 §7[x y z] §8— set corner 2");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof lightsrc §7[block] §8— set light source");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof embed §8— toggle embed-in-ground mode");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof start §8— start spawnproofing");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof stop §8— stop spawnproofing");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof pause/resume §8— pause/resume");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof status §8— show current status");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof scan §8— scan area for dark spots");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof supply §8— manage supply chests");
                ChatHelper.labelled("SpawnProof", "  §f/spawnproof chest §8— manage supply chests (alias)");
                return 1;
            });

            // ── Area selection ────────────────────────────────────────

            // /spawnproof pos1
            root.then(ClientCommandManager.literal("pos1")
                    .executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null) return 0;
                        BlockPos pos = mc.player.getBlockPos();
                        getProofer().setCorner1(pos);
                        ChatHelper.labelled("SpawnProof", "§aCorner 1 set to §f"
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
                                                getProofer().setCorner1(pos);
                                                ChatHelper.labelled("SpawnProof",
                                                        "§aCorner 1 set to §f" + x + " " + y + " " + z);
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /spawnproof pos2
            root.then(ClientCommandManager.literal("pos2")
                    .executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null) return 0;
                        BlockPos pos = mc.player.getBlockPos();
                        getProofer().setCorner2(pos);
                        ChatHelper.labelled("SpawnProof", "§aCorner 2 set to §f"
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
                                                getProofer().setCorner2(pos);
                                                ChatHelper.labelled("SpawnProof",
                                                        "§aCorner 2 set to §f" + x + " " + y + " " + z);
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // ── Light source ─────────────────────────────────────────

            // /spawnproof lightsrc [block]
            root.then(ClientCommandManager.literal("lightsrc")
                    .executes(ctx -> {
                        ChatHelper.labelled("SpawnProof",
                                "§7Current light source: §f" + getProofer().getLightSourceName());
                        ChatHelper.labelled("SpawnProof", "§7Options: torch, lantern, glowstone, "
                                + "sea_lantern, shroomlight, jack_o_lantern, soul_torch, soul_lantern");
                        return 1;
                    })
                    .then(ClientCommandManager.argument("block", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (Block b : SpawnProofer.getKnownLightSources()) {
                                    builder.suggest(Registries.BLOCK.getId(b).getPath());
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String blockName = StringArgumentType.getString(ctx, "block");
                                // Try with minecraft: prefix
                                String fullId = blockName.contains(":") ? blockName : "minecraft:" + blockName;

                                if (getProofer().setLightSource(fullId)) {
                                    ChatHelper.labelled("SpawnProof",
                                            "§aLight source set to §f" + getProofer().getLightSourceName());
                                } else {
                                    ChatHelper.labelled("SpawnProof",
                                            "§cUnknown or non-luminous block: " + blockName);
                                }
                                return 1;
                            })
                    )
            );

            // /spawnproof embed
            root.then(ClientCommandManager.literal("embed")
                    .executes(ctx -> {
                        SpawnProofer proofer = getProofer();
                        if (!proofer.isEmbedInGround()) {
                            // Trying to turn ON — reject if light source is not a full block
                            if (!proofer.isFullBlockLightSource()) {
                                ChatHelper.labelled("SpawnProof",
                                        "§c" + proofer.getLightSourceName()
                                                + " cannot be embedded in the ground.");
                                ChatHelper.labelled("SpawnProof",
                                        "§cSwitch to a full block first: glowstone, sea_lantern, "
                                                + "shroomlight, froglight, etc.");
                                return 0;
                            }
                            proofer.setEmbedInGround(true);
                            ChatHelper.labelled("SpawnProof",
                                    "§aEmbed mode §fON§a — light sources will replace ground blocks.");
                        } else {
                            proofer.setEmbedInGround(false);
                            ChatHelper.labelled("SpawnProof",
                                    "§eEmbed mode §fOFF§e — light sources placed on top of surfaces.");
                        }
                        return 1;
                    })
            );

            // ── Control ─────────────────────────────────────────────

            // /spawnproof start
            root.then(ClientCommandManager.literal("start")
                    .executes(ctx -> {
                        getProofer().start();
                        return 1;
                    })
            );

            // /spawnproof stop
            root.then(ClientCommandManager.literal("stop")
                    .executes(ctx -> {
                        getProofer().stop();
                        return 1;
                    })
            );

            // /spawnproof pause
            root.then(ClientCommandManager.literal("pause")
                    .executes(ctx -> {
                        getProofer().pause();
                        return 1;
                    })
            );

            // /spawnproof resume
            root.then(ClientCommandManager.literal("resume")
                    .executes(ctx -> {
                        getProofer().resume();
                        return 1;
                    })
            );

            // /spawnproof status
            root.then(ClientCommandManager.literal("status")
                    .executes(ctx -> {
                        SpawnProofer proofer = getProofer();
                        ChatHelper.labelled("SpawnProof", "§7State: §f" + proofer.getStatus());

                        BlockPos c1 = proofer.getCorner1();
                        BlockPos c2 = proofer.getCorner2();
                        if (c1 != null) {
                            ChatHelper.labelled("SpawnProof",
                                    "§7Corner 1: §f" + c1.getX() + " " + c1.getY() + " " + c1.getZ());
                        }
                        if (c2 != null) {
                            ChatHelper.labelled("SpawnProof",
                                    "§7Corner 2: §f" + c2.getX() + " " + c2.getY() + " " + c2.getZ());
                        }
                        ChatHelper.labelled("SpawnProof",
                                "§7Light source: §f" + proofer.getLightSourceName());
                        ChatHelper.labelled("SpawnProof",
                                "§7Embed mode: §f" + (proofer.isEmbedInGround() ? "ON" : "OFF")
                                        + (proofer.isEmbedInGround() && !proofer.isFullBlockLightSource()
                                                ? " §7(inactive — not a full block)" : ""));
                        ChatHelper.labelled("SpawnProof",
                                "§7Dark spots: §f" + proofer.getDarkSpotCount());
                        ChatHelper.labelled("SpawnProof",
                                "§7Placed: §f" + proofer.getTotalPlaced());
                        return 1;
                    })
            );

            // /spawnproof scan (scan only, don't start placing)
            root.then(ClientCommandManager.literal("scan")
                    .executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.world == null || mc.player == null) return 0;

                        SpawnProofer proofer = getProofer();
                        BlockPos c1 = proofer.getCorner1();
                        BlockPos c2 = proofer.getCorner2();
                        if (c1 == null || c2 == null) {
                            ChatHelper.labelled("SpawnProof",
                                    "§cSet both corners first.");
                            return 0;
                        }

                        // Manual scan — count dark spots without starting
                        int minX = Math.min(c1.getX(), c2.getX());
                        int minY = Math.min(c1.getY(), c2.getY());
                        int minZ = Math.min(c1.getZ(), c2.getZ());
                        int maxX = Math.max(c1.getX(), c2.getX());
                        int maxY = Math.max(c1.getY(), c2.getY());
                        int maxZ = Math.max(c1.getZ(), c2.getZ());

                        int darkCount = 0;
                        int totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

                        for (int x = minX; x <= maxX; x++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                for (int y = minY; y <= maxY; y++) {
                                    BlockPos pos = new BlockPos(x, y, z);
                                    // Quick dark-spawnable check
                                    if (mc.world.getBlockState(pos).isSolidBlock(mc.world, pos)
                                            && mc.world.getBlockState(pos.up()).isAir()
                                            && mc.world.getLightLevel(
                                                    net.minecraft.world.LightType.BLOCK, pos.up()) == 0) {
                                        darkCount++;
                                    }
                                }
                            }
                        }

                        ChatHelper.labelled("SpawnProof",
                                "§7Scanned §f" + totalBlocks + "§7 blocks, found §f"
                                        + darkCount + "§7 dark spawnable spots.");
                        return 1;
                    })
            );

            // ── Supply chests (for light sources) ────────────────────

            root.then(ClientCommandManager.literal("supply")
                    .then(ClientCommandManager.literal("add")
                            .executes(ctx -> {
                                MinecraftClient mc = MinecraftClient.getInstance();
                                if (mc.player == null) return 0;
                                BlockPos pos = getTargetBlock(mc);
                                if (pos == null) {
                                    ChatHelper.labelled("SpawnProof",
                                            "§cLook at a chest block.");
                                    return 0;
                                }
                                getProofer().addSupplyChest(pos);
                                ChatHelper.labelled("SpawnProof",
                                        "§aSupply chest added at §f"
                                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                                return 1;
                            })
                    )
                    .then(ClientCommandManager.literal("remove")
                            .executes(ctx -> {
                                MinecraftClient mc = MinecraftClient.getInstance();
                                if (mc.player == null) return 0;
                                BlockPos pos = getTargetBlock(mc);
                                if (pos == null) {
                                    ChatHelper.labelled("SpawnProof",
                                            "§cLook at a chest block.");
                                    return 0;
                                }
                                getProofer().removeSupplyChest(pos);
                                ChatHelper.labelled("SpawnProof",
                                        "§eSupply chest removed.");
                                return 1;
                            })
                    )
                    .then(ClientCommandManager.literal("list")
                            .executes(ctx -> {
                                var chests = getProofer().getSupplyChests();
                                if (chests.isEmpty()) {
                                    ChatHelper.labelled("SpawnProof",
                                            "§7No supply chests configured.");
                                } else {
                                    ChatHelper.labelled("SpawnProof",
                                            "§7Supply chests (" + chests.size() + "):");
                                    for (BlockPos p : chests) {
                                        ChatHelper.labelled("SpawnProof",
                                                "  §f" + p.getX() + " " + p.getY() + " " + p.getZ());
                                    }
                                }
                                return 1;
                            })
                    )
            );

            // ── Storage chests → Supply chests for light sources ─────

            root.then(ClientCommandManager.literal("chest")
                    .executes(ctx -> {
                        ChatHelper.labelled("SpawnProof", "§7Chest subcommands:");
                        ChatHelper.labelled("SpawnProof", "  §f/spawnproof chest add §8— add supply chest at crosshair");
                        ChatHelper.labelled("SpawnProof", "  §f/spawnproof chest remove §8— remove supply chest");
                        ChatHelper.labelled("SpawnProof", "  §f/spawnproof chest list §8— list supply chests");
                        ChatHelper.labelled("SpawnProof", "  §f/spawnproof chest clear §8— clear all supply chests");
                        return 1;
                    })
                    .then(ClientCommandManager.literal("add")
                            .executes(ctx -> {
                                MinecraftClient mc = MinecraftClient.getInstance();
                                if (mc.player == null) return 0;
                                BlockPos pos = getTargetBlock(mc);
                                if (pos == null) {
                                    ChatHelper.labelled("SpawnProof",
                                            "§cLook at a chest block.");
                                    return 0;
                                }
                                getProofer().addSupplyChest(pos);
                                getChestManager().addSupplyChest(pos);
                                ChatHelper.labelled("SpawnProof",
                                        "§aSupply chest added at §f"
                                                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                                return 1;
                            })
                    )
                    .then(ClientCommandManager.literal("remove")
                            .executes(ctx -> {
                                MinecraftClient mc = MinecraftClient.getInstance();
                                if (mc.player == null) return 0;
                                BlockPos pos = getTargetBlock(mc);
                                if (pos == null) {
                                    ChatHelper.labelled("SpawnProof",
                                            "§cLook at a chest block.");
                                    return 0;
                                }
                                getProofer().removeSupplyChest(pos);
                                getChestManager().removeSupplyChest(pos);
                                ChatHelper.labelled("SpawnProof",
                                        "§eSupply chest removed.");
                                return 1;
                            })
                    )
                    .then(ClientCommandManager.literal("list")
                            .executes(ctx -> {
                                var chests = getProofer().getSupplyChests();
                                if (chests.isEmpty()) {
                                    ChatHelper.labelled("SpawnProof",
                                            "§7No supply chests configured.");
                                } else {
                                    ChatHelper.labelled("SpawnProof",
                                            "§7Supply chests (" + chests.size() + "):");
                                    for (BlockPos p : chests) {
                                        ChatHelper.labelled("SpawnProof",
                                                "  §f" + p.getX() + " " + p.getY() + " " + p.getZ());
                                    }
                                }
                                return 1;
                            })
                    )
                    .then(ClientCommandManager.literal("clear")
                            .executes(ctx -> {
                                getProofer().getSupplyChests().forEach(p -> {
                                    getProofer().removeSupplyChest(p);
                                    getChestManager().removeSupplyChest(p);
                                });
                                ChatHelper.labelled("SpawnProof",
                                        "§eAll supply chests cleared.");
                                return 1;
                            })
                    )
            );

            dispatcher.register(root);
            LOGGER.info("SpawnProofCommand: /spawnproof registered ({} subcommands)",
                    root.getArguments().size());
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static SpawnProofer getProofer() {
        return MoarMod.getSpawnProofer();
    }

    private static ChestManager getChestManager() {
        return MoarMod.getChestManager();
    }

    /**
     * Get the block position the player is looking at.
     */
    private static BlockPos getTargetBlock(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult blockHit) {
            if (blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                return blockHit.getBlockPos();
            }
        }
        return null;
    }
}
