package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import de.quest.guildtown.GuildTownProgress;
import de.quest.village.VillageLifeState;
import java.util.UUID;

/** Keeps settlement history in the existing personal Chronicle, once per physical event cycle. */
public final class VillageLifeChronicle {
    private VillageLifeChronicle() {}

    public static boolean record(PlayerQuestData data, UUID ownerId, VillageLifeState.VillageKey village,
                                 int cycle, boolean restored, long gameTime) {
        if (data == null || ownerId == null || village == null || cycle <= 0) {
            return false;
        }
        String eventId = "village." + (restored ? "restored." : "abandoned.")
                + village.dimension().replace(':', '.') + ".cycle." + cycle;
        return GuildTownProgress.addPersonalChronicle(data,
                new GuildTownProgress.VillageIdentity(ownerId, village.anchorX(), village.anchorZ()),
                eventId, false, gameTime);
    }
}
