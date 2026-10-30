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
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;

final class VillageTradeTrustNativeTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void onlyCommittedTradesWithLiveSurvivalActorAndConfirmedMerchantVillageEarnCredits() {
        var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class);
        var player=mock(ServerPlayer.class); var villager=mock(Villager.class);
        var owner=UUID.randomUUID(); var trader=UUID.randomUUID();
        var playerPos=new BlockPos(32,64,64); var traderPos=new BlockPos(34,64,64);
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(player.level()).thenReturn(world); when(villager.level()).thenReturn(world);
        when(player.getUUID()).thenReturn(owner); when(villager.getUUID()).thenReturn(trader);
        when(player.blockPosition()).thenReturn(playerPos); when(villager.blockPosition()).thenReturn(traderPos);
        when(player.isAlive()).thenReturn(true); when(villager.isAlive()).thenReturn(true);
        when(world.hasChunkAt(traderPos)).thenReturn(true);
        var marker=new ShadowsTradeRoadEncounterService.VillageMarker("32_64",32,64,16,48,48,80);
        var other=new ShadowsTradeRoadEncounterService.VillageMarker("90_90",90,90,80,100,80,100);
        var state=VillageBondMigrationTest.load(new CompoundTag()); var data=state.getPlayerData(owner);
        var kingdoms=new LivingKingdomsState();
        var contact=VillageContactService.bindCanonical(kingdoms,data,"minecraft:overworld",marker,playerPos,VillageBondType.GRANARY);
        assertTrue(contact.accepted()); data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),1);
        var life=mock(VillageLifeState.class); when(life.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
        try(var states=mockStatic(QuestState.class); var ids=mockStatic(LivingKingdomsState.class);
            var villages=mockStatic(ShadowsTradeRoadEncounterService.class); var lives=mockStatic(VillageLifeState.class);
            var calendar=mockStatic(de.quest.util.QuestCalendarService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            ids.when(()->LivingKingdomsState.get(server)).thenReturn(kingdoms);
            lives.when(()->VillageLifeState.get(server)).thenReturn(life);
            villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,playerPos)).thenReturn(marker);
            villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,traderPos)).thenReturn(marker);
            calendar.when(de.quest.util.QuestCalendarService::currentDay).thenReturn(4294967300L);
            when(player.isCreative()).thenReturn(true); trade(world,player,villager); when(player.isCreative()).thenReturn(false);
            when(player.isSpectator()).thenReturn(true); trade(world,player,villager); when(player.isSpectator()).thenReturn(false);
            when(player.isAlive()).thenReturn(false); trade(world,player,villager); when(player.isAlive()).thenReturn(true);
            when(villager.isAlive()).thenReturn(false); trade(world,player,villager); when(villager.isAlive()).thenReturn(true);
            when(villager.isBaby()).thenReturn(true); trade(world,player,villager); when(villager.isBaby()).thenReturn(false);
            when(world.hasChunkAt(traderPos)).thenReturn(false); trade(world,player,villager); when(world.hasChunkAt(traderPos)).thenReturn(true);
            when(player.level()).thenReturn(mock(ServerLevel.class)); trade(world,player,villager); when(player.level()).thenReturn(world);
            when(villager.level()).thenReturn(mock(ServerLevel.class)); trade(world,player,villager); when(villager.level()).thenReturn(world);
            when(life.status(any())).thenReturn(VillageLifeState.Status.ABANDONED); trade(world,player,villager);
            when(life.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
            villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,traderPos)).thenReturn(other);
            trade(world,player,villager);
            villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,traderPos)).thenReturn(marker);
            var outside=new ShadowsTradeRoadEncounterService.VillageMarker("32_64",32,64,16,33,48,80);
            villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,traderPos)).thenReturn(outside);
            trade(world,player,villager);
            villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,traderPos)).thenReturn(marker);
            assertEquals(0,VillageBondProgression.snapshot(data,0).qualifiedTrades());
            var receipt=UUID.randomUUID(); VillageTradeTrustService.onCommittedTrade(world,player,villager,receipt);
            VillageTradeTrustService.onCommittedTrade(world,player,villager,receipt);
            assertEquals(1,VillageBondProgression.snapshot(data,0).qualifiedTrades());
            trade(world,player,villager); trade(world,player,villager);
            assertEquals(2,VillageBondProgression.snapshot(data,0).qualifiedTrades());
            when(villager.getUUID()).thenReturn(UUID.randomUUID()); trade(world,player,villager); trade(world,player,villager);
            assertEquals(4,VillageBondProgression.snapshot(data,0).qualifiedTrades());
            assertEquals(VillageBondLevel.TRUSTED,VillageBondService.knownBondLevel(data,
                    UUID.fromString(data.getTradeRouteString(VillageBondService.villageKey(0,"canonical_id")))));
            assertEquals(0,data.getTradeRouteInt("route_count")); assertEquals(0,data.getCurrencyBalance());
            // A replaced player entity with the same UUID cannot clear the personal cap.
            var respawn=mock(ServerPlayer.class);
            when(respawn.level()).thenReturn(world); when(respawn.getUUID()).thenReturn(owner);
            when(respawn.isAlive()).thenReturn(true); when(respawn.blockPosition()).thenReturn(playerPos);
            for(int i=0;i<200;i++) trade(world,respawn,villager);
            assertEquals(4,VillageBondProgression.snapshot(data,0).qualifiedTrades());
        }
    }
    private static void trade(ServerLevel world,ServerPlayer player,Villager villager) {
        VillageTradeTrustService.onCommittedTrade(world,player,villager,UUID.randomUUID());
    }
}
