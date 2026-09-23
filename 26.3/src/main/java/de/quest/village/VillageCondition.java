package de.quest.village;

import net.minecraft.network.chat.Component;

/** Bounded village-supply condition. It is deliberately separate from player trust. */
public enum VillageCondition {
    CRISIS(0, "crisis"),
    STRAINED(1, "strained"),
    STABLE(2, "stable"),
    RECOVERING(3, "recovering"),
    THRIVING(4, "thriving");

    private final int id;
    private final String key;

    VillageCondition(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public Component label() {
        return Component.translatable("text.village-quest.village_network.condition." + key);
    }

    public static VillageCondition fromSupport(int support) {
        int safe = Math.max(0, Math.min(100, support));
        if (safe < 20) return CRISIS;
        if (safe < 40) return STRAINED;
        if (safe < 60) return STABLE;
        if (safe < 80) return RECOVERING;
        return THRIVING;
    }
}
