package de.quest.reputation;

import de.quest.network.ReputationPayloads;
import de.quest.network.ReputationPayloads.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Standalone, never inserted into QuestState and never issued an action-authorizing session. */
public final class ReputationPreview {
    private ReputationPreview() {}
    public static void open(ServerPlayer player, String mode) {
        var data = new SocialReputationData(); List<VillageKey> villages = new ArrayList<>();
        for (int i = 0; i < 12; i++) villages.add(new VillageKey("minecraft:overworld", 150 + i * 100, -200));
        data.setGuildTrust(switch (mode) { case "reliable" -> 34; case "respected" -> 76; case "blocked", "major" -> -70; default -> 0; });
        data.setLocalTrust(villages.getFirst(), 25); data.setLocalTrust(villages.get(1), -30);
        if (mode.equals("blocked") || mode.equals("major")) data.openCase(UUID.randomUUID(), mode.equals("major") ? Offence.CREW_KILL : Offence.ASSAULT, villages.get(1), 100);
        if (mode.equals("probation")) { data.setGuildTrust(70); data.setProbationRemaining(2); }
        List<SupportView> support = List.of(new SupportView(UUID.randomUUID(), villages.get(2), false, false, false),
                new SupportView(UUID.randomUUID(), villages.get(3), true, true, false));
        ServerPlayNetworking.send(player, new InteractionPayload(ReputationPayloads.NONE, true, false,
                ReputationViewService.build(data, villages, 0, true), support, ""));
    }
}
