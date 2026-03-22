package de.quest.content.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PeaceArmorHandler {
    private PeaceArmorHandler() {}

    public static int countPeacePieces(ServerPlayerEntity player) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getEquippedStack(slot);
            if (isPeacePiece(stack, slot)) {
                count++;
            }
        }
        return count;
    }

    public static boolean tryNegateAttack(ServerPlayerEntity player, Entity attacker) {
        if (!(attacker instanceof HostileEntity)) {
            return false;
        }
        int pieces = countPeacePieces(player);
        if (pieces <= 0) {
            return false;
        }

        float chance = Math.min(1.0f, pieces * 0.25f);
        boolean negate = chance >= 1.0f || player.getRandom().nextFloat() < chance;
        if (negate) {
            if (attacker instanceof MobEntity mob && mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
        return negate;
    }

    private static boolean isPeacePiece(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return false;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().stream().noneMatch(text -> text.getString().contains("Frieden"))) {
            return false;
        }

        Item item = stack.getItem();
        return switch (slot) {
            case HEAD -> item == Items.LEATHER_HELMET;
            case CHEST -> item == Items.LEATHER_CHESTPLATE;
            case LEGS -> item == Items.LEATHER_LEGGINGS;
            case FEET -> item == Items.LEATHER_BOOTS;
            default -> false;
        };
    }
}

