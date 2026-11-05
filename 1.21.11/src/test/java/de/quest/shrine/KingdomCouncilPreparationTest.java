package de.quest.shrine;

import de.quest.data.QuestState;
import de.quest.kingdom.KingdomBuildingService;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryLane;
import de.quest.quest.story.StoryQuestService;
import de.quest.party.QuestPartyService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.QuestSoundFeedback;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.recipe.VillageQuestRecipeBookService;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real accept hook and native authority state, without a server/world runtime. */
final class KingdomCouncilPreparationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void councilRecipeCanBePreparedBeforeEitherTrustedPathGrantsAdministration(boolean tradePath) {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        UUID owner=UUID.randomUUID(); var data=quests.getPlayerData(owner);
        data.setLastRewardDay(1); data.setActiveStoryArc(StoryLane.CORE,StoryArcType.FAILING_HARVEST);
        data.setStoryInt("core_counter",17);
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(owner);
        var players=mock(PlayerList.class); when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayer(owner)).thenReturn(player);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
            var parties=mockStatic(QuestPartyService.class); var recipes=mockStatic(VillageQuestRecipeBookService.class);
            var books=mockStatic(QuestBookHelper.class); var trackers=mockStatic(QuestTrackerService.class);
            var sounds=mockStatic(QuestSoundFeedback.class); var ui=mockStatic(QuestMasterUiService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            assertTrue(StoryQuestService.acceptQuest(world,player,StoryArcType.LIVING_KINGDOMS));
            assertTrue(data.hasMilestoneFlag(KingdomBuildingService.COUNCIL_UNLOCK));
            assertFalse(data.hasMilestoneFlag(KingdomBuildingService.BUILDINGS_UNLOCK));
            assertEquals(17,data.getStoryInt("core_counter"));
            var kingdom=new LivingKingdomsState();
            var village=kingdom.registerGenerated(VillageKey.overworld(0,0),new BlockPos(0,64,0),"Village").orElseThrow();
            data.setTradeRouteInt("bond_village_count",1);
            data.setTradeRouteString(VillageBondService.villageKey(0,"canonical_id"),village.id().toString());
            assertEquals(VillageBondLevel.KNOWN,VillageBondService.knownBondLevel(data,village.id()));
            assertFalse(kingdom.canManageVillage(owner,village.id()));
            assertFalse(kingdom.recognizeMediator(village.id(),owner,false));
            assertEquals(LivingKingdomsState.Mutation.UNAUTHORIZED,kingdom.rename(owner,village.id(),village.revision(),"Other"));
            data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),tradePath?1:2);
            if(tradePath) {
                var merchantA=UUID.randomUUID(); var merchantB=UUID.randomUUID();
                for(int i=0;i<4;i++) assertTrue(VillageTradeTrustService.recordTrade(data,0,village.id(),
                        i<2?merchantA:merchantB,1,UUID.randomUUID()));
            }
            assertEquals(VillageBondLevel.TRUSTED,VillageBondService.knownBondLevel(data,village.id()));
            assertFalse(kingdom.canManageVillage(owner,village.id()),"Trust alone does not grant authority");
            assertTrue(kingdom.recognizeMediator(village.id(),owner,
                    VillageBondService.knownBondLevel(data,village.id()).id()>=1));
            assertTrue(kingdom.canManageVillage(owner,village.id()));
        }
    }
}
