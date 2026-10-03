package de.quest.guildtown;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.shrine.VillageBondService;
import de.quest.village.VillageLifeState;
import java.util.UUID;

/** Applies a physical village loss to existing personal story and commission progress. */
public final class GuildTownLifePolicy {
    private GuildTownLifePolicy() {}

    public static PauseResult pauseAffected(QuestState quests, PlayerQuestData actorData,
                                            VillageLifeState.VillageKey village) {
        if (quests == null || actorData == null || village == null
                || !"minecraft:overworld".equals(village.dimension())) {
            return new PauseResult(false, false);
        }
        boolean storyPaused = false;
        int storyIndex = GuildTownProgress.activeStoryVillage(actorData);
        if (storyIndex >= 0 && matches(actorData, storyIndex, village)
                && GuildTownProgress.setStoryPaused(actorData, true)) {
            storyPaused = true;
        }

        boolean commissionPaused = false;
        GuildTownCommission commission = GuildTownProgress.activeCommission(actorData);
        if (commission != null && GuildTownProgress.commissionState(actorData, commission)
                == GuildTownProgress.ACTIVE && commissionTouches(quests, actorData, village)) {
            commissionPaused = GuildTownProgress.setCommissionPaused(actorData, true);
        }
        return new PauseResult(storyPaused, commissionPaused);
    }

    private static boolean commissionTouches(QuestState quests, PlayerQuestData actorData,
                                             VillageLifeState.VillageKey village) {
        GuildTownProgress.VillageIdentity first = GuildTownProgress.commissionFirstIdentity(actorData);
        GuildTownProgress.VillageIdentity second = GuildTownProgress.commissionSecondIdentity(actorData);
        if (first != null && second != null) {
            return first.x() == village.anchorX() && first.z() == village.anchorZ()
                    || second.x() == village.anchorX() && second.z() == village.anchorZ();
        }
        UUID owner = GuildTownProgress.commissionOwner(actorData);
        PlayerQuestData ownerData = owner == null ? null : quests.getPlayersView().get(owner);
        return ownerData != null && (matches(ownerData,
                GuildTownProgress.commissionFirstVillage(actorData), village)
                || matches(ownerData, GuildTownProgress.commissionSecondVillage(actorData), village));
    }

    private static boolean matches(PlayerQuestData data, int index, VillageLifeState.VillageKey village) {
        if (data == null || index < 0 || index >= VillageBondService.historicalVillageCount(data)) {
            return false;
        }
        String dimension = data.getTradeRouteString(VillageBondService.villageKey(index, "dimension"));
        if (dimension.isBlank()) dimension = "minecraft:overworld";
        return dimension.equals(village.dimension())
                && data.getTradeRouteInt(VillageBondService.villageKey(index, "x")) == village.anchorX()
                && data.getTradeRouteInt(VillageBondService.villageKey(index, "z")) == village.anchorZ();
    }

    public record PauseResult(boolean storyPaused, boolean commissionPaused) {
        public boolean changed() { return storyPaused || commissionPaused; }
    }
}
