package de.quest.caravan;

import net.minecraft.text.Text;

public enum TradeRouteUpgrade {
    REINFORCED_WHEELS(1, "wheels", 25, 1),
    LANTERN_CREW(2, "lanterns", 30, 2),
    WEATHER_COVERS(4, "covers", 35, 2),
    ESCORTS(8, "escorts", 45, 3),
    INSURANCE(16, "insurance", 50, 4),
    TRADE_OFFICE(32, "office", 60, 5);

    private final int bit;
    private final String key;
    private final int cost;
    private final int requiredGuildRank;

    TradeRouteUpgrade(int bit, String key, int cost, int requiredGuildRank) {
        this.bit = bit;
        this.key = key;
        this.cost = cost;
        this.requiredGuildRank = requiredGuildRank;
    }

    public int bit() { return bit; }
    public String key() { return key; }
    public int cost() { return cost; }
    public int requiredGuildRank() { return requiredGuildRank; }
    public Text label() { return Text.translatable("text.village-quest.trade_guild.upgrade." + key); }

    public static TradeRouteUpgrade fromName(String name) {
        if (name == null) return null;
        for (TradeRouteUpgrade value : values()) {
            if (value.key.equalsIgnoreCase(name)) return value;
        }
        return null;
    }
}
