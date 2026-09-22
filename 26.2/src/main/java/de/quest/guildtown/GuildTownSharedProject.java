package de.quest.guildtown;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Explicitly accepted, delivery-based guild projects; separate from passive 2.3 guild benefits. */
public enum GuildTownSharedProject {
    COMMON_STOREHOUSE(0, "common_storehouse", Items.WHEAT, 96),
    ROAD_WAYMARKERS(1, "road_waymarkers", Items.RAIL, 48),
    TRAVELLING_ARCHIVE(2, "travelling_archive", Items.PAPER, 64);

    private final int id;
    private final String key;
    private final Item item;
    private final int target;

    GuildTownSharedProject(int id, String key, Item item, int target) {
        this.id = id;
        this.key = key;
        this.item = item;
        this.target = target;
    }

    public int id() { return id; }
    public String key() { return key; }
    public Item item() { return item; }
    public int target() { return target; }
    public Component title() { return Component.translatable("quest.village-quest.guild_town.project." + key + ".title"); }

    public static GuildTownSharedProject byId(int id) {
        for (GuildTownSharedProject project : values()) if (project.id == id) return project;
        return null;
    }

    public static GuildTownSharedProject byKey(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (GuildTownSharedProject project : values()) if (project.key.equals(normalized)) return project;
        return null;
    }
}
