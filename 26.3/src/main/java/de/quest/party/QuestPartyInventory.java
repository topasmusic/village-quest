package de.quest.party;

import de.quest.quest.daily.DailyQuestService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/** Inventory aggregation for shared party turn-ins. */
final class QuestPartyInventory {
    private QuestPartyInventory() {}

    static int countItem(List<ServerPlayer> members, Item item) {
        if (members == null || item == null) {
            return 0;
        }
        int total = 0;
        for (ServerPlayer member : members) {
            total += DailyQuestService.countInventoryItem(member, item);
        }
        return total;
    }

    static boolean consumeItem(List<ServerPlayer> members, Item item, int amount) {
        if (members == null || item == null || amount <= 0 || countItem(members, item) < amount) {
            return false;
        }
        int remaining = amount;
        for (ServerPlayer member : members) {
            if (remaining <= 0) {
                break;
            }
            int available = DailyQuestService.countInventoryItem(member, item);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!DailyQuestService.consumeInventoryItem(member, item, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    static int countMatching(List<ServerPlayer> members, Predicate<ItemStack> matcher) {
        if (members == null || matcher == null) {
            return 0;
        }
        int total = 0;
        for (ServerPlayer member : members) {
            total += countMatching(member, matcher);
        }
        return total;
    }

    static boolean consumeMatching(List<ServerPlayer> members, Predicate<ItemStack> matcher, int amount) {
        if (members == null || matcher == null || amount <= 0 || countMatching(members, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        for (ServerPlayer member : members) {
            if (remaining <= 0) {
                break;
            }
            int available = countMatching(member, matcher);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!consumeMatching(member, matcher, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }

    private static int countMatching(ServerPlayer player, Predicate<ItemStack> matcher) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consumeMatching(ServerPlayer player, Predicate<ItemStack> matcher, int amount) {
        if (player == null || amount <= 0 || countMatching(player, matcher) < amount) {
            return false;
        }
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.inventoryMenu.broadcastChanges();
        return remaining <= 0;
    }
}
