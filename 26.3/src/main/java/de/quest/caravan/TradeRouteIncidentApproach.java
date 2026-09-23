package de.quest.caravan;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Player-selected response doctrine for normal route incidents. */
public enum TradeRouteIncidentApproach {
    CAREFUL(1, "careful", -6, 0.85, 0.60),
    BOLD(2, "bold", 6, 1.25, 1.25);

    private final int id;
    private final String key;
    private final int eventChanceDelta;
    private final double rewardMultiplier;
    private final double failureMultiplier;

    TradeRouteIncidentApproach(int id, String key, int eventChanceDelta,
                               double rewardMultiplier, double failureMultiplier) {
        this.id = id; this.key = key; this.eventChanceDelta = eventChanceDelta;
        this.rewardMultiplier = rewardMultiplier; this.failureMultiplier = failureMultiplier;
    }

    public int id() { return id; }
    public String key() { return key; }
    public int eventChanceDelta() { return eventChanceDelta; }
    public double rewardMultiplier() { return rewardMultiplier; }
    public double failureMultiplier() { return failureMultiplier; }
    public Component label() { return Component.translatable("text.village-quest.trade_route.approach." + key); }
    public Component terms() { return Component.translatable("text.village-quest.trade_route.approach." + key + ".terms"); }

    public static TradeRouteIncidentApproach byId(int id) {
        for (TradeRouteIncidentApproach value : values()) if (value.id == id) return value;
        return CAREFUL;
    }

    public static TradeRouteIncidentApproach byKey(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (TradeRouteIncidentApproach value : values()) if (value.key.equals(key)) return value;
        return null;
    }
}
