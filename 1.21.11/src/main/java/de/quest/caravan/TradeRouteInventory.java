package de.quest.caravan;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/** Atomic material checks for interactive route-event repairs. */
final class TradeRouteInventory {
    private TradeRouteInventory() {}

    static boolean isPlank(ItemStack stack) {
        return stack.is(Items.OAK_PLANKS)
                || stack.is(Items.SPRUCE_PLANKS)
                || stack.is(Items.BIRCH_PLANKS)
                || stack.is(Items.JUNGLE_PLANKS)
                || stack.is(Items.ACACIA_PLANKS)
                || stack.is(Items.DARK_OAK_PLANKS)
                || stack.is(Items.MANGROVE_PLANKS)
                || stack.is(Items.CHERRY_PLANKS)
                || stack.is(Items.BAMBOO_PLANKS)
                || stack.is(Items.PALE_OAK_PLANKS);
    }

    static boolean consumePair(ServerPlayer player, Predicate<ItemStack> first, int firstAmount,
                               Predicate<ItemStack> second, int secondAmount) {
        if (count(player, first) < firstAmount || count(player, second) < secondAmount) {
            return false;
        }
        return consume(player, first, firstAmount) && consume(player, second, secondAmount);
    }

    static int count(ServerPlayer player, Predicate<ItemStack> matcher) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static boolean consume(ServerPlayer player, Predicate<ItemStack> matcher, int amount) {
        if (amount <= 0 || count(player, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.inventoryMenu.broadcastChanges();
        return remaining == 0;
    }
}
