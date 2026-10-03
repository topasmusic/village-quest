package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import java.util.ArrayList;
import java.util.List;

/** Small persistent mailbox for freight already consumed when its route is removed or expires. */
public final class TradeContractRefundLedger {
    private static final int SLOTS = TradeContractType.values().length;
    private static final String PREFIX = "guild_contract_refund_";

    private TradeContractRefundLedger() {}

    public static List<Refund> peek(PlayerQuestData data) {
        if (data == null) return List.of(); List<Refund> refunds = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            int type = data.getTradeRouteInt(key(slot, "type")), amount = data.getTradeRouteInt(key(slot, "amount"));
            if (type > 0 && type <= SLOTS && amount > 0) refunds.add(new Refund(type, amount));
        } return List.copyOf(refunds);
    }
    public static int claim(PlayerQuestData data, int typeId, int requested) {
        if (data == null || requested <= 0) return 0;
        for (int slot = 0; slot < SLOTS; slot++) if (data.getTradeRouteInt(key(slot, "type")) == typeId) {
            int amount = data.getTradeRouteInt(key(slot, "amount")); int taken = Math.min(requested, Math.max(0, amount));
            if (taken == 0) return 0;
            data.setTradeRouteInt(key(slot, "amount"), amount - taken);
            if (taken == amount) data.setTradeRouteInt(key(slot, "type"), 0); return taken;
        } return 0;
    }

    public static boolean queue(PlayerQuestData data, int typeId, int amount) {
        if (data == null || typeId <= 0 || typeId > TradeContractType.values().length
                || amount <= 0 || amount > 64) return false;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (data.getTradeRouteInt(key(slot, "type")) != typeId) continue;
            int saved = data.getTradeRouteInt(key(slot, "amount"));
            if (saved < 0 || (long) saved + amount > Integer.MAX_VALUE) return false;
            data.setTradeRouteInt(key(slot, "amount"), saved + amount);
            return true;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (data.getTradeRouteInt(key(slot, "type")) != 0) continue;
            data.setTradeRouteInt(key(slot, "type"), typeId);
            data.setTradeRouteInt(key(slot, "amount"), amount);
            return true;
        }
        return false;
    }

    public static List<Refund> drain(PlayerQuestData data) {
        if (data == null) return List.of();
        List<Refund> refunds = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            int type = data.getTradeRouteInt(key(slot, "type"));
            int amount = data.getTradeRouteInt(key(slot, "amount"));
            if (type > 0 && type <= TradeContractType.values().length
                    && amount > 0) {
                refunds.add(new Refund(type, amount));
            }
            if (type != 0 || amount != 0) {
                data.setTradeRouteInt(key(slot, "type"), 0);
                data.setTradeRouteInt(key(slot, "amount"), 0);
            }
        }
        return List.copyOf(refunds);
    }

    private static String key(int slot, String suffix) {
        return PREFIX + slot + "_" + suffix;
    }

    public record Refund(int typeId, int amount) {}
}
