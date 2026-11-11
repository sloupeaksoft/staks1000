package com.ruststacks.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;

/**
 * Патчи:
 *  1. ItemStack: Count в NBT хранится как int (иначе >127 обрезается при сохранении).
 *  2. PacketBuffer: количество в пакетах как int (иначе >127 ломается при открытии инвентаря/сундука).
 *  3. NetHandlerPlayServer: креатив больше не удаляет стаки больше 64.
 *  4. getInventoryStackLimit / getSlotLimit / getSlotStackLimit "return 64" -> лимит из конфига.
 */
public class RustTransformer implements IClassTransformer, Opcodes {

    private static final String HOOKS = "com/ruststacks/core/Hooks";
    private static final String NBT = "net/minecraft/nbt/NBTTagCompound";

    private static final byte[][] LIMIT_NAMES = {
        b("func_70297_j_"), b("getInventoryStackLimit"),
        b("getSlotLimit"),
        b("func_75219_a"), b("getSlotStackLimit")
    };

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null) return bytes;
        try {
            if (transformedName.equals("net.minecraft.item.ItemStack")) return patchItemStack(bytes);
            if (transformedName.equals("net.minecraft.network.PacketBuffer")) return patchPacketBuffer(bytes);
            if (transformedName.equals("net.minecraft.network.NetHandlerPlayServer")) return patchCreative(bytes);
            if (transformedName.startsWith("java.") || transformedName.startsWith("javax.")
                || transformedName.startsWith("sun.") || transformedName.startsWith("org.objectweb.")
                || transformedName.startsWith("com.ruststacks.") || transformedName.startsWith("scala.")
                || transformedName.startsWith("kotlin.") || transformedName.startsWith("org.lwjgl.")
                || transformedName.startsWith("com.google.") || transformedName.startsWith("org.apache.")
                || transformedName.startsWith("io.netty.")) {
                return bytes;
            }
            for (byte[] n : LIMIT_NAMES) {
                if (contains(bytes, n)) return patchLimits(bytes, transformedName);
            }
        } catch (Throwable t) {
            System.err.println("[RustStacks] failed to transform " + transformedName + ": " + t);
        }
        return bytes;
    }

    // ------------------------------------------------------------------ utils

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode n) {
        AbstractInsnNode p = n.getPrevious();
        while (p != null && p.getOpcode() < 0) p = p.getPrevious();
        return p;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        AbstractInsnNode p = n.getNext();
        while (p != null && p.getOpcode() < 0) p = p.getNext();
        return p;
    }

    /** Есть ли LDC "Count" среди нескольких инструкций перед вызовом. */
    private static boolean countLdcBefore(AbstractInsnNode n, int depth) {
        AbstractInsnNode p = n;
        for (int i = 0; i < depth && p != null; i++) {
            p = prevReal(p);
            if (p instanceof LdcInsnNode && "Count".equals(((LdcInsnNode) p).cst)) return true;
        }
        return false;
    }

    private static boolean contains(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------- ItemStack

    private byte[] patchItemStack(byte[] bytes) {
        ClassNode node = read(bytes);
        int reads = 0, writes = 0;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() != INVOKEVIRTUAL) continue;
                MethodInsnNode mi = (MethodInsnNode) insn;
                if (!NBT.equals(mi.owner)) continue;
                if ("(Ljava/lang/String;)B".equals(mi.desc) && countLdcBefore(mi, 1)) {
                    // compound.getByte("Count") -> Hooks.readCount(compound, "Count")
                    m.instructions.set(mi, new MethodInsnNode(INVOKESTATIC, HOOKS, "readCount",
                        "(L" + NBT + ";Ljava/lang/String;)I", false));
                    reads++;
                } else if ("(Ljava/lang/String;B)V".equals(mi.desc) && countLdcBefore(mi, 6)) {
                    // compound.setByte("Count", (byte) stackSize) -> Hooks.writeCount(compound, "Count", stackSize)
                    AbstractInsnNode prev = prevReal(mi);
                    if (prev != null && prev.getOpcode() == I2B) m.instructions.remove(prev);
                    m.instructions.set(mi, new MethodInsnNode(INVOKESTATIC, HOOKS, "writeCount",
                        "(L" + NBT + ";Ljava/lang/String;I)V", false));
                    writes++;
                }
            }
        }
        System.out.println("[RustStacks] ItemStack patched: reads=" + reads + " writes=" + writes);
        if (reads > 0 && writes > 0) System.setProperty("ruststacks.nbt", "ok");
        return (reads + writes) > 0 ? write(node) : bytes;
    }

    // ----------------------------------------------------------- PacketBuffer

    private byte[] patchPacketBuffer(byte[] bytes) {
        ClassNode node = read(bytes);
        int w = 0, r = 0;
        for (MethodNode m : node.methods) {
            if ("(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/network/PacketBuffer;".equals(m.desc)) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() == INVOKEVIRTUAL) {
                        MethodInsnNode mi = (MethodInsnNode) insn;
                        if ("writeByte".equals(mi.name) && "(I)Lio/netty/buffer/ByteBuf;".equals(mi.desc)) {
                            mi.name = "writeInt";
                            w++;
                            break;
                        }
                    }
                }
            } else if ("()Lnet/minecraft/item/ItemStack;".equals(m.desc)) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() == INVOKEVIRTUAL) {
                        MethodInsnNode mi = (MethodInsnNode) insn;
                        if ("readByte".equals(mi.name) && "()B".equals(mi.desc)) {
                            mi.name = "readInt";
                            mi.desc = "()I";
                            r++;
                            break;
                        }
                    }
                }
            }
        }
        System.out.println("[RustStacks] PacketBuffer patched: write=" + w + " read=" + r);
        if (w > 0 && r > 0) System.setProperty("ruststacks.net", "ok");
        return (w + r) > 0 ? write(node) : bytes;
    }

    // --------------------------------------------------------------- creative

    private byte[] patchCreative(byte[] bytes) {
        ClassNode node = read(bytes);
        int n = 0;
        for (MethodNode m : node.methods) {
            if (!"(Lnet/minecraft/network/play/client/CPacketCreativeInventoryAction;)V".equals(m.desc)) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() == BIPUSH && ((IntInsnNode) insn).operand == 64) {
                    m.instructions.set(insn, new MethodInsnNode(INVOKESTATIC, HOOKS, "creativeLimit", "()I", false));
                    n++;
                }
            }
        }
        System.out.println("[RustStacks] creative limit patched: " + n);
        if (n > 0) System.setProperty("ruststacks.creative", "ok");
        return n > 0 ? write(node) : bytes;
    }

    // ----------------------------------------------------------------- limits

    private static boolean isLimitMethod(MethodNode m) {
        if ("()I".equals(m.desc)) {
            return m.name.equals("func_70297_j_") || m.name.equals("getInventoryStackLimit")
                || m.name.equals("func_75219_a") || m.name.equals("getSlotStackLimit");
        }
        return "(I)I".equals(m.desc) && m.name.equals("getSlotLimit");
    }

    private byte[] patchLimits(byte[] bytes, String cls) {
        ClassNode node = read(bytes);
        int n = 0;
        for (MethodNode m : node.methods) {
            if ((m.access & ACC_ABSTRACT) != 0 || !isLimitMethod(m)) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() == BIPUSH && ((IntInsnNode) insn).operand == 64) {
                    AbstractInsnNode next = nextReal(insn);
                    if (next != null && next.getOpcode() == IRETURN) {
                        m.instructions.set(insn, new MethodInsnNode(INVOKESTATIC, HOOKS, "slotLimit", "()I", false));
                        n++;
                    }
                }
            }
        }
        if (n == 0) return bytes;
        return write(node);
    }
}
