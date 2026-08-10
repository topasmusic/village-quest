package de.quest.caravan;

import net.minecraft.text.Text;

public enum TradeRouteSpecialization {
    GENERAL(0, "general"),
    PROVISIONS(1, "provisions"),
    FORGE(2, "forge"),
    LIVESTOCK(3, "livestock"),
    COURIER(4, "courier"),
    GUARDED(5, "guarded");

    private final int id;
    private final String key;

    TradeRouteSpecialization(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }
    public String key() { return key; }
    public Text label() { return Text.translatable("text.village-quest.trade_guild.specialization." + key); }

    public static TradeRouteSpecialization fromId(int id) {
        for (TradeRouteSpecialization value : values()) {
            if (value.id == id) return value;
        }
        return GENERAL;
    }

    public static TradeRouteSpecialization fromName(String name) {
        if (name == null) return null;
        for (TradeRouteSpecialization value : values()) {
            if (value.key.equalsIgnoreCase(name)) return value;
        }
        return null;
    }
}
