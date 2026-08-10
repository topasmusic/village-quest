package de.quest.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Thin Yarn 1.21.1 adapters for NBT reads that newer MC lines express with
 * default-arg / OrEmpty helpers. Keeps saved keys and default semantics stable.
 */
public final class NbtCompat {
    private NbtCompat() {}

    public static NbtCompound compound(NbtCompound root, String key) {
        return root.getCompound(key);
    }

    public static NbtList compoundList(NbtCompound root, String key) {
        return root.getList(key, NbtElement.COMPOUND_TYPE);
    }

    public static NbtCompound compoundAt(NbtList list, int index) {
        return list.getCompound(index);
    }

    public static String getString(NbtCompound nbt, String key, String defaultValue) {
        return nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : defaultValue;
    }

    public static long getLong(NbtCompound nbt, String key, long defaultValue) {
        return nbt.contains(key) ? nbt.getLong(key) : defaultValue;
    }

    public static int getInt(NbtCompound nbt, String key, int defaultValue) {
        return nbt.contains(key) ? nbt.getInt(key) : defaultValue;
    }

    public static boolean getBoolean(NbtCompound nbt, String key, boolean defaultValue) {
        return nbt.contains(key) ? nbt.getBoolean(key) : defaultValue;
    }
}
