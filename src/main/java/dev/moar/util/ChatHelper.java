package dev.moar.util;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.chat.MutableComponent;
*//*?} else {*/
import net.minecraft.text.MutableText;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.chat.Style;
*//*?} else {*/
import net.minecraft.text.Style;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.chat.Component;
*//*?} else {*/
import net.minecraft.text.Text;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.chat.TextColor;
*//*?} else {*/
import net.minecraft.text.TextColor;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.ChatFormatting;
*//*?} else {*/
import net.minecraft.util.Formatting;
/*?}*/

// Prefixed chat messages: [Printer] message or [Printer] [Label] message.
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
                /*? if >=26.1 {*//*
                .append(Component.literal(" "))
                *//*?} else {*/
                .append(Text.literal(" "))
                /*?}*/
                .append(tag(label, Style.EMPTY.withColor(TextColor.fromRgb(0xFFA500)))),
                message);
    }

    // internals

    /*? if >=26.1 {*//*
    private static void send(MutableComponent prefix, String message) {
    *//*?} else {*/
    private static void send(MutableText prefix, String message) {
    /*?}*/
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null || mc.player == null) return;

        /*? if >=26.1 {*//*
        MutableComponent full = prefix.copy()
        *//*?} else {*/
        MutableText full = prefix.copy()
        /*?}*/
                /*? if >=26.1 {*//*
                .append(Component.literal(" "))
                *//*?} else {*/
                .append(Text.literal(" "))
                /*?}*/
                /*? if >=26.1 {*//*
                .append(Component.literal(message)
                *//*?} else {*/
                .append(Text.literal(message)
                /*?}*/
                        /*? if >=26.1 {*//*
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
                        *//*?} else {*/
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                        /*?}*/

        /*? if >=26.1 {*//*
        mc.player.sendSystemMessage(full);
        *//*?} else {*/
        mc.player.sendMessage(full, false);
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private static MutableComponent prefix() {
    *//*?} else {*/
    private static MutableText prefix() {
    /*?}*/
        return tag(PREFIX_NAME, Style.EMPTY.withColor(TextColor.fromRgb(ACCENT_COLOR)));
    }

    /*? if >=26.1 {*//*
    private static MutableComponent tag(String label, Style labelStyle) {
    *//*?} else {*/
    private static MutableText tag(String label, Style labelStyle) {
    /*?}*/
        /*? if >=26.1 {*//*
        MutableComponent left  = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY);
        *//*?} else {*/
        MutableText left  = Text.literal("[").formatted(Formatting.DARK_GRAY);
        /*?}*/
        /*? if >=26.1 {*//*
        MutableComponent mid   = Component.literal(label).setStyle(labelStyle);
        *//*?} else {*/
        MutableText mid   = Text.literal(label).setStyle(labelStyle);
        /*?}*/
        /*? if >=26.1 {*//*
        MutableComponent right = Component.literal("]").withStyle(ChatFormatting.DARK_GRAY);
        *//*?} else {*/
        MutableText right = Text.literal("]").formatted(Formatting.DARK_GRAY);
        /*?}*/
        return left.append(mid).append(right);
    }
}
