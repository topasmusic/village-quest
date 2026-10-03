package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.caravan.*;
import de.quest.reputation.*;
import de.quest.reputation.ReputationDamageAdapter.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;

/** Regression coverage for the independently reproduced .7 audit findings. */
final class ReputationAuditRegressionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void reset() { ReputationDamageAdapter.resetRuntime(); }

    @Test void nonfatalFirePreservesRecentPlayerAttribution() {
        assertEquals(-60, fireSequence(false));
        assertEquals(-60, fireSequence(true));
    }
    private int fireSequence(boolean intermediate) {
        return damageSequence(intermediate ? Followup.FIRE : Followup.NONE, 0);
    }
    @Test void fireTicksDoNotExtendTheOriginal200TickDeadline() {
        assertEquals(-60, damageSequence(Followup.FIRE, 198));
        assertEquals(-15, damageSequence(Followup.FIRE, 199));
    }
    @Test void unknownInterveningDamageInvalidatesTheOldCause() {
        assertEquals(-15, damageSequence(Followup.UNKNOWN, 0));
    }
    @Test void aNewKnownAttackerReplacesTheOldCause() {
        assertEquals(-15, damageSequence(Followup.OTHER_PLAYER, 0));
    }
    @Test void nonfatalFallAlsoPreservesTheRecentCause() {
        assertEquals(-60, damageSequence(Followup.FALL, 0));
    }
    private enum Followup { NONE, FIRE, FALL, UNKNOWN, OTHER_PLAYER }
    private int damageSequence(Followup intermediate, int delay) {
        ReputationDamageAdapter.resetRuntime();
        var state = QuestState.fromNbt(new CompoundTag());
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        var players = mock(net.minecraft.server.players.PlayerList.class);
        when(world.getServer()).thenReturn(server); when(server.getPlayerList()).thenReturn(players);
        var entity = mock(LivingEntity.class); UUID entityId = UUID.randomUUID(), playerId = UUID.randomUUID();
        when(entity.getUUID()).thenReturn(entityId); when(entity.getHealth()).thenReturn(20f);
        var player = mock(ServerPlayer.class); when(player.getUUID()).thenReturn(playerId);
        UUID otherId = UUID.randomUUID(); var otherPlayer = mock(ServerPlayer.class); when(otherPlayer.getUUID()).thenReturn(otherId);
        var hit = mock(DamageSource.class); when(hit.getEntity()).thenReturn(player);
        var fire = mock(DamageSource.class); when(fire.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)).thenReturn(true);
        ReputationDamageAdapter.activate((w, e) -> new ProtectedVictim(entityId, VictimKind.CREW, null));
        var social = state.getPlayerData(playerId).socialReputation();
        try (var lookup = mockStatic(QuestState.class); var incidents = mockStatic(ReputationIncidentService.class, CALLS_REAL_METHODS)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            incidents.when(() -> ReputationIncidentService.record(eq(server), any(DamageEvidence.class)))
                    .thenAnswer(call -> {
                        var evidence = call.getArgument(1, DamageEvidence.class);
                        return ReputationIncidentService.record(state.getPlayerData(evidence.attacker()).socialReputation(), evidence);
                    });
            var frame = ReputationDamageAdapter.begin(world, entity, hit);
            ReputationDamageAdapter.finish(world, entity, hit, frame, 5);
            state.advanceSocialServerTick();
            if (intermediate != Followup.NONE) {
                var next = mock(DamageSource.class);
                if (intermediate == Followup.FIRE) when(next.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)).thenReturn(true);
                if (intermediate == Followup.FALL) when(next.is(DamageTypes.FALL)).thenReturn(true);
                if (intermediate == Followup.OTHER_PLAYER) when(next.getEntity()).thenReturn(otherPlayer);
                frame = ReputationDamageAdapter.begin(world, entity, next);
                ReputationDamageAdapter.finish(world, entity, next, frame, 1);
                state.advanceSocialServerTick();
            }
            for (int i = 0; i < delay; i++) state.advanceSocialServerTick();
            frame = ReputationDamageAdapter.begin(world, entity, fire); frame.fatalConfirmed = true;
            ReputationDamageAdapter.finish(world, entity, fire, frame, 20);
            if (intermediate == Followup.OTHER_PLAYER) assertEquals(-60, state.getPlayerData(otherId).socialReputation().guildTrust());
        }
        return social.guildTrust();
    }

    @Test void caravanKeepsDestinationAttributionWhileCrossingUnrelatedVillages() throws Exception {
        var state = QuestState.fromNbt(new CompoundTag()); UUID owner = UUID.randomUUID(), entityId = UUID.randomUUID();
        var data = state.getPlayerData(owner); data.setTradeRouteInt("route_count", 1);
        data.setTradeRouteInt("route_0_x", 1000); data.setTradeRouteInt("route_0_z", 1000);
        var member = CaravanCrewLifecycle.resolve(data, 0, CaravanRole.MASTER);
        var reloaded = QuestState.fromNbt(QuestState.toNbt(state));
        var entity = mock(de.quest.entity.CaravanMerchantEntity.class);
        when(entity.getUUID()).thenReturn(entityId); when(entity.getCrewRole()).thenReturn(CaravanRole.MASTER);
        when(entity.entityTags()).thenReturn(Set.of("vq_trade_route_caravan", "vq_crew_member_" + member.id()));
        when(entity.blockPosition()).thenReturn(new BlockPos(0, 64, 0));
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        var routeClass = Class.forName("de.quest.caravan.TradeRouteService$RouteKey");
        var constructor = routeClass.getDeclaredConstructor(UUID.class, int.class); constructor.setAccessible(true);
        var field = TradeRouteService.class.getDeclaredField("ENTITY_ROUTES"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var map = (Map<UUID, Object>) field.get(null);
        map.put(entityId, constructor.newInstance(owner, 0));
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            assertEquals(VillageKey.overworld(1000, 1000), TradeRouteService.socialVictim(world, entity).village());
            var unrelated = VillageKey.overworld(0, 0); state.protectedVillages().upsert(unrelated, 64);
            assertEquals(VillageKey.overworld(1000, 1000), TradeRouteService.socialVictim(world, entity).village());
            assertNotEquals(unrelated, TradeRouteService.socialVictim(world, entity).village());
            lookup.when(() -> QuestState.get(server)).thenReturn(reloaded);
            assertEquals(VillageKey.overworld(1000, 1000), TradeRouteService.socialVictim(world, entity).village());
            reloaded.getPlayerData(owner).setTradeRouteInt("route_count", 0);
            assertNull(TradeRouteService.socialVictim(world, entity));
        } finally { map.remove(entityId); }
    }

    @Test void affectedNoticeBoardCanCreateTheFirstAdministrativeReconciliation() {
        var state = QuestState.fromNbt(new CompoundTag()); UUID id = UUID.randomUUID();
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class); var player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(id); when(player.level()).thenReturn(world); when(player.isAlive()).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(net.minecraft.world.entity.player.Inventory.class));
        when(player.blockPosition()).thenReturn(new BlockPos(0, 64, 0));
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.hasChunkAt(any())).thenReturn(true);
        var block = mock(net.minecraft.world.level.block.state.BlockState.class);
        when(world.getBlockState(any())).thenReturn(block); when(block.is(de.quest.registry.ModBlocks.GUILD_NOTICE_POST)).thenReturn(true);
        var village = VillageKey.overworld(0, 0); state.protectedVillages().upsert(village, 64);
        var social = state.getPlayerData(id).socialReputation(); social.setLocalTrust(village, -30);
        var anchor = new ReparationService.InteractionAnchor(ReparationService.AnchorKind.BOARD, "minecraft:overworld", new BlockPos(0, 64, 0), null);
        try (var lookup = mockStatic(QuestState.class); var routes = mockStatic(TradeRouteService.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            assertNull(social.activeCase()); assertTrue(ReparationService.validAnchor(player, anchor));
            ReparationService.open(player, anchor); assertNotNull(social.activeCase());
            assertFalse(ReparationService.beginReconciliation(social, 0));
            assertTrue(ReparationService.validAnchor(player, anchor));
            when(player.blockPosition()).thenReturn(new BlockPos(100, 64, 0));
            assertFalse(ReparationService.validAnchor(player, anchor));
        }
    }

    @Test void malformedIndividualAnchorRemainsAvailableForRepairAcrossRoundTrip() {
        var root = new CompoundTag(); root.putInt("schema", 1); var entries = new ListTag();
        var malformed = new CompoundTag(); malformed.putString("dimension", "minecraft:overworld"); malformed.putString("opaque", "preserve-me");
        entries.add(malformed); root.put("anchors", entries);
        assertEquals(malformed, ProtectedVillageIndex.fromNbt(root).toNbt().getListOrEmpty("anchors").getCompoundOrEmpty(0));
    }

    @Test void historicalAnchorWithoutHeightIsSkippedWhenAnchorChunkIsUnloaded() {
        var index = new ProtectedVillageIndex(); index.upsert(VillageKey.overworld(15, 15));
        var world = mock(ServerLevel.class); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.hasChunkAt(any())).thenReturn(false);
        assertTrue(index.resolve(world, new BlockPos(40, 64, 15)).isEmpty());
        verify(world, never()).getHeight(any(), anyInt(), anyInt());
    }

    @Test void registrationCapturesLoadedAnchorHeightBeforeItsChunkUnloads() {
        var state = QuestState.fromNbt(new CompoundTag());
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.hasChunkAt(any())).thenReturn(true);
        when(world.getHeight(any(), eq(15), eq(15))).thenReturn(64);
        var village = VillageKey.overworld(15, 15);
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            ProtectedVillageIndex.register(world, village);
        }
        when(world.hasChunkAt(any())).thenReturn(false);
        var loaded = ProtectedVillageIndex.fromNbt(state.protectedVillages().toNbt());
        assertEquals(village, loaded.resolve(world, new BlockPos(40, 64, 15)).orElseThrow());
    }

    @Test void historicalAnchorLearnsItsHeightOnNormalChunkLoadAndRetainsItAfterUnload() {
        var state = QuestState.fromNbt(new CompoundTag()); var village = VillageKey.overworld(15, 15);
        state.protectedVillages().upsert(village);
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.hasChunkAt(any())).thenReturn(true);
        when(world.getHeight(any(), eq(15), eq(15))).thenReturn(64);
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            ProtectedVillageIndex.onChunkLoad(world, new net.minecraft.world.level.ChunkPos(0, 0));
        }
        when(world.hasChunkAt(any())).thenReturn(false);
        var loaded = ProtectedVillageIndex.fromNbt(state.protectedVillages().toNbt());
        assertEquals(village, loaded.resolve(world, new BlockPos(40, 64, 15)).orElseThrow());
        assertTrue(loaded.resolve(world, new BlockPos(40, 100, 15)).isEmpty());
    }

    @Test void historicalStartupAlsoCapturesPreviouslySavedAnchorsInAlreadyLoadedChunks() {
        var state = QuestState.fromNbt(new CompoundTag()); var village = VillageKey.overworld(15, 15);
        state.protectedVillages().upsert(village);
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(server.getLevel(net.minecraft.world.level.Level.OVERWORLD)).thenReturn(world);
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.hasChunkAt(any())).thenReturn(true); when(world.getHeight(any(), eq(15), eq(15))).thenReturn(64);
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            ProtectedVillageIndex.primeHistorical(server);
        }
        when(world.hasChunkAt(any())).thenReturn(false);
        assertEquals(village, state.protectedVillages().resolve(world, new BlockPos(40, 64, 15)).orElseThrow());
    }

    @Test void muleKeepsItsConnectionDestinationAfterAnotherRouteIsRemoved() throws Exception {
        var state = QuestState.fromNbt(new CompoundTag()); UUID owner = UUID.randomUUID(), entityId = UUID.randomUUID();
        var data = state.getPlayerData(owner); data.setTradeRouteInt("route_count", 2);
        data.setTradeRouteInt("route_0_x", 100); data.setTradeRouteInt("route_0_z", 100);
        data.setTradeRouteInt("route_1_x", 700); data.setTradeRouteInt("route_1_z", 800);
        var member = CaravanCrewLifecycle.resolveMule(data, 1);
        UUID connection = UUID.fromString(data.getTradeRouteString("route_1_connection_id"));
        var removal = Class.forName("de.quest.caravan.TradeRouteData").getDeclaredMethod("removeRoute", PlayerQuestData.class, int.class, int.class);
        removal.setAccessible(true); assertEquals(true, removal.invoke(null, data, 0, 5));
        var loaded = QuestState.fromNbt(QuestState.toNbt(state));
        var entity = mock(de.quest.entity.CaravanPackMuleEntity.class); when(entity.getUUID()).thenReturn(entityId);
        when(entity.entityTags()).thenReturn(Set.of("vq_trade_route_caravan", "vq_crew_member_" + member.id()));
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class); when(world.getServer()).thenReturn(server);
        var routeClass = Class.forName("de.quest.caravan.TradeRouteService$RouteKey");
        var constructor = routeClass.getDeclaredConstructor(UUID.class, int.class); constructor.setAccessible(true);
        var field = TradeRouteService.class.getDeclaredField("ENTITY_ROUTES"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var map = (Map<UUID, Object>) field.get(null);
        map.put(entityId, constructor.newInstance(owner, 0));
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(loaded);
            var victim = TradeRouteService.socialVictim(world, entity);
            assertEquals(VillageKey.overworld(700, 800), victim.village()); assertEquals(connection, victim.connection());
            assertEquals(member.id(), victim.logicalId()); assertEquals(VictimKind.MULE, victim.kind());
        } finally { map.remove(entityId); }
    }
}
