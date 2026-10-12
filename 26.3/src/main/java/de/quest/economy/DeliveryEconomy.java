package de.quest.economy;

import de.quest.data.PlayerQuestData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Conservative commodity references in tenths of a Silvermark; transport is paid separately. */
public final class DeliveryEconomy {
    public static final int DAILY_COMMISSIONS = 3;
    private static final String DAY = "economy.delivery_day";
    private static final String ACCEPTED = "economy.delivery_accepted";
    private DeliveryEconomy() {}

    public static int materialValue(Item item, int amount) {
        int tenths;
        if (item == Items.WHEAT || item == Items.POTATO || item == Items.WHEAT_SEEDS
                || item == Items.DANDELION || item == Items.EGG || item == Items.FEATHER) tenths = 1;
        else if (item == Items.BREAD) tenths = 3;
        else if (item == Items.HAY_BLOCK) tenths = 9;
        else if (item == Items.BOOK) tenths = 9;
        else if (item == Items.IRON_INGOT) tenths = 6;
        else if (item == Items.LEATHER || item == Items.HONEYCOMB) tenths = 5;
        else if (item == Items.HONEY_BOTTLE || item == Items.CANDLE || item == Items.INK_SAC) tenths = 8;
        else if (item == Items.COPPER_INGOT || item == Items.WOOL.white()) tenths = 3;
        else tenths = 2; // Paper, coal, stone bricks and arrows.
        return Math.max(1, (int) Math.round(Math.max(0, amount) * tenths / 10.0));
    }

    public static int freightBase(Item item, int amount, int rank) {
        return materialValue(item, amount) + 7 + Math.max(1, Math.min(5, rank));
    }

    public static int transportBonus(int distance, boolean specialized, boolean office, boolean dangerous) {
        return Math.min(3, Math.max(0, distance) / 750)
                + (specialized ? 2 : 0) + (office ? 2 : 0) + (dangerous ? 2 : 0);
    }

    public static int regionalReward(Item item, int amount, int distance) {
        return materialValue(item, amount) + 6 + Math.min(4, Math.max(0, distance) / 750);
    }

    public static boolean canAccept(PlayerQuestData data, int day) {
        // A clock correction cannot replenish already used commissions.
        return day > data.getTradeRouteInt(DAY) || Math.max(0, data.getTradeRouteInt(ACCEPTED)) < DAILY_COMMISSIONS;
    }

    public static boolean reserve(PlayerQuestData data, int day) {
        if (!canAccept(data, day)) return false;
        if (day > data.getTradeRouteInt(DAY)) {
            data.setTradeRouteInt(DAY, day);
            data.setTradeRouteInt(ACCEPTED, 0);
        }
        data.setTradeRouteInt(ACCEPTED, Math.max(0, data.getTradeRouteInt(ACCEPTED)) + 1);
        return true;
    }
}
