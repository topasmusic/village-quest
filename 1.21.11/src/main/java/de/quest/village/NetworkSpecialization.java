package de.quest.village;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Voluntary bounded endgame identity for a player's living-village network. */
public enum NetworkSpecialization {
    NONE(0, "none"),
    STEWARD(1, "steward"),
    COURIER(2, "courier"),
    WAYFARER(3, "wayfarer");

    private final int id;
    private final String key;

    NetworkSpecialization(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }
    public String key() { return key; }
    public Component label() {
        return Component.translatable("text.village-quest.village_network.specialization." + key);
    }

    public Component benefit() {
        return Component.translatable("text.village-quest.village_network.specialization." + key + ".benefit");
    }

    public static NetworkSpecialization byId(int id) {
        for (NetworkSpecialization value : values()) if (value.id == id) return value;
        return NONE;
    }

    public static NetworkSpecialization byKey(String raw) {
        if (raw == null) return NONE;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (NetworkSpecialization value : values()) if (value.key.equals(key)) return value;
        return NONE;
    }
}
