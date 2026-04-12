package dev.moar.util;

import net.minecraft.SharedConstants;
/*? if >=26.1 {*//*
import net.minecraft.nbt.CompoundTag;
*//*?} else {*/
import net.minecraft.nbt.NbtCompound;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.nbt.Tag;
*//*?} else {*/
import net.minecraft.nbt.NbtElement;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.nbt.ListTag;
*//*?} else {*/
import net.minecraft.nbt.NbtList;
/*?}*/

// Compatibility layer for NbtCompound access across MC versions.
// 1.21.5+ returns Optional from getters and changed contains() signature.
// This class provides a stable API using Stonecutter version predicates.
public final class NbtCompat {

    private NbtCompat() {}

    /** Check whether the compound contains a key of the given NBT element type. */
    /*? if >=26.1 {*//*
    public static boolean contains(CompoundTag nbt, String key, int type) {
    *//*?} else {*/
    public static boolean contains(NbtCompound nbt, String key, int type) {
    /*?}*/
        /*? if >=1.21.5 {*//*
        return nbt.contains(key);
        *//*?} else {*/
        return nbt.contains(key, type);
        /*?}*/
    }

    /** Get an int value, returning defaultValue if absent. */
    /*? if >=26.1 {*//*
    public static int getInt(CompoundTag nbt, String key, int defaultValue) {
    *//*?} else {*/
    public static int getInt(NbtCompound nbt, String key, int defaultValue) {
    /*?}*/
        /*? if >=1.21.5 {*//*
        return nbt.getInt(key).orElse(defaultValue);
        *//*?} else {*/
        return nbt.contains(key, NbtElement.INT_TYPE) ? nbt.getInt(key) : defaultValue;
        /*?}*/
    }

    /** Get an int value, returning 0 if absent. */
    /*? if >=26.1 {*//*
    public static int getInt(CompoundTag nbt, String key) {
    *//*?} else {*/
    public static int getInt(NbtCompound nbt, String key) {
    /*?}*/
        return getInt(nbt, key, 0);
    }

    /** Get a string value, returning defaultValue if absent. */
    /*? if >=26.1 {*//*
    public static String getString(CompoundTag nbt, String key, String defaultValue) {
    *//*?} else {*/
    public static String getString(NbtCompound nbt, String key, String defaultValue) {
    /*?}*/
        /*? if >=1.21.5 {*//*
        return nbt.getString(key).orElse(defaultValue);
        *//*?} else {*/
        return nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : defaultValue;
        /*?}*/
    }

    /** Get a string value, returning empty string if absent. */
    /*? if >=26.1 {*//*
    public static String getString(CompoundTag nbt, String key) {
    *//*?} else {*/
    public static String getString(NbtCompound nbt, String key) {
    /*?}*/
        return getString(nbt, key, "");
    }

    /** Get a sub-compound, returning a new empty compound if absent. */
    /*? if >=26.1 {*//*
    public static CompoundTag getCompound(CompoundTag nbt, String key) {
    *//*?} else {*/
    public static NbtCompound getCompound(NbtCompound nbt, String key) {
    /*?}*/
        /*? if >=26.1 {*//*
        return nbt.getCompound(key).orElse(new CompoundTag());
        *//*?} else if >=1.21.5 {*//*
        return nbt.getCompound(key).orElse(new NbtCompound());
        *//*?} else {*/
        return nbt.getCompound(key);
        /*?}*/
    }

    /** Get a long array, returning an empty array if absent. */
    /*? if >=26.1 {*//*
    public static long[] getLongArray(CompoundTag nbt, String key) {
    *//*?} else {*/
    public static long[] getLongArray(NbtCompound nbt, String key) {
    /*?}*/
        /*? if >=1.21.5 {*//*
        return nbt.getLongArray(key).orElse(new long[0]);
        *//*?} else {*/
        return nbt.getLongArray(key);
        /*?}*/
    }

    /** Get an NBT list of the specified element type. */
    /*? if >=26.1 {*//*
    public static ListTag getList(CompoundTag nbt, String key, int type) {
    *//*?} else {*/
    public static NbtList getList(NbtCompound nbt, String key, int type) {
    /*?}*/
        /*? if >=26.1 {*//*
        return nbt.getList(key).orElse(new ListTag());
        *//*?} else if >=1.21.5 {*//*
        return nbt.getList(key).orElse(new NbtList());
        *//*?} else {*/
        return nbt.getList(key, type);
        /*?}*/
    }

    // game version helpers

    /** Returns the data version (save version ID) of the current Minecraft build. */
    public static int currentDataVersion() {
        /*? if >=26.1 {*//*
        return SharedConstants.getCurrentVersion().dataVersion().version();
        *//*?} else if >=1.21.8 {*//*
        return SharedConstants.getGameVersion().dataVersion().id();
        *//*?} else {*/
        return SharedConstants.getGameVersion().getSaveVersion().getId();
        /*?}*/
    }
}
