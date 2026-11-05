package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.special.SurveyorCompassQuestService;
import de.quest.caravan.TradeRouteService;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

final class FinalAuditRegressionTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static Object call(Class<?> type,String name,Class<?>[] args,Object... values) throws Exception {
        var method=type.getDeclaredMethod(name,args);method.setAccessible(true);
        try{return method.invoke(null,values);}catch(InvocationTargetException ex){
            if(ex.getCause() instanceof Error e)throw e;
            if(ex.getCause() instanceof RuntimeException e)throw e;
            throw ex;
        }
    }
    final MinecraftServer server=mock(MinecraftServer.class);
    final ServerLevel world=mock(ServerLevel.class),nether=mock(ServerLevel.class);
    final ServerPlayer player=mock(ServerPlayer.class);
    final UUID owner=UUID.randomUUID();
    @BeforeEach void setup(){
        when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);
        when(nether.getServer()).thenReturn(server);
        when(world.dimension()).thenReturn(Level.OVERWORLD);when(nether.dimension()).thenReturn(Level.NETHER);
        when(player.level()).thenReturn(nether);when(player.getUUID()).thenReturn(owner);
        when(player.blockPosition()).thenReturn(new BlockPos(100,70,100));
        when(world.getDifficulty()).thenReturn(net.minecraft.world.Difficulty.NORMAL);
        when(world.getRandom()).thenThrow(new AssertionError("Foreign-dimension location search"));
    }
    @Test void emptyCaravanCannotTriggerAcrossDimensions() throws Exception {
        assertEquals(false,call(EmptyCaravanStoryService.class,"isNear",new Class[]{ServerPlayer.class,BlockPos.class,int.class},player,player.blockPosition(),12));
    }
    @Test void shadowsDoesNotPlanFromNetherCoordinates() throws Exception {
        assertEquals(false,call(ShadowsTradeRoadEncounterService.class,"scheduleEncounter",new Class[]{ServerLevel.class,ServerPlayer.class,int.class,int.class},world,player,1,0));
    }
    @Test void deliveredCourierNeverSpawnsAgain() throws Exception {
        var data=new PlayerQuestData();data.setStoryInt(StoryQuestKeys.SHADOWS_LETTER_RECEIVED,1);
        var state=mock(QuestState.class);when(state.getPlayerData(owner)).thenReturn(data);
        when(world.getHeight(any(),anyInt(),anyInt())).thenThrow(new AssertionError("Courier spawned after delivery"));
        try(var states=mockStatic(QuestState.class)){states.when(()->QuestState.get(server)).thenReturn(state);
            call(ShadowsTradeRoadEncounterService.class,"ensureCourier",new Class[]{ServerLevel.class,ServerPlayer.class},world,player);
        }
    }
    @Test void imprintHintNeverReadsOverworldAtNetherPosition() throws Exception {
        var data=new PlayerQuestData();data.setMilestoneFlag("surveyor_compass_biome_calibration_active",true);
        var state=mock(QuestState.class);when(state.getPlayerData(owner)).thenReturn(data);
        when(world.getBiome(any())).thenThrow(new AssertionError("Cross-dimension biome read"));
        try(var states=mockStatic(QuestState.class)){states.when(()->QuestState.get(server)).thenReturn(state);
            assertNull(call(SurveyorCompassQuestService.class,"currentHintCandidate",new Class[]{ServerLevel.class,ServerPlayer.class},world,player));
        }
    }
    @Test void deletedRespawnBlockIsNeverUsedAsHome() throws Exception {
        var config=mock(ServerPlayer.RespawnConfig.class);var spawn=mock(LevelData.RespawnData.class);
        when(config.respawnData()).thenReturn(spawn);when(player.getRespawnConfig()).thenReturn(config);
        when(spawn.dimension()).thenReturn(Level.OVERWORLD);when(spawn.pos()).thenReturn(new BlockPos(10,70,10));
        when(server.getLevel(Level.OVERWORLD)).thenReturn(world);when(world.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        assertNull(call(SurveyorCompassQuestService.class,"resolveHomeTarget",new Class[]{MinecraftServer.class,ServerPlayer.class},server,player));
    }
    @Test void wonBaitCannotBeSelectedAgain() throws Exception {
        when(player.level()).thenReturn(world);
        var data=new PlayerQuestData();data.setActiveStoryArc(de.quest.quest.story.StoryArcType.THE_EMPTY_CARAVAN);
        data.setStoryChapterProgress("the_empty_caravan",4);data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE,3);
        var state=mock(QuestState.class);when(state.getPlayerData(owner)).thenReturn(data);
        try(var states=mockStatic(QuestState.class)){states.when(()->QuestState.get(server)).thenReturn(state);
            var sword=mock(net.minecraft.world.item.ItemStack.class);when(sword.is(Items.IRON_SWORD)).thenReturn(true);
            assertFalse(EmptyCaravanStoryService.chooseApproach(world,player,mock(de.quest.entity.QuestMasterEntity.class),sword));
        }
    }
    @Test void absentAmbushAttackerDoesNotUnlockClaim() throws Exception {
        var data=new PlayerQuestData();data.setTradeRouteInt("route_0_event_progress",1);
        var runtimeType=Class.forName("de.quest.caravan.TradeRouteService$CaravanRuntime");
        var constructor=runtimeType.getDeclaredConstructor();constructor.setAccessible(true);var runtime=constructor.newInstance();
        var attackers=runtimeType.getDeclaredField("attackerIds");attackers.setAccessible(true);
        ((Set<UUID>)attackers.get(runtime)).add(UUID.randomUUID());
        var keyType=Class.forName("de.quest.caravan.TradeRouteService$RouteKey");
        var keyConstructor=keyType.getDeclaredConstructors()[0];keyConstructor.setAccessible(true);var key=keyConstructor.newInstance(owner,0);
        var active=TradeRouteService.class.getDeclaredField("ACTIVE_CARAVANS");active.setAccessible(true);
        var map=(Map<Object,Object>)active.get(null);map.put(key,runtime);
        var state=mock(QuestState.class);
        try(var states=mockStatic(QuestState.class)){states.when(()->QuestState.get(server)).thenReturn(state);
            call(TradeRouteService.class,"tickAmbush",new Class[]{ServerLevel.class,keyType,PlayerQuestData.class},world,key,data);
            assertEquals(1,data.getTradeRouteInt("route_0_event_progress"));
        }finally{map.remove(key);}
    }
    @Test void unloadedCourierRetainsItsIdentityWithoutReplacementSearch() throws Exception {
        when(player.level()).thenReturn(world);
        var data=new PlayerQuestData();var state=mock(QuestState.class);when(state.getPlayerData(owner)).thenReturn(data);
        var field=ShadowsTradeRoadEncounterService.class.getDeclaredField("LETTER_COURIERS");field.setAccessible(true);
        var map=(Map<UUID,UUID>)field.get(null);var courier=UUID.randomUUID();map.put(owner,courier);
        when(world.getHeight(any(),anyInt(),anyInt())).thenThrow(new AssertionError("Unloaded courier duplicated"));
        try(var states=mockStatic(QuestState.class)){states.when(()->QuestState.get(server)).thenReturn(state);
            call(ShadowsTradeRoadEncounterService.class,"ensureCourier",new Class[]{ServerLevel.class,ServerPlayer.class},world,player);
            assertEquals(courier,map.get(owner));
        }finally{map.remove(owner);}
    }
    @Test void emptyBaitWithAnUnloadedAttackerStaysActive() throws Exception {
        when(player.level()).thenReturn(world);
        var data=new PlayerQuestData();data.setActiveStoryArc(de.quest.quest.story.StoryArcType.THE_EMPTY_CARAVAN);
        data.setStoryChapterProgress("the_empty_caravan",4);data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE,2);
        data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_OBJECTIVE_MODE,1);
        data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_TARGET_X,100);data.setStoryInt(StoryQuestKeys.EMPTY_CARAVAN_TARGET_Z,100);
        var state=mock(QuestState.class);when(state.getPlayerData(owner)).thenReturn(data);
        var type=Class.forName("de.quest.content.story.EmptyCaravanStoryService$BaitRuntime");
        var cons=type.getDeclaredConstructor();cons.setAccessible(true);var runtime=cons.newInstance();
        var ids=type.getDeclaredField("attackerIds");ids.setAccessible(true);((List<UUID>)ids.get(runtime)).add(UUID.randomUUID());
        var field=EmptyCaravanStoryService.class.getDeclaredField("ACTIVE_BAITS");field.setAccessible(true);var map=(Map<UUID,Object>)field.get(null);map.put(owner,runtime);
        try(var states=mockStatic(QuestState.class)){states.when(()->QuestState.get(server)).thenReturn(state);
            call(EmptyCaravanStoryService.class,"tickBait",new Class[]{ServerLevel.class,ServerPlayer.class},world,player);
            assertEquals(2,data.getStoryInt(StoryQuestKeys.EMPTY_CARAVAN_BAIT_STATE));assertEquals(1,((List<?>)ids.get(runtime)).size());
        }finally{map.remove(owner);}
    }
    @Test void shadowWaveWaitsForUnloadedEnemiesInsteadOfWinning() throws Exception {
        when(player.level()).thenReturn(world);
        var service=ShadowsTradeRoadEncounterService.class;var specField=service.getDeclaredField("FIRST_SIGNAL_SPEC");specField.setAccessible(true);var spec=specField.get(null);
        var type=Class.forName(service.getName()+"$ActiveEncounter");var cons=type.getDeclaredConstructors()[0];cons.setAccessible(true);
        var runtime=cons.newInstance(owner,spec,BlockPos.ZERO);var enemies=type.getDeclaredField("hostileIds");enemies.setAccessible(true);
        ((List<UUID>)enemies.get(runtime)).add(UUID.randomUUID());
        call(service,"tickActiveEncounter",new Class[]{ServerLevel.class,ServerPlayer.class,type,String.class,int.class},world,player,runtime,"wins",1);
        assertEquals(1,((List<?>)enemies.get(runtime)).size());verify(world,never()).getRandom();
    }
}
