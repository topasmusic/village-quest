package de.quest.caravan;

import net.minecraft.network.chat.Component;

public enum TradeRouteEventType {
    BROKEN_WHEEL(1, "broken_wheel"),
    INJURED_PACK_ANIMAL(2, "injured_pack_animal"),
    WASHED_OUT_BRIDGE(3, "washed_out_bridge"),
    FALSE_DISTRESS(4, "false_distress"),
    HUNGRY_TRAVELERS(5, "hungry_travelers"),
    ROAD_TOLL(6, "road_toll"),
    MISSING_COURIER(7, "missing_courier"),
    STORM_CAMP(8, "storm_camp");

    private final int id;
    private final String translationSuffix;

    TradeRouteEventType(int id, String translationSuffix) {
        this.id = id;
        this.translationSuffix = translationSuffix;
    }

    public int id() {
        return id;
    }

    public String key() {
        return translationSuffix;
    }

    public Component label() {
        return Component.translatable("text.village-quest.trade_route.event." + translationSuffix);
    }

    public Component help() {
        return Component.translatable("message.village-quest.trade_route.event." + translationSuffix + ".help");
    }

    public static TradeRouteEventType byId(int id) {
        for (TradeRouteEventType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }

    public static TradeRouteEventType byKey(String key) {
        if (key == null) {
            return null;
        }
        for (TradeRouteEventType type : values()) {
            if (type.translationSuffix.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
