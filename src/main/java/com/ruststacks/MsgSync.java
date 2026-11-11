package com.ruststacks;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/** Сервер -> клиент: размеры стаков, чтобы у игрока они были такими же, как на сервере. */
public class MsgSync implements IMessage {

    private Map<String, Integer> sizes = new HashMap<>();

    public MsgSync() {}

    public MsgSync(Map<String, Integer> sizes) {
        this.sizes = new HashMap<>(sizes);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(sizes.size());
        for (Map.Entry<String, Integer> en : sizes.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, en.getKey());
            buf.writeInt(en.getValue());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            String key = ByteBufUtils.readUTF8String(buf);
            sizes.put(key, buf.readInt());
        }
    }

    public static class Handler implements IMessageHandler<MsgSync, IMessage> {
        @Override
        public IMessage onMessage(MsgSync msg, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                int n = 0;
                for (Map.Entry<String, Integer> en : msg.sizes.entrySet()) {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(en.getKey()));
                    if (item == null) continue;
                    int size = en.getValue();
                    if (size > 64 && !com.ruststacks.core.Hooks.patched()) size = 64;
                    item.setMaxStackSize(size);
                    n++;
                }
                System.out.println("[RustStacks] synced " + n + " stack sizes from server");
            });
            return null;
        }
    }
}
