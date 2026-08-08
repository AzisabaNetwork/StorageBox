package xyz.acrylicstyle.storageBox.network;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.v1_15_R1.*;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftItemStack;

import java.lang.reflect.Field;
import java.util.List;

public class PacketListener extends ChannelDuplexHandler {
    private final EntityPlayer player;

    public PacketListener(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof PacketPlayInBlockPlace && ((PacketPlayInBlockPlace) msg).b() == net.minecraft.server.v1_15_R1.EnumHand.MAIN_HAND) {
            ItemStack stack = player.b(((PacketPlayInBlockPlace) msg).b());
            NBTTagCompound tag = stack.getTag();
            if (tag != null && tag.hasKey("storageBoxType")) {
                // restore item in the hand
                //ctx.write(new PacketPlayOutSetSlot(0, player.inventory.itemInHandIndex, player.inventory.getItemInHand()));
            }
        }
        super.channelRead(ctx, msg);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof PacketPlayOutWindowItems) {
            msg = rewriteWindowItems((PacketPlayOutWindowItems) msg);
        } else if (msg instanceof PacketPlayOutEntityEquipment) {
            msg = rewriteEntityEquipment((PacketPlayOutEntityEquipment) msg);
        } else if (msg instanceof PacketPlayOutSetSlot) {
            msg = rewriteSetSlot((PacketPlayOutSetSlot) msg);
        }
        super.write(ctx, msg, promise);
    }

    @SuppressWarnings("unchecked")
    private static PacketPlayOutWindowItems rewriteWindowItems(PacketPlayOutWindowItems packet) throws ReflectiveOperationException {
        Field itemsField = getField(PacketPlayOutWindowItems.class, "b");
        List<ItemStack> items = (List<ItemStack>) itemsField.get(packet);
        NonNullList<ItemStack> rewrittenItems = NonNullList.a(items.size(), ItemStack.a);
        boolean rewritten = false;
        for (int i = 0; i < items.size(); i++) {
            ItemStack original = items.get(i);
            ItemStack replacement = rewriteItem(original);
            rewrittenItems.set(i, replacement);
            rewritten |= replacement != original;
        }
        if (!rewritten) return packet;

        Field windowIdField = getField(PacketPlayOutWindowItems.class, "a");
        return new PacketPlayOutWindowItems(windowIdField.getInt(packet), rewrittenItems);
    }

    private static PacketPlayOutEntityEquipment rewriteEntityEquipment(PacketPlayOutEntityEquipment packet) throws ReflectiveOperationException {
        Field itemField = getField(PacketPlayOutEntityEquipment.class, "c");
        ItemStack original = (ItemStack) itemField.get(packet);
        ItemStack replacement = rewriteItem(original);
        if (replacement == original) return packet;

        Field entityIdField = getField(PacketPlayOutEntityEquipment.class, "a");
        Field slotField = getField(PacketPlayOutEntityEquipment.class, "b");
        return new PacketPlayOutEntityEquipment(
                entityIdField.getInt(packet),
                (EnumItemSlot) slotField.get(packet),
                replacement
        );
    }

    private static PacketPlayOutSetSlot rewriteSetSlot(PacketPlayOutSetSlot packet) throws ReflectiveOperationException {
        Field itemField = getField(PacketPlayOutSetSlot.class, "c");
        ItemStack original = (ItemStack) itemField.get(packet);
        ItemStack replacement = rewriteItem(original);
        if (replacement == original) return packet;

        Field windowIdField = getField(PacketPlayOutSetSlot.class, "a");
        Field slotField = getField(PacketPlayOutSetSlot.class, "b");
        return new PacketPlayOutSetSlot(
                windowIdField.getInt(packet),
                slotField.getInt(packet),
                replacement
        );
    }

    private static Field getField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("deprecation")
    private static ItemStack rewriteItem(ItemStack item) {
        if (item == null) return null;
        NBTTagCompound tag = item.getTag();
        if (tag == null) return item;
        try {
            if (!tag.hasKey("storageBoxType") ||
                    tag.getString("storageBoxType").isEmpty() ||
                    tag.getString("storageBoxType").equals("null")) {
                return item;
            }
            ItemStack rewritten = item.cloneItemStack();
            NBTTagCompound rewrittenTag = rewritten.getTag();
            if (rewrittenTag.hasKey("storageBoxTag") && rewrittenTag.getCompound("storageBoxTag").hasKey("CustomModelData")) {
                rewrittenTag.setInt("CustomModelData", rewrittenTag.getCompound("storageBoxTag").getInt("CustomModelData"));
            }
            Material material = Material.valueOf(rewrittenTag.getString("storageBoxType"));
            if (material == Material.AIR) material = Material.BARRIER;
            rewritten.setItem(CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material)).getItem());
            return rewritten;
        } catch (Exception e) {
            e.printStackTrace();
            return item;
        }
    }
}
