package dev.moar;

import dev.moar.api.ApiServer;
import dev.moar.api.MoarProperties;
import dev.moar.world.SetbackMonitor;
import dev.moar.chest.ChestManager;
import dev.moar.command.PrinterCommand;
import dev.moar.command.SpawnProofCommand;
import dev.moar.command.StashCommand;
import dev.moar.stash.StashDatabase;
import dev.moar.stash.StashManager;
import dev.moar.printer.SchematicPrinter;
import dev.moar.schematic.PrinterResourceManager;
import dev.moar.spawnproof.SpawnProofer;
import dev.moar.util.PathWalker;
import dev.moar.util.PrinterDatabase;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*//*?} else {*/
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.KeyMapping;
*//*?} else {*/
import net.minecraft.client.option.KeyBinding;
/*?}*/
/*? if >=26.1 {*//*
import com.mojang.blaze3d.platform.InputConstants;
*//*?} else {*/
import net.minecraft.client.util.InputUtil;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.resources.Identifier;
*//*?} else if >=1.21.10 {*//*
import net.minecraft.util.Identifier;
*//*?}*/
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOAR — a standalone Fabric mod that automatically places
 * blocks from loaded .litematic schematics.
 */
public class MoarMod implements ClientModInitializer {

    public static final String MOD_ID = "moar";
    public static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    private static final StashDatabase DATABASE = new StashDatabase();
    private static final SchematicPrinter PRINTER = new SchematicPrinter();
    private static final SpawnProofer SPAWN_PROOFER = new SpawnProofer();
    private static final ChestManager CHEST_MANAGER = new ChestManager();
    private static final StashManager STASH_MANAGER = new StashManager();
    private static MoarProperties PROPERTIES;
    private static ApiServer API_SERVER;

    /*? if >=26.1 {*//*
    private static KeyMapping toggleKey;
    *//*?} else {*/
    private static KeyBinding toggleKey;
    /*?}*/

    @Override
    public void onInitializeClient() {
        LOGGER.info("MOAR initializing...");

        // Register keybinding to toggle the printer
        /*? if >=26.1 {*//*
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        *//*?} else {*/
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        /*?}*/
                "key.moar.toggle",
                /*? if >=26.1 {*//*
                InputConstants.Type.KEYSYM,
                *//*?} else {*/
                InputUtil.Type.KEYSYM,
                /*?}*/
                GLFW.GLFW_KEY_KP_0,
                /*? if >=26.1 {*//*
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("moar", "category"))
                *//*?} else if >=1.21.10 {*//*
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

        // Load properties and start API server if enabled
        PROPERTIES = MoarProperties.load();
        API_SERVER = new ApiServer(PROPERTIES);
        API_SERVER.start();

        // Register tick handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Setback monitor must tick first so other subsystems see
            // up-to-date isCalm() / ticksSinceSetback() this tick.
            SetbackMonitor.get().tick(client);

            // Toggle keybind check
            /*? if >=26.1 {*//*
            while (toggleKey.consumeClick()) {
            *//*?} else {*/
            while (toggleKey.wasPressed()) {
            /*?}*/
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

        // Restart API server when joining a server/world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (API_SERVER != null) API_SERVER.start();
        });

        // Clean up all state when leaving a server/world
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PRINTER.onDisconnect();
            STASH_MANAGER.stop();
            STASH_MANAGER.getOrganizer().stop();
            STASH_MANAGER.getRetriever().stop();
            SPAWN_PROOFER.stop();
            PathWalker.stop();
            SetbackMonitor.get().reset();
            PrinterDatabase.clearScaffold();
            DATABASE.close();
            if (API_SERVER != null) API_SERVER.close();
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

    /** Get the shared SQLite database. */
    public static StashDatabase getDatabase() {
        return DATABASE;
    }

    /** Get the loaded API/webhook properties. */
    public static MoarProperties getProperties() {
        return PROPERTIES;
    }

    /** Get the embedded API server. */
    public static ApiServer getApiServer() {
        return API_SERVER;
    }
}
