package com.ruststacks;

import com.ruststacks.core.Hooks;
import com.ruststacks.core.RSConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Mod(modid = RustStacks.MODID, name = "Rust Stacks", version = "3.0", acceptedMinecraftVersions = "[1.12.2]")
public class RustStacks {

    public static final String MODID = "ruststacks";

    public static SimpleNetworkWrapper NET;
    /** Размеры, которые реально применены (их сервер отправляет игрокам). */
    public static final Map<String, Integer> APPLIED = new HashMap<>();
    public static String status = "not started";
    public static boolean ok;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NET.registerMessage(MsgSync.Handler.class, MsgSync.class, 0, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent e) {
        try {
            ok = Hooks.patched();
            int changed = 0, capped = 0;
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation rl = item.getRegistryName();
                if (rl == null) continue;
                String key = rl.toString().toLowerCase(Locale.ROOT);
                Integer size = RSConfig.SIZES.get(key);
                if (size == null) {
                    if (RSConfig.defaultSize != 64 && item.getItemStackLimit() == 64) size = RSConfig.defaultSize;
                    else continue;
                }
                if (item.isDamageable()) continue; // инструменты/оружие с прочностью не стакаем
                if (size > 64 && !ok) {
                    size = 64;
                    capped++;
                }
                item.setMaxStackSize(size);
                APPLIED.put(key, size);
                changed++;
            }
            status = ok ? "OK" : "ПАТЧИ НЕ ПРИМЕНИЛИСЬ (nbt=" + System.getProperty("ruststacks.nbt")
                + ", net=" + System.getProperty("ruststacks.net") + "), стаки >64 отключены";
            status += ", изменено предметов: " + changed + (capped > 0 ? ", урезано до 64: " + capped : "")
                + ", креатив-фикс: " + ("ok".equals(System.getProperty("ruststacks.creative")) ? "ok" : "нет");
        } catch (Throwable t) {
            status = "ошибка: " + t;
        }
        System.out.println("[RustStacks] " + status + " | " + RSConfig.status);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) e.player;
        NET.sendTo(new MsgSync(APPLIED), player);
        if (!ok) {
            player.sendMessage(new TextComponentString("§c[Rust Stacks] " + status));
        }
    }
}
