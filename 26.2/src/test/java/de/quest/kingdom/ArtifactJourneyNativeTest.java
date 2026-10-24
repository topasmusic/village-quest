package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import de.quest.reward.VanillaRewardRegistry;
import de.quest.party.QuestShareProfiles;
import de.quest.quest.story.StoryArcType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

/** Native interface guards and pure policy only: no Minecraft runtime or world started. */
final class ArtifactJourneyNativeTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void crossThreadSuccessCannotReadOrMutateWorldData() {
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        when(world.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(false);
        assertFalse(ArtifactJourneyService.confirmed(world,player,ArtifactJourneyProgress.Deed.HIVE,net.minecraft.core.BlockPos.ZERO));
        verify(server,never()).overworld(); verifyNoInteractions(player);
    }
    @Test void leverMustUseActualExtendedRangeRatherThanAnOrdinaryOrInvalidReach() {
        assertFalse(ArtifactJourneyService.leverReach(9,4.5,6.5));
        assertTrue(ArtifactJourneyService.leverReach(36,4.5,6.5));
        assertFalse(ArtifactJourneyService.leverReach(64,4.5,6.5));
        assertFalse(ArtifactJourneyService.leverReach(Double.NaN,4.5,6.5));
        assertFalse(ArtifactJourneyService.leverReach(36,4.5,4.5));
    }
    @Test void personalToolRequirementsCannotBeBypassedByGenericPartyAutoclaim() {
        assertFalse(QuestShareProfiles.isStoryShareable(StoryArcType.ARTIFACT_JOURNEY));
        assertTrue(QuestShareProfiles.isStoryShareable(StoryArcType.LIVING_KINGDOMS));
    }
    @Test void finaleOffersThreeRealEscrowPackagesAboveTheConcordFloorWithoutNewArtifacts() {
        var options=VanillaRewardRegistry.options("story:artifact_journey",RepeatableTargetProfile.NORMAL);
        assertEquals(3,options.size());
        for(var option:options) {
            assertFalse(option.diamondGate());
            assertTrue(option.goods().stream().anyMatch(g -> g.item().equals("diamond")&&g.count()>=12));
            assertTrue(option.goods().stream().allMatch(g -> !g.item().contains("village-quest")));
        }
        assertTrue(options.stream().anyMatch(o -> o.id().equals("roads")&&o.goods().stream().anyMatch(g -> g.item().equals("rail")&&g.count()==256)));
    }
}
