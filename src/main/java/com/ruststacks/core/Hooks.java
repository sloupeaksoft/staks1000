package com.ruststacks.core;

import net.minecraft.nbt.NBTTagCompound;

/** Вызывается из пропатченных классов Minecraft. */
public final class Hooks {

    private Hooks() {}

    /** ItemStack(NBT): читаем Count как int (работает и со старыми byte-тегами). */
    public static int readCount(NBTTagCompound nbt, String key) {
        return nbt.getInteger(key);
    }

    /** ItemStack.writeToNBT: маленькие стаки как раньше (byte), большие - int. */
    public static void writeCount(NBTTagCompound nbt, String key, int count) {
        if (count >= Byte.MIN_VALUE && count <= Byte.MAX_VALUE) nbt.setByte(key, (byte) count);
        else nbt.setInteger(key, count);
    }

    /** Лимит слотов (сундуки, инвентарь, печки, ящики MineRust...) вместо 64. */
    public static int slotLimit() {
        return RSConfig.slotLimit;
    }

    /** Креатив: ваниль отбрасывает стаки больше 64 - из-за этого предметы пропадали. */
    public static int creativeLimit() {
        return Integer.MAX_VALUE;
    }

    public static boolean patched() {
        return "ok".equals(System.getProperty("ruststacks.nbt")) && "ok".equals(System.getProperty("ruststacks.net"));
    }
}
