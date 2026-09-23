package de.quest.guild;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum VillageGuildProject {
    NONE(0, "none"),
    COMMON_RESERVE(1, "common_reserve"),
    WAYSTATION(2, "waystation"),
    ARCHIVE_EXCHANGE(3, "archive_exchange");

    private final int id;
    private final String key;
    VillageGuildProject(int id, String key) { this.id = id; this.key = key; }
    public int id() { return id; }
    public String key() { return key; }
    public Component label() { return Component.translatable("text.village-quest.guild.project." + key); }
    public Component benefit() { return Component.translatable("text.village-quest.guild.project." + key + ".benefit"); }
    public static VillageGuildProject byId(int id) {
        for (VillageGuildProject value : values()) if (value.id == id) return value;
        return NONE;
    }
    public static VillageGuildProject byKey(String raw) {
        if (raw == null) return NONE;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (VillageGuildProject value : values()) if (value.key.equals(key)) return value;
        return NONE;
    }
}
