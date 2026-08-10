package de.quest.pilgrim;

import de.quest.quest.story.VillageProjectType;

public enum PilgrimContractType {
    ROADMARKS_FOR_THE_COMPASS("roadmarks_for_the_compass", VillageProjectType.MARKET_CHARTER),
    QUENCH_FOR_THE_HALL("quench_for_the_hall", VillageProjectType.WATCH_BELL),
    WOOL_BEFORE_RAIN("wool_before_rain", VillageProjectType.WATCH_BELL),
    TRACKS_IN_THE_DARK("tracks_in_the_dark", VillageProjectType.WATCH_BELL),
    FANGS_BY_THE_HEDGEROW("fangs_by_the_hedgerow", VillageProjectType.WATCH_BELL),
    ASH_ON_THE_PASS("ash_on_the_pass", VillageProjectType.WATCH_BELL),
    SMOKE_OVER_BLACKSTONE("smoke_over_blackstone", VillageProjectType.WATCH_BELL),
    STILLNESS_BEYOND_THE_GATE("stillness_beyond_the_gate", VillageProjectType.WATCH_BELL);

    private final String id;
    private final VillageProjectType requiredProject;

    PilgrimContractType(String id, VillageProjectType requiredProject) {
        this.id = id;
        this.requiredProject = requiredProject;
    }

    public String id() {
        return id;
    }

    public VillageProjectType requiredProject() {
        return requiredProject;
    }

    public static PilgrimContractType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (PilgrimContractType type : values()) {
            if (type.id.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}
