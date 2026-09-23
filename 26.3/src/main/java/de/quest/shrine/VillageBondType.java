package de.quest.shrine;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum VillageBondType {
    GRANARY(0, "granary"),
    FORGE(1, "forge"),
    PASTURE(2, "pasture"),
    APIARY(3, "apiary"),
    ARCHIVE(4, "archive");

    private final int id;
    private final String key;

    VillageBondType(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }
    public String key() { return key; }
    public Component label() { return Component.translatable("text.village-quest.village_bond.type." + key); }

    public static VillageBondType byId(int id) {
        for (VillageBondType value : values()) {
            if (value.id == id) return value;
        }
        return GRANARY;
    }

    public static VillageBondType byName(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (VillageBondType value : values()) {
            if (value.key.equals(normalized)) return value;
        }
        return null;
    }
}
