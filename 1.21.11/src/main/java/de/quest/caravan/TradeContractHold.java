package de.quest.caravan;

import de.quest.data.PlayerQuestData;

/** Persists settlement downtime for an already accepted Trade Guild contract. */
public final class TradeContractHold {
    private static final String HOLD_START_DAY = "guild_contract_settlement_hold_start_day";
    private static final String DUE_DAY = "guild_contract_due_day";

    private TradeContractHold() {}

    public static boolean isHeld(PlayerQuestData data) {
        return data != null && data.getTradeRouteInt(HOLD_START_DAY) > 0;
    }

    public static boolean begin(PlayerQuestData data, int currentDay) {
        if (data == null || isHeld(data)) return false;
        data.setTradeRouteInt(HOLD_START_DAY, Math.max(0, currentDay) + 1);
        return true;
    }

    public static boolean end(PlayerQuestData data, int currentDay) {
        if (!isHeld(data)) return false;
        int started = data.getTradeRouteInt(HOLD_START_DAY) - 1;
        long pausedDays = Math.max(0L, (long) currentDay - started);
        long due = (long) data.getTradeRouteInt(DUE_DAY) + pausedDays;
        data.setTradeRouteInt(DUE_DAY, (int) Math.min(Integer.MAX_VALUE, due));
        data.setTradeRouteInt(HOLD_START_DAY, 0);
        return true;
    }

    public static void clear(PlayerQuestData data) {
        if (data != null) data.setTradeRouteInt(HOLD_START_DAY, 0);
    }
}
