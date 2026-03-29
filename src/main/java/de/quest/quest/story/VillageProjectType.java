package de.quest.quest.story;

public enum VillageProjectType {
    VILLAGE_LEDGER("village_ledger", true),
    APIARY_CHARTER("apiary_charter", false),
    FORGE_CHARTER("forge_charter", false),
    MARKET_CHARTER("market_charter", false),
    PASTURE_CHARTER("pasture_charter", false),
    WATCH_BELL("watch_bell", false);

    private final String id;
    private final boolean alwaysUnlocked;

    VillageProjectType(String id, boolean alwaysUnlocked) {
        this.id = id;
        this.alwaysUnlocked = alwaysUnlocked;
    }

    public String id() {
        return id;
    }

    public boolean alwaysUnlocked() {
        return alwaysUnlocked;
    }

    public static VillageProjectType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (VillageProjectType type : values()) {
            if (type.id.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}
