package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ResettlementSupportTest {
    private static final VillageKey VILLAGE = VillageKey.overworld(128, -224);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void onlyEightPlayersCanRegisterAndAidAloneDoesNotCreateAReward() {
        Map<UUID, SocialReputationData> players = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            UUID player = UUID.randomUUID(); players.put(player, new SocialReputationData());
            assertEquals(i < 8, ResettlementSupportService.accept(players, player, VILLAGE, 1, true));
            if (i < 8) {
                assertTrue(ResettlementSupportService.recordSupply(players.get(player), VILLAGE, 1));
                assertEquals(0, players.get(player).guildTrust());
            }
        }
        assertEquals(8, ResettlementSupportService.resolve(players, VILLAGE, 1, 10, 100).size());
        assertEquals(8, players.values().stream().filter(data -> data.guildTrust() == 5).count());
    }

    @Test void realRestorationAwardsOnceAcrossReloadAndASecondCycleHasANewIdentity() {
        UUID player = UUID.randomUUID(); Map<UUID, SocialReputationData> players = new HashMap<>();
        players.put(player, new SocialReputationData());
        assertFalse(ResettlementSupportService.accept(players, player, VILLAGE, 1, false));
        assertTrue(ResettlementSupportService.accept(players, player, VILLAGE, 1, true));
        assertFalse(ResettlementSupportService.accept(players, player, VILLAGE, 1, true));
        assertTrue(ResettlementSupportService.recordSupply(players.get(player), VILLAGE, 1));
        players.put(player, SocialReputationData.fromNbt(players.get(player).toNbt()));
        assertEquals(5, ResettlementSupportService.resolve(players, VILLAGE, 1, 10, 100).get(player).guildDelta());
        assertEquals(8, players.get(player).localTrust(VILLAGE));
        players.put(player, SocialReputationData.fromNbt(players.get(player).toNbt()));
        assertTrue(ResettlementSupportService.resolve(players, VILLAGE, 1, 11, 200).isEmpty());
        assertTrue(ResettlementSupportService.accept(players, player, VILLAGE, 2, true));
        assertTrue(ResettlementSupportService.recordSupply(players.get(player), VILLAGE, 2));
        assertEquals(5, ResettlementSupportService.resolve(players, VILLAGE, 2, 11, 300).get(player).guildDelta());
    }

    @Test void violenceInTheAffectedVillageBarsTheCycleBonusAndIncompleteAidCostsNothing() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID(); Map<UUID, SocialReputationData> players = new HashMap<>();
        players.put(player, new SocialReputationData()); players.put(other, new SocialReputationData());
        assertTrue(ResettlementSupportService.accept(players, player, VILLAGE, 1, true));
        assertTrue(ResettlementSupportService.recordSupply(players.get(player), VILLAGE, 1));
        players.get(player).openCase(UUID.randomUUID(), Offence.ASSAULT, VILLAGE, 50);
        assertTrue(ResettlementSupportService.accept(players, other, VILLAGE, 1, true));
        ResettlementSupportService.resolve(players, VILLAGE, 1, 10, 100);
        assertEquals(0, players.get(player).guildTrust()); assertEquals(0, players.get(other).guildTrust());
        assertTrue(players.get(player).supportView().get(VILLAGE).resolved());
        assertTrue(players.get(other).supportView().get(VILLAGE).resolved());
    }
    @Test void restorationWhileDisabledConsumesThePromiseWithoutBankingItForReenable() {
        var players = new HashMap<UUID, SocialReputationData>(); UUID player = UUID.randomUUID(); players.put(player, new SocialReputationData());
        assertTrue(ResettlementSupportService.accept(players, player, VILLAGE, 1, true));
        assertTrue(ResettlementSupportService.recordSupply(players.get(player), VILLAGE, 1));
        var result = ResettlementSupportService.resolve(players, VILLAGE, 1, 10, 100, false);
        assertEquals(0, result.get(player).guildDelta()); assertEquals(0, players.get(player).guildTrust());
        assertTrue(players.get(player).supportView().get(VILLAGE).resolved());
        assertTrue(ResettlementSupportService.resolve(players, VILLAGE, 1, 11, 200, true).isEmpty());
    }

}
