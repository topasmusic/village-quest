package de.quest.client.screen;

import de.quest.economy.CurrencyService;
import de.quest.network.RewardPayloads;
import java.util.*;
import net.minecraft.world.item.ItemStack;

/** Display totals only: native component-bearing stacks remain untouched. */
final class RewardPreview {
    record Entry(ItemStack stack, long count, String currency) { }
    static List<Entry> rows(RewardPayloads.Choice choice) {
        var rows = new ArrayList<Entry>();
        long crowns = choice.currency() / CurrencyService.CROWN, silver = choice.currency() % CurrencyService.CROWN;
        if (crowns > 0) rows.add(new Entry(ItemStack.EMPTY, crowns, "crown"));
        if (silver > 0) rows.add(new Entry(ItemStack.EMPTY, silver, "silvermark"));
        for (var stack : choice.items()) {
            if (stack.isEmpty()) continue;
            int same = -1;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).currency().isEmpty()
                    && ItemStack.isSameItemSameComponents(rows.get(i).stack(), stack)) { same = i; break; }
            if (same < 0) rows.add(new Entry(stack.copy(), stack.getCount(), ""));
            else { var row = rows.get(same); rows.set(same, new Entry(row.stack(), row.count() + stack.getCount(), "")); }
        }
        return List.copyOf(rows);
    }
    private RewardPreview() { }
}
