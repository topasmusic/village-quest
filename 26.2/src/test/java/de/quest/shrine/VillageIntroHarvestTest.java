package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.QuestState;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.village.VillageLifeState;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import org.junit.jupiter.api.*;

final class VillageIntroHarvestTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void verifiedLocalHarvestsRequireTheOriginalEpisodeAndNeverPayOrConsumeCrops() {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class); var player = mock(ServerPlayer.class);
        var owner = UUID.randomUUID(); var pos = new BlockPos(32,64,64);
        when(world.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(true);
        when(world.dimension()).thenReturn(Level.OVERWORLD); when(world.hasChunkAt(pos)).thenReturn(true);
        when(player.level()).thenReturn(world); when(player.getUUID()).thenReturn(owner);
        when(player.isAlive()).thenReturn(true); when(player.blockPosition()).thenReturn(pos);
        var marker = new ShadowsTradeRoadEncounterService.VillageMarker("32_64",32,64,16,48,48,80);
        var state = VillageBondMigrationTest.load(new CompoundTag()); var data = state.getPlayerData(owner);
        var kingdoms = new LivingKingdomsState();
        var contact = VillageContactService.bindCanonical(kingdoms,data,"minecraft:overworld",marker,pos,VillageBondType.GRANARY);
        assertTrue(contact.accepted());
        var village = UUID.fromString(data.getTradeRouteString(VillageBondService.villageKey(0,"canonical_id")));
        var progress = VillageIntroRequestProgress.open(data,village,0,false);
        assertTrue(progress.accept(VillageIntroRequestType.HARVEST_HELP,1,0,false));
        var life = mock(VillageLifeState.class); when(life.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
        var crop = (CropBlock) Blocks.WHEAT; var ripe = crop.getStateForAge(crop.getMaxAge());
        when(world.getBlockState(pos)).thenReturn(ripe);
        try (var states = mockStatic(QuestState.class); var ids = mockStatic(LivingKingdomsState.class);
             var villages = mockStatic(ShadowsTradeRoadEncounterService.class); var lives = mockStatic(VillageLifeState.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            ids.when(() -> LivingKingdomsState.get(server)).thenReturn(kingdoms);
            lives.when(() -> VillageLifeState.get(server)).thenReturn(life);
            villages.when(() -> ShadowsTradeRoadEncounterService.currentVillage(world,pos)).thenReturn(marker);
            var ticket = VillageIntroRequestService.harvestTicket(player); assertNotNull(ticket);
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket));
            when(world.getBlockState(pos)).thenReturn(crop.getStateForAge(0));
            when(player.isCreative()).thenReturn(true);
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket));
            when(player.isCreative()).thenReturn(false);
            when(world.hasChunkAt(pos)).thenReturn(false);
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket));
            when(world.hasChunkAt(pos)).thenReturn(true);
            when(life.status(any())).thenReturn(VillageLifeState.Status.ABANDONED);
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket));
            when(life.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
            var stale = new VillageIntroRequestService.HarvestTicket(owner,ticket.generation()+1,ticket.episode(),village,UUID.randomUUID());
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,stale));
            stale = new VillageIntroRequestService.HarvestTicket(owner,ticket.generation(),UUID.randomUUID(),village,UUID.randomUUID());
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,stale));
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,crop.getStateForAge(1),ticket));
            assertTrue(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket));
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket));
            assertEquals(1,progress.harvestCount());
            villages.when(() -> ShadowsTradeRoadEncounterService.currentVillage(world,pos)).thenReturn(
                    new ShadowsTradeRoadEncounterService.VillageMarker("90_90",90,90,80,100,80,100));
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,VillageIntroRequestService.harvestTicket(player)));
            villages.when(() -> ShadowsTradeRoadEncounterService.currentVillage(world,pos)).thenReturn(marker);
            for (int i=1;i<8;i++) assertTrue(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,VillageIntroRequestService.harvestTicket(player)));
            assertFalse(VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,VillageIntroRequestService.harvestTicket(player)));
            assertEquals(8,progress.harvestCount()); assertEquals(0,data.getCurrencyBalance());
            assertEquals(0,data.getTradeRouteInt(VillageBondService.villageKey(0,"completions")));
            verify(player,never()).getInventory();
        }
    }
}
