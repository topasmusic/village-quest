package de.quest.shrine;

/** Stable catalogue identities; never use translated titles as receipt keys. */
public enum VillageIntroRequestType {
    REPAIR_TIMBER("repair_timber", 16),
    PATH_STONE("path_stone", 16),
    HARVEST_HELP("harvest_help", 8);

    public static final int CATALOG_REVISION = 1;
    private final String id;
    private final int target;
    VillageIntroRequestType(String id, int target) { this.id = id; this.target = target; }
    public String id() { return id; }
    public int target() { return target; }
    int bit() { return 1 << ordinal(); }
    static VillageIntroRequestType fromId(String id) {
        for (var type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
