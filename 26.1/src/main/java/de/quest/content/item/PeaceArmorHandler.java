package de.quest.content.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class PeaceArmorHandler {
    private PeaceArmorHandler() {}

    public static int countPeacePieces(ServerPlayer player) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (isPeacePiece(stack, slot)) {
                count++;
            }
        }
        return count;
    }

    public static boolean tryNegateAttack(ServerPlayer player, Entity attacker) {
        if (!(attacker instanceof Monster)) {
            return false;
        }
        int pieces = countPeacePieces(player);
        if (pieces <= 0) {
            return false;
        }

        float chance = Math.min(1.0f, pieces * 0.25f);
        boolean negate = chance >= 1.0f || player.getRandom().nextFloat() < chance;
        if (negate) {
            if (attacker instanceof Mob mob && mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
        return negate;
    }

    private static boolean isPeacePiece(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
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

