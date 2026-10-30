package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.caravan.TradeRouteService;
import de.quest.caravan.VillageLifeService;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.QuestState;
import de.quest.guildtown.GuildTownService;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.network.VillageNetworkPayloads;
import de.quest.registry.ModBlocks;
import de.quest.village.LivingVillageNetworkState;
import de.quest.village.VillageLifeState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

final class VillageNoticeLocalAccessTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var request : VillageRequestType.values()) request.item().builtInRegistryHolder().bindComponents(components);
        for (var item : java.util.List.of(net.minecraft.world.item.Items.OAK_LOG,net.minecraft.world.item.Items.COBBLESTONE,
                net.minecraft.world.item.Items.WHEAT)) item.builtInRegistryHolder().bindComponents(components);
    }

    // Catches a route gate on local offers and network creation caused by merely reading a board.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,2})
    void welcomeContactWithoutAnyRouteReceivesRequestsIndependentlyOfLocalStory(int completions) throws Exception {
        var server = mock(MinecraftServer.class);
        var world = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        var pos = new BlockPos(32, 64, 64);
        var owner = UUID.randomUUID();
        when(world.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(player.getUUID()).thenReturn(owner);
        when(player.level()).thenReturn(world);
        when(player.blockPosition()).thenReturn(pos);
        when(player.isAlive()).thenReturn(true);
        when(world.hasChunkAt(pos)).thenReturn(true);
        var block = mock(BlockState.class);
        when(world.getBlockState(pos)).thenReturn(block);
        when(block.is(ModBlocks.GUILD_NOTICE_POST)).thenReturn(true);
        var inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(36);
        when(inventory.getItem(anyInt())).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        when(player.getInventory()).thenReturn(inventory);
        var menu = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        menu.setAccessible(true); menu.set(player, mock(net.minecraft.world.inventory.InventoryMenu.class));
        var state = emptyState(QuestState.class);
        var data = state.getPlayerData(owner);
        var kingdoms = new LivingKingdomsState();
        var marker = new ShadowsTradeRoadEncounterService.VillageMarker("32_64", 32, 64, 16, 48, 48, 80);
        var contact = VillageContactService.bindCanonical(kingdoms, data, "minecraft:overworld", marker,
                pos, VillageBondType.GRANARY);
        assertTrue(contact.accepted());
        VillageWelcomeService.begin(data, contact.contact().villageIndex());
        for (int i = 0; i < 3; i++) VillageWelcomeService.greet(data, UUID.randomUUID());
        data.setTradeRouteInt(VillageBondService.villageKey(0,"completions"),completions);
        var lifeState = mock(VillageLifeState.class);
        when(lifeState.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
        var networkState = emptyState(LivingVillageNetworkState.class);
        List<VillageNetworkPayloads.NoticeJourneyPayload> journeys = new ArrayList<>();
        try (var states = mockStatic(QuestState.class); var villages = mockStatic(ShadowsTradeRoadEncounterService.class);
             var life = mockStatic(VillageLifeState.class); var observation = mockStatic(VillageLifeService.class);
             var routes = mockStatic(TradeRouteService.class); var stories = mockStatic(GuildTownService.class);
             var ids = mockStatic(LivingKingdomsState.class); var networks = mockStatic(LivingVillageNetworkState.class);
             var networkService = mockStatic(de.quest.village.LivingVillageNetworkService.class);
             var guild = mockStatic(de.quest.guild.VillageGuildService.class);
             var social = mockStatic(de.quest.reputation.SocialReputationService.class);
             var atmosphere = mockStatic(VillageAtmosphereService.class);
             var intro = mockStatic(VillageIntroRequestService.class,CALLS_REAL_METHODS);
             var wire = mockStatic(ServerPlayNetworking.class)) {
            intro.when(() -> VillageIntroRequestService.recoverBeforeActions(player)).thenReturn(true);
            wire.when(() -> ServerPlayNetworking.canSend(eq(player),any(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type.class))).thenReturn(true);
            social.when(de.quest.reputation.SocialReputationService::enabled).thenReturn(true);
            states.when(() -> QuestState.get(server)).thenReturn(state);
            villages.when(() -> ShadowsTradeRoadEncounterService.currentVillage(world, pos)).thenReturn(marker);
            life.when(() -> VillageLifeState.get(server)).thenReturn(lifeState);
            ids.when(() -> LivingKingdomsState.get(server)).thenReturn(kingdoms);
            networks.when(() -> LivingVillageNetworkState.get(server)).thenReturn(networkState);
            wire.when(() -> ServerPlayNetworking.send(eq(player), any())).thenAnswer(call -> {
                if (call.getArgument(1) instanceof VillageNetworkPayloads.NoticeJourneyPayload journey) journeys.add(journey);
                return null;
            });
            VillageNoticeBoardService.use(world, player, pos);
            assertEquals(1, journeys.size());
            assertNotNull(journeys.getFirst().requests(), "Welcome and a canonical contact must suffice without a route or local story");
            assertEquals(3, journeys.getFirst().requests().offers().size());
            var requests = journeys.getFirst().requests();
            assertFalse(requests.networkConnected());
            assertEquals(-1, requests.villageSupport());
            assertTrue(requests.villageNeed().getString().isEmpty());
            assertTrue(requests.offers().stream().allMatch(o -> o.support() == 0 && !o.primaryNeed()));
            if (completions==0) {
                assertNotNull(requests.intro()); assertEquals(3,requests.offers().size());
                assertEquals(contact.contact().villageIndex(),0);
                VillageNoticeBoardService.handleAction(player,new VillageNetworkPayloads.NoticeBoardActionPayload(32,64,64,1,0));
                assertEquals(0,data.getCurrencyBalance());
                assertEquals(0,data.getTradeRouteInt(VillageBondService.villageKey(0,"completions")));
                assertEquals(0,data.getTradeRouteInt("route_count"));
                assertTrue(networkState.villagesView().isEmpty());
                networkService.verifyNoInteractions(); guild.verifyNoInteractions();
                return;
            }
            assertNull(requests.intro());
            var offer = requests.offers().getFirst();
            var stack = offer.stack().copyWithCount(offer.requiredAmount());
            when(inventory.getContainerSize()).thenReturn(1); when(inventory.getItem(0)).thenReturn(stack);
            var action = new VillageNetworkPayloads.NoticeBoardActionPayload(32,64,64,
                    VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_DELIVER, offer.id());
            VillageNoticeBoardService.handleAction(player, action);
            assertTrue(stack.isEmpty());
            assertEquals(offer.reward(), data.getCurrencyBalance());
            assertEquals(completions+1, data.getTradeRouteInt(VillageBondService.villageKey(0,"completions")));
            VillageNoticeBoardService.handleAction(player, action);
            assertEquals(offer.reward(), data.getCurrencyBalance());
            assertEquals(completions+1, data.getTradeRouteInt(VillageBondService.villageKey(0,"completions")));
            assertEquals(0, data.getTradeRouteInt("route_count"));
            assertTrue(networkState.villagesView().isEmpty(), "Viewing local requests must not create a network village");
            networkService.verifyNoInteractions(); guild.verifyNoInteractions();

            when(player.isCreative()).thenReturn(true);
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(player.isCreative()).thenReturn(false); when(player.isSpectator()).thenReturn(true);
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(player.isSpectator()).thenReturn(false); when(player.isAlive()).thenReturn(false);
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(player.isAlive()).thenReturn(true); when(world.hasChunkAt(pos)).thenReturn(false);
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(world.hasChunkAt(pos)).thenReturn(true); when(block.is(ModBlocks.GUILD_NOTICE_POST)).thenReturn(false);
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(block.is(ModBlocks.GUILD_NOTICE_POST)).thenReturn(true); when(player.blockPosition()).thenReturn(pos.east(9));
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(player.blockPosition()).thenReturn(pos.east());
            villages.when(() -> ShadowsTradeRoadEncounterService.currentVillage(world,pos.east())).thenReturn(
                    new ShadowsTradeRoadEncounterService.VillageMarker("other",90,90,80,100,80,100));
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(player.blockPosition()).thenReturn(pos); when(player.level()).thenReturn(mock(ServerLevel.class));
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            when(player.level()).thenReturn(world);
            for (var status : List.of(VillageLifeState.Status.ABANDONED, VillageLifeState.Status.RECOVERING)) {
                when(lifeState.status(any())).thenReturn(status);
                assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            }
            when(lifeState.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
            data.setTradeRouteFlag("guild_intro.welcome_completed",false);
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            routes.when(() -> TradeRouteService.isRegisteredDestination(world,owner,32,64)).thenReturn(true);
            var snapshot=networkState.ensureVillage(owner,0,32,64,VillageBondType.GRANARY);
            networkService.when(() -> de.quest.village.LivingVillageNetworkService.ensureVillage(
                    world,owner,0,32,64,VillageBondType.GRANARY)).thenReturn(snapshot);
            VillageNoticeBoardService.use(world,player,pos);
            var connected=journeys.getLast().requests();
            assertNotNull(connected); assertTrue(connected.networkConnected());
            assertEquals(50,connected.villageSupport());
            assertTrue(connected.offers().stream().allMatch(o->o.support()>0));
            data.setTradeRouteFlag("guild_intro.welcome_completed",true);
            data.socialReputation().openCase(UUID.randomUUID(),de.quest.reputation.SocialReputationRules.Offence.VILLAGER_KILL,
                    new VillageLifeState.VillageKey("minecraft:overworld",32,64),1);
            assertTrue(VillageNoticeBoardService.localContext(world,player,pos).isEmpty());
            VillageNoticeBoardService.use(world,player,pos);
            assertEquals("reputation.village-quest.access.case", ((net.minecraft.network.chat.contents.TranslatableContents)journeys.getLast().detail().getContents()).getKey());
            assertTrue(journeys.getLast().detail().getString().contains("screen.village-quest.relationship.bond"),"Suspended board still shows earned bond");
            assertTrue(journeys.getLast().detail().getString().contains("screen.village-quest.reputation.local_standing"),"Suspended board still shows standing");
        }
    }

    private static <T> T emptyState(Class<T> type) throws Exception {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
