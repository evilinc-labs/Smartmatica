package dev.moar.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Simple chat helper — sends prefixed messages to the player's chat HUD.
 *
 * Format: {@code [Printer] message} or {@code [Printer] [Label] message}.
 */
public final class ChatHelper {

    private static final String PREFIX_NAME = "Printer";
    private static final int ACCENT_COLOR = 0x55FFFF; // cyan

    private ChatHelper() {}

    /** Send a message: [Printer] message */
    public static void info(String message) {
        send(prefix(), message);
    }

    /** Send a labelled message: [Printer] [label] message */
    public static void labelled(String label, String message) {
        send(prefix()
                .append(Text.literal(" "))
                .append(tag(label, Style.EMPTY.withColor(TextColor.fromRgb(0xFFA500)))),
                message);
    }

    // ── internals ───────────────────────────────────────────────────────

    private static void send(MutableText prefix, String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        MutableText full = prefix.copy()
                .append(Text.literal(" "))
                .append(Text.literal(message)
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));

        mc.player.sendMessage(full, false);
    }

    private static MutableText prefix() {
        return tag(PREFIX_NAME, Style.EMPTY.withColor(TextColor.fromRgb(ACCENT_COLOR)));
    }

    private static MutableText tag(String label, Style labelStyle) {
        MutableText left  = Text.literal("[").formatted(Formatting.DARK_GRAY);
        MutableText mid   = Text.literal(label).setStyle(labelStyle);
        MutableText right = Text.literal("]").formatted(Formatting.DARK_GRAY);
        return left.append(mid).append(right);
    }
}
