package de.quest.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class QuestMapHelper {
    private static final String TAG_QUEST_MAP = "village_quest_map";

    private QuestMapHelper() {}

    public static void tag(ItemStack stack, String questId) {
        if (stack == null || stack.isEmpty() || questId == null || questId.isBlank()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_QUEST_MAP, questId));
    }

    public static boolean isTagged(ItemStack stack, String questId) {
        if (stack == null || stack.isEmpty() || questId == null) return false;
        CustomData custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return questId.equals(custom.copyTag().getStringOr(TAG_QUEST_MAP, ""));
    }

    public static int removeTaggedMaps(ServerPlayer player, String questId) {
        if (player == null) return 0;
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isTagged(stack, questId)) continue;
            removed += stack.getCount();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        if (removed > 0) player.inventoryMenu.broadcastChanges();
        return removed;
    }
}
