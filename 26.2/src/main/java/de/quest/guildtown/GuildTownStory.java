package de.quest.guildtown;

import de.quest.shrine.VillageBondType;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** The five short, identity-specific stories introduced by 2.4. */
public enum GuildTownStory {
    SHARED_TABLE(0, "shared_table", VillageBondType.GRANARY),
    SPARKS_FOR_THE_ROAD(1, "sparks_for_the_road", VillageBondType.FORGE),
    LONG_DRIVE(2, "long_drive", VillageBondType.PASTURE),
    LANTERNS_IN_BLOOM(3, "lanterns_in_bloom", VillageBondType.APIARY),
    INK_BETWEEN_VILLAGES(4, "ink_between_villages", VillageBondType.ARCHIVE);

    private final int id;
    private final String key;
    private final VillageBondType villageType;

    GuildTownStory(int id, String key, VillageBondType villageType) {
        this.id = id;
        this.key = key;
        this.villageType = villageType;
    }

    public int id() { return id; }
    public String key() { return key; }
    public VillageBondType villageType() { return villageType; }
    public int bit() { return 1 << id; }
    public Component title() { return Component.translatable("quest.village-quest.guild_town.story." + key + ".title"); }

    public static GuildTownStory forVillage(VillageBondType type) {
        for (GuildTownStory story : values()) if (story.villageType == type) return story;
        return SHARED_TABLE;
    }

    public static GuildTownStory byKey(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (GuildTownStory story : values()) if (story.key.equals(normalized)) return story;
        return null;
    }
}
