package xyz.acrylicstyle.storageBox.network;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
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
            // the item on the cursor is sent with the window's items
            packet.getCarriedItem().ifPresent(carried -> {
                ItemStack rewritten = rewriteItem(carried);
                if (rewritten != null) packet.setCarriedItem(rewritten);
            });
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            // 1.21.2+: slots of the player's own inventory (e.g. set by plugins while no container is open)
            WrapperPlayServerSetPlayerInventory packet = new WrapperPlayServerSetPlayerInventory(event);
            ItemStack rewritten = rewriteItem(packet.getStack());
            if (rewritten != null) packet.setStack(rewritten);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_CURSOR_ITEM) {
            // 1.21.2+: the item on the cursor
            WrapperPlayServerSetCursorItem packet = new WrapperPlayServerSetCursorItem(event);
            ItemStack rewritten = rewriteItem(packet.getStack());
            if (rewritten != null) packet.setStack(rewritten);
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
        if (tag == null) {
            try {
                tag = item.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
            } catch (Throwable ignored) {}
        }
        if (tag == null) return null;

        String typeStr = tag.getStringTagValueOrNull("storageBoxType");
        NBTCompound storageBoxTag = tag.getCompoundTagOrNull("storageBoxTag");

        if (typeStr == null) {
            NBTCompound customData = tag.getCompoundTagOrNull("minecraft:custom_data");
            if (customData == null) customData = tag.getCompoundTagOrNull("custom_data");
            if (customData != null) {
                typeStr = customData.getStringTagValueOrNull("storageBoxType");
                if (storageBoxTag == null) {
                    storageBoxTag = customData.getCompoundTagOrNull("storageBoxTag");
                }
            }
        }
        if (typeStr == null) {
            NBTCompound pbv = tag.getCompoundTagOrNull("PublicBukkitValues");
            if (pbv == null) {
                NBTCompound customData = tag.getCompoundTagOrNull("minecraft:custom_data");
                if (customData != null) pbv = customData.getCompoundTagOrNull("PublicBukkitValues");
            }
            if (pbv != null) {
                typeStr = pbv.getStringTagValueOrNull("storagebox:type");
            }
        }

        if (typeStr == null || typeStr.isEmpty() || typeStr.equalsIgnoreCase("null")) return null;

        try {
            NBTCompound rewrittenTag = tag.copy();
            if (storageBoxTag == null) {
                storageBoxTag = rewrittenTag.getCompoundTagOrNull("storageBoxTag");
            }
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

            ItemStack.Builder builder = ItemStack.builder()
                    .type(itemType)
                    .amount(item.getAmount())
                    .nbt(rewrittenTag)
                    .legacyData(item.getLegacyData());

            if (item.getComponents() != null) {
                builder.components(item.getComponents().copy());
            }
            try {
                builder.component(ComponentTypes.CUSTOM_DATA, rewrittenTag);
            } catch (Throwable ignored) {}
            // 1.20.5+ clients take the model from the custom_model_data component, not from the item's NBT.
            // Boxes saved on 1.15.2 only have the content's model in storageBoxTag; boxes written since carry
            // the component themselves (StorageBox#getItemStack), which is left as is.
            if (storageBoxTag != null && storageBoxTag.getTagOrNull("CustomModelData") instanceof NBTNumber) {
                try {
                    if (item.getComponentOr(ComponentTypes.CUSTOM_MODEL_DATA_LISTS, null) == null) {
                        builder.component(ComponentTypes.CUSTOM_MODEL_DATA,
                                ((NBTNumber) storageBoxTag.getTagOrNull("CustomModelData")).getAsInt());
                    }
                } catch (Throwable ignored) {}
            }

            return builder.build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
