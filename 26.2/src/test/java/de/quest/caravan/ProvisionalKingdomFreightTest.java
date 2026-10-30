package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.kingdom.*;
import de.quest.quest.story.*;
import de.quest.shrine.VillageBondService;
import de.quest.village.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Native services and saved states; world/storage lookups are replaced, no route ticks or game runtime. */
final class ProvisionalKingdomFreightTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for (var type : TradeContractType.values()) type.item().builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64)
                        .set(DataComponents.ITEM_NAME, Component.translatable(type.item().getDescriptionId())).build());
    }

    @ParameterizedTest @CsvSource({"false,false,false", "false,true,false", "true,false,false", "true,true,false",
            "false,false,true", "true,false,true"})
    void eitherPermitCanSupplyAndFinishFreightButOnlyAParticipatingTownGetsStoryCredit(
            boolean charter, boolean homeTarget, boolean unrelated) throws Exception {
        UUID owner = UUID.randomUUID();
        var quests = QuestState.TYPE.codec().parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        var data = quests.getPlayerData(owner);
        var kingdom = new LivingKingdomsState();
        UUID home = town(kingdom, owner, 0), destination = town(kingdom, owner, 500), other = town(kingdom, owner, 1000);
        var life = VillageLifeState.TYPE.codec().parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        var network = LivingVillageNetworkState.TYPE.codec().parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        var world = mock(ServerLevel.class); var server = mock(MinecraftServer.class);
        var player = mock(ServerPlayer.class); var players = mock(PlayerList.class);
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(server.isSameThread()).thenReturn(true); when(server.getPlayerList()).thenReturn(players);
        when(player.getUUID()).thenReturn(owner);
        // Empty live player list exercises the durable offline story path after a real supplied arrival.
        var inventory = mock(Inventory.class); when(player.getInventory()).thenReturn(inventory);
        var menu = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        menu.setAccessible(true); menu.set(player, mock(InventoryMenu.class));
        ItemStack[] slots = new ItemStack[41]; Arrays.fill(slots, ItemStack.EMPTY);
        when(inventory.getContainerSize()).thenReturn(slots.length);
        when(inventory.getItem(anyInt())).thenAnswer(c -> slots[c.getArgument(0, Integer.class)]);
        try (var q = mockStatic(QuestState.class, CALLS_REAL_METHODS); var k = mockStatic(LivingKingdomsState.class);
             var l = mockStatic(VillageLifeState.class); var n = mockStatic(LivingVillageNetworkState.class);
             var villages = mockStatic(VillageBondService.class)) {
            q.when(() -> QuestState.get(server)).thenReturn(quests);
            k.when(() -> LivingKingdomsState.get(server)).thenReturn(kingdom);
            l.when(() -> VillageLifeState.get(server)).thenReturn(life);
            n.when(() -> LivingVillageNetworkState.get(server)).thenReturn(network);
            assertFalse(TradeRouteService.hasRouteAccess(world, owner));
            if (charter) data.setUnlockedProject(VillageProjectType.MARKET_CHARTER.id(), true);
            else data.setStoryChapterProgress("living_kingdoms", 2);
            assertTrue(TradeRouteService.hasRouteAccess(world, owner));
            assertEquals(1, TradeRouteService.routeCapacity(world, owner));
            assertFalse(TradeRouteService.hasCaravanYard(world, owner));
            TradeRouteData.bindVillageHome(data, 0, 64, 0);
            data.setTradeRouteInt("route_count", 1); data.setTradeRouteInt("route_0_x", 500);
            TradeRouteData.ensureConnectionId(data, 0);
            KingdomRouteEndpoints.captureValidated(data, 0, new BlockPos(0, 64, 0), new BlockPos(500, 64, 0));
            assertTrue(KingdomStoryProgress.bind(data, unrelated ? other : homeTarget ? home : destination, false));
            var offers = TradeGuildService.offers(world, owner);
            int choice = -1;
            for (int i = 0; i < offers.size(); i++)
                if (Set.of(Items.WHEAT, Items.STONE_BRICKS, Items.COAL, Items.PAPER).contains(offers.get(i).item())) { choice = i; break; }
            assertTrue(choice >= 0, "A first freight offer must not require specialist farms");
            var contract = offers.get(choice);
            assertTrue(TradeGuildService.acceptContract(world, player, choice + 1, 0));
            TradeGuildService.onRouteArrival(world, owner, 0);
            assertEquals(0, data.getTradeRouteInt("guild_contracts_completed"), "Unsupplied arrival earns nothing");
            slots[0] = new ItemStack(contract.item(), contract.amount() - 1);
            assertFalse(TradeGuildService.supplyContract(world, player));
            assertEquals(contract.amount() - 1, slots[0].getCount(), "Incomplete supplies stay intact");
            slots[0].grow(1); assertTrue(TradeGuildService.supplyContract(world, player));
            assertTrue(slots[0].isEmpty()); assertFalse(TradeGuildService.supplyContract(world, player));
            TradeGuildService.onRouteArrival(world, owner, 1);
            assertEquals(0, data.getTradeRouteInt("guild_contracts_completed"), "Another route cannot consume the contract");
            TradeGuildService.onRouteArrival(world, owner, 0);
            assertEquals(1, data.getTradeRouteInt("guild_contracts_completed"));
            long paid = data.getCurrencyBalance(); assertTrue(paid > 0);
            assertEquals(!unrelated, KingdomStoryProgress.has(data, KingdomStoryProgress.Deed.STORE_DELIVERY));
            TradeGuildService.onRouteArrival(world, owner, 0);
            assertEquals(paid, data.getCurrencyBalance()); assertEquals(1, data.getTradeRouteInt("guild_contracts_completed"));
            var saved = QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE, quests).getOrThrow();
            var loaded = QuestState.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow().getPlayerData(owner);
            assertEquals(paid, loaded.getCurrencyBalance());
            assertEquals(!unrelated, KingdomStoryProgress.has(loaded, KingdomStoryProgress.Deed.STORE_DELIVERY));
        }
    }
    private static UUID town(LivingKingdomsState state, UUID owner, int x) {
        UUID id = UUID.randomUUID();
        state.registerCustom(id, owner, VillageLifeState.VillageKey.overworld(x, 0), new BlockPos(x, 64, 0), "Town " + x).orElseThrow();
        return id;
    }
}
