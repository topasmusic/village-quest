package de.quest.caravan;

import net.minecraft.network.chat.Component;

public enum TradeRouteStatus {
    UNKNOWN(0, "unknown"),
    DANGEROUS(1, "dangerous"),
    SECURED(2, "secured"),
    FLOURISHING(3, "flourishing");

    private final int id;
    private final String translationSuffix;

    TradeRouteStatus(int id, String translationSuffix) {
        this.id = id;
        this.translationSuffix = translationSuffix;
    }

    public int id() {
        return id;
    }

    public Component label() {
        return Component.translatable("text.village-quest.trade_route.status." + translationSuffix);
    }

    public static TradeRouteStatus byId(int id) {
        for (TradeRouteStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
