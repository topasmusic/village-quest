package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.archive.GuildArchiveService;
import de.quest.data.QuestState;
import de.quest.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.*;

/** Native cache block/entity and owner generation tests. No loaded game/world. */
final class ShardResetRewardTest {
    static Item previous;final UUID owner=UUID.randomUUID();
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @BeforeEach void setup(){previous=ModItems.STARREACH_RING;ModItems.STARREACH_RING=Items.FEATHER;
        for(var item:java.util.List.of(Items.FEATHER,Items.DIAMOND))item.builtInRegistryHolder().bindComponents(
                net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());}
    @AfterEach void restore(){ModItems.STARREACH_RING=previous;}
    private void check(boolean ordinary,boolean full) throws Exception {
        var state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();var data=state.getPlayerData(owner);
        data.setTradeRouteInt("archive.gen."+GuildArchiveService.ArchiveItem.STARREACH_RING.id(),2);
        data.setShardRelicQuestStage(ShardRelicQuestStage.CACHE_HUNT);data.setShardRelicChestX(12);data.setShardRelicChestY(65);data.setShardRelicChestZ(24);
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);when(world.getServer()).thenReturn(server);
        var player=mock(ServerPlayer.class);when(player.getUUID()).thenReturn(owner);when(player.level()).thenReturn(world);
        var players=mock(net.minecraft.server.players.PlayerList.class);when(server.getPlayerList()).thenReturn(players);
        when(server.overworld()).thenReturn(world);when(players.getPlayer(owner)).thenReturn(player);
        when(player.blockPosition()).thenReturn(new BlockPos(12,65,24));when(world.hasChunkAt(any())).thenReturn(true);
        var pos=new BlockPos(12,65,24);var block=Blocks.CHEST.defaultBlockState();var chest=new ChestBlockEntity(pos,block);
        if(ordinary)chest.setItem(0,new ItemStack(Items.DIAMOND,5));
        if(full)for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.DIAMOND,64));
        when(world.getBlockState(pos)).thenReturn(block);when(world.getBlockEntity(pos)).thenReturn(chest);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            var ensure=ShardRelicQuestService.class.getDeclaredMethod("ensureCacheChest",ServerLevel.class,UUID.class,de.quest.data.PlayerQuestData.class);ensure.setAccessible(true);
            ensure.invoke(null,world,owner,data);
            if(full){assertFalse(ShardRelicQuestService.hasIssuedCacheRing(data));for(int i=0;i<chest.getContainerSize();i++)assertEquals(64,chest.getItem(i).getCount());}
            else if(ordinary)assertEquals(5,chest.getItem(0).getCount(),"Cache issue must never clear existing vanilla storage");
            else {
                var ring=java.util.stream.IntStream.range(0,chest.getContainerSize()).mapToObj(chest::getItem).filter(s->s.is(ModItems.STARREACH_RING)).findFirst().orElseThrow();
                assertTrue(GuildArchiveService.isValidOwnedStack(world,player,ring,GuildArchiveService.ArchiveItem.STARREACH_RING));
                assertTrue(GuildArchiveService.eligible(world,player,GuildArchiveService.ArchiveItem.STARREACH_RING));
                chest.clearContent();ensure.invoke(null,world,owner,data);
                assertTrue(java.util.stream.IntStream.range(0,chest.getContainerSize()).mapToObj(chest::getItem).allMatch(ItemStack::isEmpty),"Issued cache must not refill after removal");
            }
        }
    }
    @Test void newCacheRingMustBeValidAfterProgressGenerationWasRaised() throws Exception {check(false,false);}
    @Test void cacheNeverDeletesOrdinaryItemsAlreadyStoredAtItsPosition() throws Exception {check(true,false);}
    @Test void fullExistingChestDoesNotLoseItemsOrSpendTheIssueEntitlement() throws Exception {check(false,true);}
}
