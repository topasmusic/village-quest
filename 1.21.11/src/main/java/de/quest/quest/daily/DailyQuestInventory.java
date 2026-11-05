package de.quest.quest.daily;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared inventory and statistic operations used by daily quest definitions. */
final class DailyQuestInventory {
    private DailyQuestInventory() {}

    static int count(Player player, Item item) {
        if (player == null || item == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static int count(Player player, Item... items) {
        if (player == null || items == null || items.length == 0) {
            return 0;
        }
        int total = 0;
        for (Item item : items) {
            total += count(player, item);
        }
        return total;
    }

    static boolean consume(Player player, Item item, int amount) {
        if (player == null || item == null || amount <= 0 || count(player, item) < amount) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        return remaining <= 0;
    }

    static boolean consume(Player player, int amount, Item... items) {
        if (player == null || amount <= 0 || items == null || items.length == 0
                || count(player, items) < amount) {
            return false;
        }
        int remaining = amount;
        for (Item item : items) {
            if (remaining <= 0) {
                break;
            }
            int available = count(player, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!consume(player, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    static int crafted(ServerPlayer player, Item item) {
        return player.getStats().getValue(Stats.ITEM_CRAFTED.get(item));
    }

    static int pickedUp(ServerPlayer player, Item item) {
        return player.getStats().getValue(Stats.ITEM_PICKED_UP.get(item));
    }

    static int custom(ServerPlayer player, Identifier stat) {
        return player.getStats().getValue(Stats.CUSTOM.get(stat));
    }

    static int sumPickedUp(ServerPlayer player, Item... items) {
        int total = 0;
        for (Item item : items) {
            total += pickedUp(player, item);
        }
        return total;
    }

    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.daily.inventory_full.prefix")
                    .withStyle(ChatFormatting.GRAY)
                    .append(stack.getDisplayName())
                    .append(Component.translatable("message.village-quest.daily.inventory_full.suffix")
                            .withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.inventoryMenu.broadcastChanges();
        }
    }
}
