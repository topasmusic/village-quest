package de.quest.kingdom;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import de.quest.data.*;
import org.junit.jupiter.api.*;
final class DeferredBuildingConfirmationTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"applied","target","permission","invalid","anchor","connection","ambiguous"})
    void acceptedClickWaitsForEvidenceAndRechecksContextBeforeRecordingInn(String outcome) throws Exception {
        try(var states=mockStatic(LivingKingdomsState.class);var quests=mockStatic(QuestState.class);var story=mockStatic(KingdomStoryService.class)) {
            var server=mock(MinecraftServer.class);var state=mock(LivingKingdomsState.class,RETURNS_DEEP_STUBS);
            states.when(() -> LivingKingdomsState.get(server)).thenReturn(state);
            var quest=mock(QuestState.class);quests.when(() -> QuestState.get(server)).thenReturn(quest);
            UUID actor=UUID.randomUUID(),village=UUID.randomUUID();var data=new PlayerQuestData();
            var town=new LivingKingdomsState.VillageView(village,actor,LivingKingdomsState.Origin.CUSTOM,
                    de.quest.village.VillageLifeState.VillageKey.overworld(0,0),BlockPos.ZERO,"Home",0,Set.of());
            when(state.nearVillages("minecraft:overworld",BlockPos.ZERO,128,128)).thenReturn(List.of(town));
            when(state.zones(village)).thenReturn(List.of(new LivingKingdomsState.Zone(-8,8,-4,4,-8,8)));
            when(quest.getPlayerData(actor)).thenReturn(data);
            var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);var player=mock(ServerPlayer.class);when(player.level()).thenReturn(world);when(player.getUUID()).thenReturn(actor);
            // Use the actual unlock constant rather than assuming a storyline spelling.
            var unlock=KingdomBuildingService.class.getDeclaredField("BUILDINGS_UNLOCK");unlock.setAccessible(true);data.setMilestoneFlag((String)unlock.get(null),true);
            Class<?> runtimeType=Class.forName(KingdomBuildingService.class.getName()+"$Runtime"),deskType=Class.forName(KingdomBuildingService.class.getName()+"$Desk");
            var rc=runtimeType.getDeclaredConstructor(MinecraftServer.class);rc.setAccessible(true);Object runtime=rc.newInstance(server);
            var policy=new BuildingSessionPolicy(actor,"minecraft:overworld",BlockPos.ZERO,0);policy.offer(Map.of(village,0L));policy.select(village);
            var dc=deskType.getDeclaredConstructor(BuildingSessionPolicy.class,BuildingScanner.Role.class,Direction.class);dc.setAccessible(true);Object desk=dc.newInstance(policy,BuildingScanner.Role.INN,Direction.NORTH);
            var confirm=runtimeType.getDeclaredMethod("confirm",ServerPlayer.class,deskType,long.class);confirm.setAccessible(true);confirm.invoke(runtime,player,desk,50L);
            Object pending=null;try {var f=deskType.getDeclaredField("pendingConfirm");f.setAccessible(true);pending=f.get(desk);} catch(NoSuchFieldException ignored) {}
            assertNotNull(pending,"Confirm must retain the accepted request until a new scan finishes");
            verify(state,never()).confirmBuilding(any(),any(),anyLong(),any(),anyLong(),any(),any(),any(),any(),anyLong(),anyLong());
            var wf=runtimeType.getDeclaredField("watches");wf.setAccessible(true);var watches=(BuildingWatchIndex)wf.get(runtime);
            watches.watch(de.quest.network.BuildingPayloads.NONE,new BuildingWatchIndex.Spec("minecraft:overworld",BlockPos.ZERO,Direction.NORTH,BuildingScanner.Role.INN,BuildingScanner.Range.BASIC,0));
            var report=new BuildingScanner.Report(BuildingScanner.Role.INN,outcome.equals("invalid")?BuildingScanner.Status.INVALID:BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of());
            watches.publish(de.quest.network.BuildingPayloads.NONE,watches.revision(de.quest.network.BuildingPayloads.NONE),report,70);
            var block=mock(de.quest.content.block.KingdomAnchorBlock.class);when(block.role()).thenReturn(BuildingScanner.Role.INN);
            var physical=mock(net.minecraft.world.level.block.state.BlockState.class);when(physical.getBlock()).thenReturn(block);when(physical.getValue(de.quest.content.block.KingdomAnchorBlock.FACING)).thenReturn(Direction.NORTH);
            when(world.hasChunkAt(BlockPos.ZERO)).thenReturn(true);when(world.getBlockState(BlockPos.ZERO)).thenReturn(physical);
            var bf=runtimeType.getDeclaredField("budget");bf.setAccessible(true);((BuildingWorkBudget)bf.get(runtime)).begin(70);
            when(state.confirmBuilding(any(),any(),anyLong(),any(),anyLong(),any(),any(),any(),any(),anyLong(),anyLong())).thenReturn(KingdomAnchorRegistry.Result.APPLIED);
            if(outcome.equals("target")) { UUID other=UUID.randomUUID();policy.offer(Map.of(village,0L,other,0L));policy.select(other); }
            if(outcome.equals("permission")) data.setMilestoneFlag((String)unlock.get(null),false);
            if(outcome.equals("anchor")) when(physical.getBlock()).thenReturn(net.minecraft.world.level.block.Blocks.AIR);
            if(outcome.equals("connection")) when(state.zones(village)).thenReturn(List.of());
            if(outcome.equals("ambiguous")) {
                UUID other=UUID.randomUUID();var neighbor=new LivingKingdomsState.VillageView(other,actor,LivingKingdomsState.Origin.CUSTOM,
                        de.quest.village.VillageLifeState.VillageKey.overworld(1,0),BlockPos.ZERO,"Other",0,Set.of());
                when(state.nearVillages("minecraft:overworld",BlockPos.ZERO,128,128)).thenReturn(List.of(town,neighbor));
                when(state.zones(other)).thenReturn(List.of(new LivingKingdomsState.Zone(-8,8,-4,4,-8,8)));
            }
            confirm.invoke(runtime,player,desk,70L);
            var pf=deskType.getDeclaredField("pendingConfirm");pf.setAccessible(true);assertNull(pf.get(desk));
            if(!outcome.equals("applied")) {
                verify(state,never()).confirmBuilding(any(),any(),anyLong(),any(),anyLong(),any(),any(),any(),any(),anyLong(),anyLong());return;
            }
            verify(state,times(1)).confirmBuilding(eq(actor),eq(village),eq(0L),any(),eq(-1L),eq(BlockPos.ZERO),eq(Direction.NORTH),eq(BuildingScanner.Range.BASIC),eq(report),eq(70L),eq(70L));
            story.verify(() -> KingdomStoryService.chooseTarget(world,player,village,false),times(1));

        }
    }
}
