package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

/** Real cache policy with mocked world boundary; no running server/world. */
final class ShardCacheSafetyTest {
    final UUID owner=UUID.randomUUID(); final BlockPos pos=new BlockPos(12,65,24);
    ServerLevel world;ServerPlayer player;PlayerQuestData data;MockedStatic<QuestState> states;
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @BeforeEach void setup(){
        world=mock(ServerLevel.class);player=mock(ServerPlayer.class);var server=mock(MinecraftServer.class);var list=mock(PlayerList.class);
        when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(server.getPlayerList()).thenReturn(list);
        when(list.getPlayer(owner)).thenReturn(player);when(player.getUUID()).thenReturn(owner);when(player.level()).thenReturn(world);
        when(player.blockPosition()).thenReturn(pos);when(world.hasChunkAt(pos)).thenReturn(true);
        var state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();data=state.getPlayerData(owner);
        data.setShardRelicQuestStage(ShardRelicQuestStage.CACHE_HUNT);data.setShardRelicChestX(12);data.setShardRelicChestY(65);data.setShardRelicChestZ(24);
        when(world.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
        states=mockStatic(QuestState.class,CALLS_REAL_METHODS);states.when(()->QuestState.get(server)).thenReturn(state);
    }
    @AfterEach void close(){states.close();}
    void ensure() throws Exception {var m=ShardRelicQuestService.class.getDeclaredMethod("ensureCacheChest",ServerLevel.class,UUID.class,PlayerQuestData.class);m.setAccessible(true);m.invoke(null,world,owner,data);}
    void prepare() throws Exception {var m=ShardRelicQuestService.class.getDeclaredMethod("prepareCacheChestIfNearby",ServerLevel.class,ServerPlayer.class,PlayerQuestData.class);m.setAccessible(true);m.invoke(null,world,player,data);}
    @Test void savedCacheNeverOverwritesAFixedReplacementBlock() throws Exception {ensure();verify(world,never()).setBlock(any(),any(),anyInt());assertFalse(data.hasTradeRouteFlag("relic.shards.cache_issued"));}
    @Test void issuedCacheNeverRecreatesEvenAnAirBlock() throws Exception {data.setTradeRouteFlag("relic.shards.cache_issued",true);when(world.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());ensure();verify(world,never()).setBlock(any(),any(),anyInt());}
    @Test void unloadedCacheNeverReadsWorldBlocks() throws Exception {when(world.hasChunkAt(pos)).thenReturn(false);ensure();verify(world,never()).getBlockState(any());verify(world,never()).getBlockEntity(any());}
    @Test void farAwayCacheNeverReadsWorldBlocks() throws Exception {when(player.blockPosition()).thenReturn(pos.offset(500,0,0));ensure();verify(world,never()).getBlockState(any());}
    @Test void otherDimensionNeverReadsOrPreparesOverworldCache() throws Exception {when(player.level()).thenReturn(mock(ServerLevel.class));ensure();prepare();verify(world,never()).getBlockState(any());}
    @Test void resolvedCacheUsesTheSameSearchIntervalAsUnresolvedTargets() throws Exception {when(world.getGameTime()).thenReturn(1L);when(player.getId()).thenReturn(0);prepare();verify(world,never()).getBlockState(any());}
    @Test void sameCoordinatesInOtherDimensionDoNotLockOrdinaryBlocks() {var other=mock(ServerLevel.class);var server=world.getServer();when(other.getServer()).thenReturn(server);assertTrue(ShardRelicQuestService.allowBlockBreak(other,player,pos));assertEquals(net.minecraft.world.InteractionResult.PASS,ShardRelicQuestService.onUseBlock(other,player,pos));}
    @Test void freshlyValidatedBuriedNaturalSiteStillCreatesItsFirstOwnedRing() throws Exception {
        var previous=de.quest.registry.ModItems.STARREACH_RING;
        de.quest.registry.ModItems.STARREACH_RING=net.minecraft.world.item.Items.FEATHER;
        net.minecraft.world.item.Items.FEATHER.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
        try(var sites=mockStatic(de.quest.util.WildernessSiteValidator.class)) {
            data.setShardRelicChestY(Integer.MIN_VALUE);when(world.getSeaLevel()).thenReturn(64);
            when(world.hasChunk(anyInt(),anyInt())).thenReturn(true);
            var block=new java.util.concurrent.atomic.AtomicReference<BlockState>(Blocks.DIRT.defaultBlockState());
            var chest=new net.minecraft.world.level.block.entity.ChestBlockEntity(pos,Blocks.CHEST.defaultBlockState());
            when(world.getBlockState(any())).thenAnswer(c->c.getArgument(0).equals(pos)?block.get():Blocks.DIRT.defaultBlockState());
            when(world.getBlockEntity(pos)).thenAnswer(c->block.get().is(Blocks.CHEST)?chest:null);
            when(world.setBlock(eq(pos),any(),eq(3))).thenAnswer(c->{block.set(c.getArgument(1));return true;});
            sites.when(()->de.quest.util.WildernessSiteValidator.findNaturalFlatSite(eq(world),anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),eq(false))).thenReturn(pos.above(3));
            prepare();assertTrue(block.get().is(Blocks.CHEST));assertTrue(data.hasTradeRouteFlag("relic.shards.cache_issued"));
            assertTrue(java.util.stream.IntStream.range(0,chest.getContainerSize()).mapToObj(chest::getItem).anyMatch(s->s.is(net.minecraft.world.item.Items.FEATHER)));
        } finally {de.quest.registry.ModItems.STARREACH_RING=previous;}
    }

    private BlockPos resolve() throws Exception {
        var method=ShardRelicQuestService.class.getDeclaredMethod("resolveCacheChestPos",ServerLevel.class,BlockPos.class,boolean.class);
        method.setAccessible(true);return (BlockPos)method.invoke(null,world,pos,false);
    }
    private void candidateSites(MockedStatic<de.quest.util.WildernessSiteValidator> sites,boolean next) {
        when(world.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.hasChunkAt(any())).thenReturn(true);
        when(world.getBlockState(any())).thenReturn(Blocks.DIRT.defaultBlockState());
        sites.when(()->de.quest.util.WildernessSiteValidator.findNaturalFlatSite(eq(world),anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),eq(false)))
                .thenAnswer(c->{int x=c.getArgument(1),z=c.getArgument(2);
                    if(x==pos.getX() && z==pos.getZ())return pos.above(3);
                    if(next && x==pos.getX()-1 && z==pos.getZ()-1)return pos.offset(-1,3,-1);
                    return null;});
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"air","iron_ore","oak_planks"})
    void skipsUnplaceableBuriedBlockAndSelectsNextNaturalCandidate(String block) throws Exception {
        try(var sites=mockStatic(de.quest.util.WildernessSiteValidator.class)) {
            candidateSites(sites,true);
            var invalid=switch(block){case "air"->Blocks.AIR;case "iron_ore"->Blocks.IRON_ORE;default->Blocks.OAK_PLANKS;};
            when(world.getBlockState(pos)).thenReturn(invalid.defaultBlockState());
            assertEquals(pos.offset(-1,0,-1),resolve());
            verify(world,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void skipsForeignBlockEntityEvenWhenItsBlockLooksLikeNaturalGround() throws Exception {
        try(var sites=mockStatic(de.quest.util.WildernessSiteValidator.class)) {
            candidateSites(sites,true);
            when(world.getBlockEntity(pos)).thenReturn(new net.minecraft.world.level.block.entity.ChestBlockEntity(pos,Blocks.CHEST.defaultBlockState()));
            assertEquals(pos.offset(-1,0,-1),resolve());
            verify(world,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void noPlaceableCandidateReturnsNullWithoutWritingInsteadOfReusingAir() throws Exception {
        try(var sites=mockStatic(de.quest.util.WildernessSiteValidator.class)) {
            candidateSites(sites,false);when(world.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());
            assertNull(resolve());verify(world,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void initialPlacementStillRechecksPlayerBlockAfterCandidateSelection() throws Exception {
        try(var sites=mockStatic(de.quest.util.WildernessSiteValidator.class)) {
            candidateSites(sites,false);assertEquals(pos,resolve());
            when(world.getBlockState(pos)).thenReturn(Blocks.OAK_PLANKS.defaultBlockState());
            var method=ShardRelicQuestService.class.getDeclaredMethod("placeInitialCacheChest",ServerLevel.class,PlayerQuestData.class,BlockPos.class);
            method.setAccessible(true);assertEquals(false,method.invoke(null,world,data,pos));
            verify(world,never()).setBlock(any(),any(),anyInt());
        }
    }
}
