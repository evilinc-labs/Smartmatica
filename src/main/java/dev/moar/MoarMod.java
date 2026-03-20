package dev.moar;

import dev.moar.chest.ChestManager;
import dev.moar.command.PrinterCommand;
import dev.moar.command.SpawnProofCommand;
import dev.moar.command.StashCommand;
import dev.moar.stash.StashManager;
import dev.moar.printer.SchematicPrinter;
import dev.moar.schematic.PrinterResourceManager;
import dev.moar.spawnproof.SpawnProofer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
/*? if >=1.21.10 {*//*
import net.minecraft.util.Identifier;
*//*?}*/
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOAR — a standalone Fabric mod that automatically places
 * blocks from loaded {@code .litematic} schematics.
 */
public class MoarMod implements ClientModInitializer {

    public static final String MOD_ID = "moar";
    public static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    private static final SchematicPrinter PRINTER = new SchematicPrinter();
    private static final SpawnProofer SPAWN_PROOFER = new SpawnProofer();
    private static final ChestManager CHEST_MANAGER = new ChestManager();
    private static final StashManager STASH_MANAGER = new StashManager();

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("MOAR initializing...");

        // Register keybinding to toggle the printer
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moar.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0,
                /*? if >=1.21.10 {*//*
                KeyBinding.Category.create(Identifier.of("moar", "category"))
                *//*?} else {*/
                "category.moar"
                /*?}*/
        ));

        // Register client commands
        PrinterCommand.register();
        SpawnProofCommand.register();
        StashCommand.register();

        // Load saved supply chest positions
        PrinterResourceManager.load();

        // Register tick handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Toggle keybind check
            while (toggleKey.wasPressed()) {
                PRINTER.toggle();
            }

            // Tick the printer
            PRINTER.tick();

            // Tick the spawnproofer
            SPAWN_PROOFER.tick();

            // Tick the chest manager (sorting state machine)
            CHEST_MANAGER.tick();

            // Tick the stash manager
            STASH_MANAGER.tick();
        });

        LOGGER.info("MOAR initialized.");
    }

    /** Get the singleton printer instance. */
    public static SchematicPrinter getPrinter() {
        return PRINTER;
    }

    /** Get the singleton spawnproofer instance. */
    public static SpawnProofer getSpawnProofer() {
        return SPAWN_PROOFER;
    }

    /** Get the singleton chest manager instance. */
    public static ChestManager getChestManager() {
        return CHEST_MANAGER;
    }

    /** Get the singleton stash manager instance. */
    public static StashManager getStashManager() {
        return STASH_MANAGER;
    }
}
