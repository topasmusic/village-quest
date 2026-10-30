package de.quest.party;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.PeacefulRepeatableSupplies;
import de.quest.quest.daily.*;
import de.quest.quest.weekly.*;
import de.quest.util.QuestCalendarService;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

final class PeacefulPartySuppliesTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for (var item : List.of(Items.COBBLESTONE,Items.TORCH)) item.builtInRegistryHolder().bindComponents(
                net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
    }
    @AfterEach void clear() { QuestPartyService.resetRuntimeState(); }
    @Test void admittedMembersPoolSuppliesAndBothConsumedReceiptsSurviveNativeReload() throws Exception {
        UUID owner=UUID.randomUUID(), peer=UUID.randomUUID();
        var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class); var players=mock(PlayerList.class);
        when(world.getServer()).thenReturn(server); when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        when(server.isDedicatedServer()).thenReturn(true); when(server.getPlayerList()).thenReturn(players);
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var party=new PartyRuntime(UUID.randomUUID(),owner); party.members().addAll(List.of(owner,peer));
        party.daily().bind("ZOMBIE_CULL",20000); party.weekly().bind("NIGHT_WATCH",19999);
        var stocks=new HashMap<UUID,ItemStack[]>(); var actors=new HashMap<UUID,ServerPlayer>();
        for (UUID id : List.of(owner,peer)) {
            var data=quests.getPlayerData(id); data.setTradeRouteFlag("guild_intro.first_daily_completed",true);
            data.setDailyChoice(DailyQuestService.DailyQuestType.ZOMBIE_CULL); data.setAcceptedDay(20000);
            data.setWeeklyChoice(WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH); data.setWeeklyAcceptedCycle(19999);
            party.daily().markSynced(id); party.weekly().markSynced(id);
            var player=mock(ServerPlayer.class); var inv=mock(net.minecraft.world.entity.player.Inventory.class);
            when(player.getUUID()).thenReturn(id); when(player.getInventory()).thenReturn(inv); when(players.getPlayer(id)).thenReturn(player);
            ItemStack[] slots=new ItemStack[41]; Arrays.fill(slots,ItemStack.EMPTY); slots[0]=new ItemStack(Items.COBBLESTONE,8);
            when(inv.getContainerSize()).thenReturn(41); when(inv.getItem(anyInt())).thenAnswer(c -> slots[c.getArgument(0,Integer.class)]);
            var field=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu"); field.setAccessible(true);
            field.set(player,mock(net.minecraft.world.inventory.InventoryMenu.class)); stocks.put(id,slots); actors.put(id,player);
        }
        quests.setQuestPartyState(QuestPartyPersistence.write(Map.of(party.id(),party),Map.of()));
        try(var lookup=mockStatic(QuestState.class,CALLS_REAL_METHODS); var calendar=mockStatic(QuestCalendarService.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(quests);
            calendar.when(QuestCalendarService::currentDay).thenReturn(20000L);
            calendar.when(QuestCalendarService::currentWeekCycle).thenReturn(19999L);
            QuestPartyService.loadPersistentState(server);
            assertTrue(PeacefulRepeatableSupplies.dailyComplete(world,actors.get(owner),DailyQuestService.DailyQuestType.ZOMBIE_CULL));
            assertTrue(PeacefulRepeatableSupplies.consumeDaily(world,actors.get(owner),DailyQuestService.DailyQuestType.ZOMBIE_CULL));
            assertTrue(stocks.get(owner)[0].isEmpty()); assertTrue(stocks.get(peer)[0].isEmpty());
            QuestPartyService.markDailyTurnInRequirementsConsumed(world,owner,DailyQuestService.DailyQuestType.ZOMBIE_CULL);
            for (UUID id : stocks.keySet()) { stocks.get(id)[0]=new ItemStack(Items.COBBLESTONE,16); stocks.get(id)[1]=new ItemStack(Items.TORCH,8); }
            stocks.get(peer)[1].shrink(1);
            assertFalse(PeacefulRepeatableSupplies.consumeWeekly(world,actors.get(owner),WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH));
            assertEquals(16,stocks.get(owner)[0].getCount()); assertEquals(16,stocks.get(peer)[0].getCount());
            stocks.get(peer)[1].grow(1);
            assertTrue(PeacefulRepeatableSupplies.consumeWeekly(world,actors.get(owner),WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH));
            for (var slots : stocks.values()) { assertTrue(slots[0].isEmpty()); assertTrue(slots[1].isEmpty()); }
            QuestPartyService.markWeeklyTurnInRequirementsConsumed(world,owner,WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH);
            var saved=QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow();
            var loaded=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,saved).getOrThrow();
            lookup.when(() -> QuestState.get(server)).thenReturn(loaded); QuestPartyService.loadPersistentState(server);
            assertTrue(QuestPartyService.dailyTurnInRequirementsConsumed(world,peer,DailyQuestService.DailyQuestType.ZOMBIE_CULL));
            assertTrue(QuestPartyService.weeklyTurnInRequirementsConsumed(world,peer,WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH));
        }
    }
}
