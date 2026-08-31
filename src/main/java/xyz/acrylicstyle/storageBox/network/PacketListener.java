package xyz.acrylicstyle.storageBox.network;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import java.util.ArrayList;
import java.util.List;

public class PacketListener extends PacketListenerAbstract {

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
            ItemStack item = packet.getItem();
            ItemStack rewritten = rewriteItem(item);
            if (rewritten != null) {
                packet.setItem(rewritten);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
            List<ItemStack> items = packet.getItems();
            List<ItemStack> rewrittenItems = new ArrayList<>(items.size());
            boolean modified = false;
            for (ItemStack item : items) {
                ItemStack rewritten = rewriteItem(item);
                if (rewritten != null) {
                    rewrittenItems.add(rewritten);
                    modified = true;
                } else {
                    rewrittenItems.add(item);
                }
            }
            if (modified) {
                packet.setItems(rewrittenItems);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
            List<Equipment> equipmentList = packet.getEquipment();
            boolean modified = false;
            for (Equipment equipment : equipmentList) {
                ItemStack item = equipment.getItem();
                ItemStack rewritten = rewriteItem(item);
                if (rewritten != null) {
                    equipment.setItem(rewritten);
                    modified = true;
                }
            }
            if (modified) {
                packet.setEquipment(equipmentList);
            }
        }
    }

    private static ItemStack rewriteItem(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        NBTCompound tag = item.getNBT();
        if (tag == null || tag.getTagOrNull("storageBoxType") == null) return null;
        String typeStr = tag.getStringTagValueOrNull("storageBoxType");
        if (typeStr == null || typeStr.isEmpty() || typeStr.equalsIgnoreCase("null")) return null;

        try {
            NBTCompound rewrittenTag = tag.copy();
            NBTCompound storageBoxTag = rewrittenTag.getCompoundTagOrNull("storageBoxTag");
            if (storageBoxTag != null) {
                NBT customModelData = storageBoxTag.getTagOrNull("CustomModelData");
                if (customModelData != null) {
                    rewrittenTag.setTag("CustomModelData", customModelData.copy());
                }
            }
            ItemType itemType = ItemTypes.getByName("minecraft:" + typeStr.toLowerCase());
            if (itemType == null) {
                itemType = ItemTypes.getByName(typeStr.toLowerCase());
            }
            if (itemType == null || itemType == ItemTypes.AIR) {
                itemType = ItemTypes.BARRIER;
            }
            return ItemStack.builder()
                    .type(itemType)
                    .amount(item.getAmount())
                    .nbt(rewrittenTag)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
