package de.quest.config;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

/** The four world options offered by the journal. Missing values inherit server.properties. */
public record WorldQuestSettings(boolean minecraftCalendar,
                                 VillageQuestServerConfig.AdventureProfile profile,
                                 boolean storyCooldown,
                                 VillageQuestServerConfig.CaravanVisualMode caravans) {
    public WorldQuestSettings {
        java.util.Objects.requireNonNull(profile);
        java.util.Objects.requireNonNull(caravans);
    }
    public static WorldQuestSettings recommended() {
        return new WorldQuestSettings(false, VillageQuestServerConfig.AdventureProfile.STANDARD,
                true, VillageQuestServerConfig.CaravanVisualMode.FULL);
    }
    public CompoundTag toNbt() {
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putBoolean("minecraftCalendar", minecraftCalendar);
        tag.putString("profile", profile.name());
        tag.putBoolean("storyCooldown", storyCooldown);
        tag.putString("caravans", caravans.name());
        return tag;
    }
    public static Optional<WorldQuestSettings> fromNbt(CompoundTag tag) {
        if (tag == null || tag.getIntOr("schema", 0) != 1) return Optional.empty();
        try {
            return Optional.of(new WorldQuestSettings(tag.getBooleanOr("minecraftCalendar", false),
                    VillageQuestServerConfig.AdventureProfile.valueOf(tag.getStringOr("profile", "")),
                    tag.getBooleanOr("storyCooldown", true),
                    VillageQuestServerConfig.CaravanVisualMode.valueOf(tag.getStringOr("caravans", ""))));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
