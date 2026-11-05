package de.quest.economy;

import de.quest.data.PlayerQuestData;

/** One saved budget per owner, independent of routes, world days and offline catch-up. */
public final class CaravanIncomeLedger {
    private static final String CREDIT = "network_income_online_credit_seconds";
    private static final String WINDOW = "network_income_online_window_seconds";
    private static final String PAID = "network_income_online_window_paid";
    private static final int SECONDS_PER_MARK = 240;
    private CaravanIncomeLedger() {}

    /** Called once per owner per simulated online second, never for each route or catch-up. */
    public static void onlineSecond(PlayerQuestData data) {
        data.setTradeRouteInt(CREDIT, Math.min(3600, Math.max(0, data.getTradeRouteInt(CREDIT)) + 1));
        int seconds = Math.max(0, data.getTradeRouteInt(WINDOW)) + 1;
        if (seconds >= 3600) {
            seconds = 0;
            data.setTradeRouteInt(PAID, 0);
        }
        data.setTradeRouteInt(WINDOW, seconds);
    }

    public static int withdraw(PlayerQuestData data, int requested, boolean online) {
        if (!online || requested <= 0) return 0;
        int credit = Math.max(0, Math.min(3600, data.getTradeRouteInt(CREDIT)));
        int reward = Math.min(Math.min(requested, credit / SECONDS_PER_MARK),
                Math.max(0, 15 - paidThisOnlineHour(data)));
        data.setTradeRouteInt(CREDIT, credit - reward * SECONDS_PER_MARK);
        data.setTradeRouteInt(PAID, paidThisOnlineHour(data) + reward);
        return reward;
    }

    public static int paidThisOnlineHour(PlayerQuestData data) {
        return Math.max(0, data.getTradeRouteInt(PAID));
    }
}
