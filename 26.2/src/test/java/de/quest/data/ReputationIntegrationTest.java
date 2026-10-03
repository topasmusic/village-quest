package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.caravan.*;
import de.quest.reputation.*;
import de.quest.reputation.ReputationDamageAdapter.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;

final class ReputationIntegrationTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components = net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : List.of(Items.IRON_INGOT, Items.WHEAT)) item.builtInRegistryHolder().bindComponents(components);
    }
    @Test void foreignAttackerGetsTheCaseWhileOwnerKeepsSafeFreightAndSurvivingCrewIdentityAcrossReload() {
        UUID owner = UUID.randomUUID(), attacker = UUID.randomUUID(); var state = QuestState.fromNbt(new CompoundTag());
        var ownerData = state.getPlayerData(owner); ownerData.setTradeRouteInt("route_count", 1);
        ownerData.setTradeRouteInt("guild_contract_route", 1); ownerData.setTradeRouteInt("guild_contract_type", 1);
        ownerData.setTradeRouteFlag("guild_contract_supplied", true);
        var master = CaravanCrewLifecycle.resolve(ownerData, 0, CaravanRole.MASTER);
        var guard = CaravanCrewLifecycle.resolve(ownerData, 0, CaravanRole.GUARD);
        var village = VillageKey.overworld(100, 100); var offender = state.getPlayerData(attacker).socialReputation();
        assertEquals(ReputationIncidentService.Outcome.FATAL, ReputationIncidentService.record(offender,
                new DamageEvidence(attacker, master.id(), VictimKind.CREW, village, 20, true, 10)).outcome());
        assertTrue(CaravanCrewLifecycle.recordDeath(ownerData, 0, CaravanRole.MASTER, master.id()));
        assertTrue(TradeGuildService.recordCrewLoss(ownerData, 0));
        var loaded = QuestState.fromNbt(QuestState.toNbt(state)); ownerData = loaded.getPlayerData(owner); offender = loaded.getPlayerData(attacker).socialReputation();
        assertEquals(0, ownerData.socialReputation().guildTrust()); assertNull(ownerData.socialReputation().activeCase());
        assertEquals(1, TradeContractRefundLedger.peek(ownerData).size());
        assertEquals(-60, offender.guildTrust()); assertEquals(-70, offender.localTrust(village));
        assertFalse(CaravanCrewLifecycle.onSafeDeparture(ownerData, 0, false));
        assertTrue(CaravanCrewLifecycle.onSafeDeparture(ownerData, 0, true));
        assertNotEquals(master.id(), CaravanCrewLifecycle.resolve(ownerData, 0, CaravanRole.MASTER).id());
        assertEquals(guard.id(), CaravanCrewLifecycle.resolve(ownerData, 0, CaravanRole.GUARD).id());
        assertEquals(ReputationIncidentService.Outcome.IGNORED, ReputationIncidentService.record(offender,
                new DamageEvidence(attacker, master.id(), VictimKind.CREW, village, 20, true, 20)).outcome());
    }
    @Test void playerWithoutRoutesCanSubmitExactPartialAidAtAnExistingQuestmasterAndStaleReplayConsumesNothing() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class); ServerLevel world = mock(ServerLevel.class); ServerPlayer player = mock(ServerPlayer.class);
        UUID playerId = UUID.randomUUID(), masterId = UUID.randomUUID();
        var master = mock(de.quest.entity.QuestMasterEntity.class); Inventory inventory = mock(Inventory.class);
        when(player.getUUID()).thenReturn(playerId); when(player.level()).thenReturn(world); when(player.isAlive()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory); when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.hasChunkAt(any())).thenReturn(true); when(world.getEntity(masterId)).thenReturn(master);
        when(master.isAlive()).thenReturn(true); when(master.distanceToSqr(player)).thenReturn(4.0);
        var field = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu"); field.setAccessible(true); field.set(player, mock(InventoryMenu.class));
        ItemStack[] slots = new ItemStack[41]; Arrays.fill(slots, ItemStack.EMPTY); slots[0] = new ItemStack(Items.IRON_INGOT, 2); slots[1] = new ItemStack(Items.IRON_INGOT, 4);
        when(inventory.getContainerSize()).thenReturn(41); when(inventory.getItem(anyInt())).thenAnswer(call -> slots[call.getArgument(0, Integer.class)]);
        var state = QuestState.fromNbt(new CompoundTag()); var social = state.getPlayerData(playerId).socialReputation();
        social.setGuildTrust(-30); social.openCase(UUID.randomUUID(), Offence.ASSAULT, null, 0);
        UUID caseId = social.activeCase().id(); ReparationService.choose(social, caseId, social.revision(), MaterialOption.IRON);
        social.advanceOnlineTicks(12_000); for (int i = 0; i < 12_000; i++) state.advanceSocialServerTick();
        long revision = social.revision(); var anchor = new ReparationService.InteractionAnchor(ReparationService.AnchorKind.QUESTMASTER, "minecraft:overworld", BlockPos.ZERO, masterId);
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            assertTrue(ReparationService.submit(player, caseId, revision, anchor).applied());
            assertNull(social.activeCase()); assertEquals(-10, social.guildTrust()); assertEquals(2, social.probationRemaining());
            assertEquals(0, slots[0].getCount()); assertEquals(2, slots[1].getCount());
            assertFalse(ReparationService.submit(player, caseId, revision, anchor).applied()); assertEquals(2, slots[1].getCount());
            assertEquals(12_000, social.history().getLast().tick());
            assertEquals(0, state.getPlayerData(playerId).getCurrencyBalance()); assertEquals(0, state.getPlayerData(playerId).getReputation("crafting"));
        }
    }
    @Test void disablingAndReenablingPreservesTheCaseAndConsumesDisabledCompletionWithoutBankingIt() {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(server.overworld()).thenReturn(world); when(world.getServer()).thenReturn(server);
        UUID player = UUID.randomUUID(); var state = QuestState.fromNbt(new CompoundTag());
        var social = state.getPlayerData(player).socialReputation(); social.setGuildTrust(80);
        social.openCase(UUID.randomUUID(), Offence.ASSAULT, null, 0); UUID caseId = social.activeCase().id();
        var config = mock(de.quest.config.VillageQuestServerConfig.class);
        when(config.resetZone()).thenReturn(java.time.ZoneOffset.UTC);
        when(config.dailyResetHour()).thenReturn(6);
        when(config.socialReputation()).thenReturn(new de.quest.config.VillageQuestServerConfig.SocialReputationConfig(false, false, true));
        var event = new BenefitEvent(UUID.randomUUID(), BenefitKind.WEEKLY, player, List.of(), de.quest.util.TimeUtil.currentDay());
        try (var lookup = mockStatic(QuestState.class); var configs = mockStatic(de.quest.config.VillageQuestServerConfig.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            configs.when(de.quest.config.VillageQuestServerConfig::get).thenReturn(config);
            assertEquals(0, SocialReputationService.recordBenefit(server, event).guildDelta());
            assertEquals(ReputationIncidentService.Outcome.IGNORED, ReputationIncidentService.record(server,
                    new DamageEvidence(player, UUID.randomUUID(), VictimKind.CREW, null, 20, true, 1)).outcome());
            assertEquals(4, SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, social, null).caravanBuyLimit());
            lookup.when(() -> QuestState.toNbt(state)).thenCallRealMethod();
            lookup.when(() -> QuestState.fromNbt(any(CompoundTag.class))).thenCallRealMethod();
            var loaded = QuestState.fromNbt(QuestState.toNbt(state)); lookup.when(() -> QuestState.get(server)).thenReturn(loaded);
            when(config.socialReputation()).thenReturn(new de.quest.config.VillageQuestServerConfig.SocialReputationConfig(true, false, true));
            var restored = loaded.getPlayerData(player).socialReputation();
            assertEquals(caseId, restored.activeCase().id()); assertEquals(80, restored.guildTrust());
            assertFalse(SocialReputationService.recordBenefit(server, event).applied());
            assertEquals(0, SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, restored, null).caravanBuyLimit());
            assertEquals(1, SocialReputationService.recordBenefit(server, new BenefitEvent(UUID.randomUUID(), BenefitKind.DAILY,
                    player, List.of(), de.quest.util.TimeUtil.currentDay())).guildDelta());
            assertEquals(caseId, restored.activeCase().id());
        }
    }
}
