package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.daily.*;
import de.quest.quest.weekly.*;
import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

final class PeacefulRepeatableQuestTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for (var item : List.of(Items.COBBLESTONE, Items.TORCH, Items.OAK_FENCE, Items.COAL, Items.BREAD))
            item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE, 64).build());
    }
    record Delivery(Item item, int count) {}
    @Test void eachCombatDailyCanBeFinishedWithoutMobsAndConsumesOnlyItsRealSupplies() throws Exception {
        var cases = Map.of(DailyQuestService.DailyQuestType.ZOMBIE_CULL, new Delivery(Items.COBBLESTONE,16),
                DailyQuestService.DailyQuestType.SKELETON_PATROL, new Delivery(Items.TORCH,16),
                DailyQuestService.DailyQuestType.SPIDER_SWEEP, new Delivery(Items.OAK_FENCE,8),
                DailyQuestService.DailyQuestType.CREEPER_WATCH, new Delivery(Items.COAL,12));
        for (var entry : cases.entrySet()) try (var f = new Fixture()) {
            var def = DailyQuestGenerator.definition(entry.getKey()); var cargo = entry.getValue();
            f.slots[0] = new ItemStack(cargo.item(),cargo.count()-1);
            assertFalse(def.isComplete(f.world,f.player));
            assertFalse(def.consumeCompletionRequirements(f.world,f.player), "Incomplete delivery must not succeed");
            assertEquals(cargo.count()-1,f.slots[0].getCount());
            f.slots[0].grow(1);
            assertTrue(def.isComplete(f.world,f.player),entry.getKey().name());
            assertTrue(QuestObjectiveProgress.complete(def.progressLine(f.world,f.owner)),
                    "Having the supplies marks the material goal ready before explicit turn-in");
            assertEquals(QuestCompletionMode.QUESTMASTER_TURN_IN,def.completionMode(f.world));
            assertTrue(def.consumeCompletionRequirements(f.world,f.player));
            assertTrue(f.slots[0].isEmpty()); assertFalse(def.isComplete(f.world,f.player));
            assertEquals("quest.village-quest.repeatable.peaceful.offer",
                    ((net.minecraft.network.chat.contents.TranslatableContents)def.descriptionLines(f.world,f.owner).getFirst().getContents()).getKey());
            var normalReward = def.buildCompletion(f.world); var peacefulReward = def.completionFor(f.world);
            assertEquals(normalReward.currencyReward(),peacefulReward.currencyReward());
            assertEquals(normalReward.levels(),peacefulReward.levels());
            assertEquals(normalReward.reputationTrack(),peacefulReward.reputationTrack());
            when(f.world.getDifficulty()).thenReturn(Difficulty.NORMAL);
            f.slots[0] = new ItemStack(cargo.item(),cargo.count());
            assertFalse(def.isComplete(f.world,f.player),"Combat mode still requires actual kills");
        }
    }
    @Test void bothCombatWeekliesValidateEverySupplyBeforeConsumingEitherStack() throws Exception {
        for (var type : List.of(WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH,WeeklyQuestService.WeeklyQuestType.ROAD_WARDEN))
            try (var f = new Fixture()) {
                var def = WeeklyQuestGenerator.definition(type);
                boolean night = type == WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH;
                Item first = night ? Items.COBBLESTONE : Items.BREAD, second = night ? Items.TORCH : Items.OAK_FENCE;
                f.slots[0] = new ItemStack(first,32); f.slots[1] = new ItemStack(second,15);
                assertFalse(def.isComplete(f.world,f.player));
                assertFalse(def.consumeCompletionRequirements(f.world,f.player));
                assertEquals(32,f.slots[0].getCount()); assertEquals(15,f.slots[1].getCount());
                f.slots[1].grow(1); assertTrue(def.isComplete(f.world,f.player));
                assertTrue(def.consumeCompletionRequirements(f.world,f.player));
                assertTrue(f.slots[0].isEmpty()); assertTrue(f.slots[1].isEmpty());
            }
    }
    @Test void readyPeacefulDailyAndWeeklyWaitForAnExplicitClaimWithoutChangingRewardCaps() throws Exception {
        try (var f = new Fixture()) {
            f.data.setTradeRouteFlag("guild_intro.first_daily_completed",true);
            f.data.setDailyChoice(DailyQuestService.DailyQuestType.ZOMBIE_CULL);
            f.data.setAcceptedDay(1); f.data.setProgressDay(1);
            f.data.setDailyInt(DailyQuestKeys.ZOMBIE_CULL_PROGRESS,3);
            f.slots[0] = new ItemStack(Items.COBBLESTONE,32); f.slots[1] = new ItemStack(Items.TORCH,16);
            f.data.setWeeklyChoice(WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH);
            f.data.setWeeklyAcceptedCycle(1); f.data.setWeeklyProgressCycle(1);
            assertTrue(DailyQuestService.isQuestReady(f.world,f.player,false));
            assertTrue(WeeklyQuestService.isQuestReady(f.world,f.player));
            assertFalse(DailyQuestService.completeIfEligible(f.world,f.player));
            assertFalse(WeeklyQuestService.completeIfEligible(f.world,f.player));
            assertEquals(32,f.slots[0].getCount()); assertEquals(16,f.slots[1].getCount());
            assertEquals(PlayerQuestData.UNSET_DAY,f.data.getLastRewardDay());
            assertEquals(PlayerQuestData.UNSET_DAY,f.data.getWeeklyRewardCycle());
            when(f.world.getDifficulty()).thenReturn(Difficulty.NORMAL);
            assertFalse(DailyQuestService.isQuestReady(f.world,f.player,false));
            assertFalse(WeeklyQuestService.isQuestReady(f.world,f.player));
            assertEquals(3,f.data.getDailyInt(DailyQuestKeys.ZOMBIE_CULL_PROGRESS),"Mode changes preserve actual combat work");
        }
    }
    private static final class Fixture implements AutoCloseable {
        final UUID owner = UUID.randomUUID();
        final ServerLevel world = mock(ServerLevel.class); final ServerPlayer player = mock(ServerPlayer.class);
        final ItemStack[] slots = new ItemStack[41]; final org.mockito.MockedStatic<QuestState> lookup;
        final PlayerQuestData data;
        Fixture() throws Exception {
            var server = mock(MinecraftServer.class); var players = mock(PlayerList.class); var inventory = mock(Inventory.class);
            when(world.getServer()).thenReturn(server); when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
            when(server.getPlayerList()).thenReturn(players); when(players.getPlayer(owner)).thenReturn(player);
            when(player.getUUID()).thenReturn(owner); when(player.getInventory()).thenReturn(inventory);
            Arrays.fill(slots,ItemStack.EMPTY); when(inventory.getContainerSize()).thenReturn(slots.length);
            when(inventory.getItem(anyInt())).thenAnswer(c -> slots[c.getArgument(0,Integer.class)]);
            var menu = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
            menu.setAccessible(true); menu.set(player,mock(InventoryMenu.class));
            var quests = QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
            data = quests.getPlayerData(owner);
            lookup = mockStatic(QuestState.class,CALLS_REAL_METHODS); lookup.when(() -> QuestState.get(server)).thenReturn(quests);
        }
        public void close() { lookup.close(); }
    }
}
