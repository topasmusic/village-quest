package de.quest.economy;

import de.quest.quest.story.VillageProjectType;
import net.minecraft.text.Text;

/** Permanent post-story investment tracks introduced by Prosperity & Prestige. */
public enum ProsperityBranch {
    APIARY("apiary", "project_apiary", VillageProjectType.APIARY_CHARTER),
    FORGE("forge", "project_forge", VillageProjectType.FORGE_CHARTER),
    MARKET("market", "project_market", VillageProjectType.MARKET_CHARTER),
    PASTURE("pasture", "project_pasture", VillageProjectType.PASTURE_CHARTER),
    ROAD_WATCH("road_watch", "project_watch", VillageProjectType.WATCH_BELL);

    private final String id;
    private final String icon;
    private final VillageProjectType requiredProject;

    ProsperityBranch(String id, String icon, VillageProjectType requiredProject) {
        this.id = id;
        this.icon = icon;
        this.requiredProject = requiredProject;
    }

    public String id() {
        return id;
    }

    public String icon() {
        return icon;
    }

    public VillageProjectType requiredProject() {
        return requiredProject;
    }

    public Text title() {
        return Text.translatable("screen.village-quest.prosperity.branch." + id);
    }

    public Text benefit(int rank) {
        return Text.translatable("screen.village-quest.prosperity.branch." + id + ".benefit." + Math.max(0, Math.min(3, rank)));
    }

    public static ProsperityBranch byId(String id) {
        if (id == null) {
            return null;
        }
        for (ProsperityBranch branch : values()) {
            if (branch.id.equalsIgnoreCase(id)) {
                return branch;
            }
        }
        return null;
    }
}
