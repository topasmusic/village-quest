package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import de.quest.config.VillageQuestServerConfig;
import org.junit.jupiter.api.Test;

final class ShadowsTradeRoadEncounterBalanceTest {
    @Test
    void peacefulCreatesNoHostiles() {
        assertTrue(!ShadowsTradeRoadEncounterService.usesCombatWaves(Difficulty.PEACEFUL));
        assertEquals(0, ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.PEACEFUL, 1));
        assertEquals(0, ShadowsTradeRoadEncounterService.scaledHostileCount(3, Difficulty.PEACEFUL, 4));
        assertTrue(ShadowsTradeRoadEncounterService.merchantCountForKind(
                ShadowsTradeRoadEncounterService.RESCUE_KIND_FIRST_SIGNAL) > 0);
        assertTrue(ShadowsTradeRoadEncounterService.merchantCountForKind(
                ShadowsTradeRoadEncounterService.RESCUE_KIND_FINAL) > 0);
    }

    @Test
    void rescueTargetsStayInsideTheShortQaFriendlyRange() {
        assertEquals(320, ShadowsTradeRoadEncounterService.minimumRescueDistance());
        assertEquals(500, ShadowsTradeRoadEncounterService.maximumRescueDistance());
        assertTrue(ShadowsTradeRoadEncounterService.minimumRescueDistance()
                < ShadowsTradeRoadEncounterService.maximumRescueDistance());
    }

    @Test
    void merchantPlanRequiresACompleteSpacedLayout() {
        BlockPos anchor = new BlockPos(0, 70, 0);
        List<BlockPos> candidates = List.of(
                new BlockPos(3, 70, 0),
                new BlockPos(4, 70, 0),
                new BlockPos(-3, 70, 0),
                new BlockPos(0, 70, 3),
                new BlockPos(0, 76, -3)
        );
        List<BlockPos> selected = ShadowsTradeRoadEncounterService.selectSpacedMerchantPositions(
                anchor, candidates, 3);
        assertEquals(3, selected.size());
        assertTrue(selected.get(0).distSqr(selected.get(1)) >= 9.0D);
        assertTrue(selected.get(0).distSqr(selected.get(2)) >= 9.0D);
        assertTrue(ShadowsTradeRoadEncounterService.selectSpacedMerchantPositions(
                anchor, candidates, 4).isEmpty());
    }

    @Test
    void nonPeacefulDifficultiesKeepRealCombatWaves() {
        assertTrue(ShadowsTradeRoadEncounterService.usesCombatWaves(Difficulty.EASY));
        assertTrue(ShadowsTradeRoadEncounterService.usesCombatWaves(Difficulty.NORMAL));
        assertTrue(ShadowsTradeRoadEncounterService.usesCombatWaves(Difficulty.HARD));
    }

    @Test
    void easySoloIsLighterThanNormalAndHard() {
        int easy = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.EASY, 1);
        int normal = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.NORMAL, 1);
        int hard = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.HARD, 1);
        assertTrue(easy < normal);
        assertTrue(normal < hard);
    }

    @Test
    void onlyPresentPartySizeIncreasesEncounterScale() {
        int solo = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.NORMAL, 1);
        int duo = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.NORMAL, 2);
        int fullParty = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.NORMAL, 4);
        assertTrue(solo < duo);
        assertTrue(duo < fullParty);
        assertEquals(fullParty,
                ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.NORMAL, 12));
    }

    @Test
    void adventureProfilesChangePressureButNeverBreakPeaceful() {
        int relaxed = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.NORMAL, 1,
                VillageQuestServerConfig.AdventureProfile.RELAXED);
        int hardened = ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.NORMAL, 1,
                VillageQuestServerConfig.AdventureProfile.HARDENED);
        assertTrue(relaxed < hardened);
        assertEquals(0, ShadowsTradeRoadEncounterService.scaledHostileCount(8, Difficulty.PEACEFUL, 4,
                VillageQuestServerConfig.AdventureProfile.HARDENED));
    }
}
