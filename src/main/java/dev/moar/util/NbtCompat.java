package dev.moar.util;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Compatibility layer for {@link NbtCompound} access across Minecraft versions.
 *
 * Starting with Minecraft 1.21.5, many {@code NbtCompound} getters
 * return {@link java.util.Optional} instead of raw values, and
 * {@code contains(String, int)} was replaced with {@code contains(String)}.
 * This class provides a stable API that works on both old and new versions,
 * using Stonecutter version predicates internally.
 */
public final class NbtCompat {

    private NbtCompat() {}

    /** Check whether the compound contains a key of the given NBT element type. */
    public static boolean contains(NbtCompound nbt, String key, int type) {
        /*? if >=1.21.5 {*//*
        return nbt.contains(key);
        *//*?} else {*/
        return nbt.contains(key, type);
        /*?}*/
    }

    /** Get an int value, returning {@code defaultValue} if absent. */
    public static int getInt(NbtCompound nbt, String key, int defaultValue) {
        /*? if >=1.21.5 {*//*
        return nbt.getInt(key).orElse(defaultValue);
        *//*?} else {*/
        return nbt.contains(key, NbtElement.INT_TYPE) ? nbt.getInt(key) : defaultValue;
        /*?}*/
    }

    /** Get an int value, returning 0 if absent. */
    public static int getInt(NbtCompound nbt, String key) {
        return getInt(nbt, key, 0);
    }

    /** Get a string value, returning {@code defaultValue} if absent. */
    public static String getString(NbtCompound nbt, String key, String defaultValue) {
        /*? if >=1.21.5 {*//*
        return nbt.getString(key).orElse(defaultValue);
        *//*?} else {*/
        return nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : defaultValue;
        /*?}*/
    }

    /** Get a string value, returning empty string if absent. */
    public static String getString(NbtCompound nbt, String key) {
        return getString(nbt, key, "");
    }

    /** Get a sub-compound, returning a new empty compound if absent. */
    public static NbtCompound getCompound(NbtCompound nbt, String key) {
        /*? if >=1.21.5 {*//*
        return nbt.getCompound(key).orElse(new NbtCompound());
        *//*?} else {*/
        return nbt.getCompound(key);
        /*?}*/
    }

    /** Get a long array, returning an empty array if absent. */
    public static long[] getLongArray(NbtCompound nbt, String key) {
        /*? if >=1.21.5 {*//*
        return nbt.getLongArray(key).orElse(new long[0]);
        *//*?} else {*/
        return nbt.getLongArray(key);
        /*?}*/
    }

    /** Get an NBT list of the specified element type. */
    public static NbtList getList(NbtCompound nbt, String key, int type) {
        /*? if >=1.21.5 {*//*
        return nbt.getList(key).orElse(new NbtList());
        *//*?} else {*/
        return nbt.getList(key, type);
        /*?}*/
    }

    // ── game version helpers ────────────────────────────────────────────

    /** Returns the data version (save version ID) of the current Minecraft build. */
    public static int currentDataVersion() {
        /*? if >=1.21.8 {*//*
        return SharedConstants.getGameVersion().dataVersion().id();
        *//*?} else {*/
        return SharedConstants.getGameVersion().getSaveVersion().getId();
        /*?}*/
    }
}
