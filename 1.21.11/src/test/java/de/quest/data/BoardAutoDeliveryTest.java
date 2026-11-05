package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.guildtown.*;
import de.quest.shrine.*;
import de.quest.village.VillageLifeState;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

final class BoardAutoDeliveryTest {
    private final UUID id = UUID.randomUUID();
    private QuestState state; private ServerLevel world; private ServerPlayer player;
    private Inventory inventory; private ItemStack stack; private final AtomicInteger amount = new AtomicInteger();
    private MockedStatic<QuestState> states; private MockedStatic<VillageBondService> bonds;
    private MockedStatic<VillageLifeState> life; private MockedStatic<de.quest.reputation.SocialReputationService> social;
    private MockedStatic<de.quest.quest.QuestBookHelper> book; private MockedStatic<de.quest.quest.QuestTrackerService> tracker;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() throws ReflectiveOperationException {
        state = QuestState.fromNbt(new CompoundTag());
        world = mock(ServerLevel.class); var server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server); when(server.overworld()).thenReturn(world);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        player = mock(ServerPlayer.class); when(player.getUUID()).thenReturn(id); when(player.isAlive()).thenReturn(true);
        when(player.getBlockX()).thenReturn(100); when(player.getBlockZ()).thenReturn(200);
        inventory = mock(Inventory.class); when(player.getInventory()).thenReturn(inventory);
        var menuField = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        menuField.setAccessible(true);
        menuField.set(player, mock(InventoryMenu.class));
        when(inventory.getContainerSize()).thenReturn(1);
        stack = mock(ItemStack.class); when(inventory.getItem(0)).thenReturn(stack);
        when(stack.is(Items.RAIL)).thenReturn(true); when(stack.getCount()).thenAnswer(call -> amount.get());
        doAnswer(call -> { amount.addAndGet(-call.getArgument(0, Integer.class)); return null; }).when(stack).shrink(anyInt());
        states = mockStatic(QuestState.class, call -> call.getMethod().getName().equals("get") ? state : call.callRealMethod());
        life = mockStatic(VillageLifeState.class); var lifeState = mock(VillageLifeState.class);
        life.when(() -> VillageLifeState.get(server)).thenReturn(lifeState);
        when(lifeState.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
        social = mockStatic(de.quest.reputation.SocialReputationService.class);
        book = mockStatic(de.quest.quest.QuestBookHelper.class); tracker = mockStatic(de.quest.quest.QuestTrackerService.class);
        var data = state.getPlayerData(id);
        VillageContactService.establish(data, 100, 200, VillageBondType.FORGE);
        bonds = mockStatic(VillageBondService.class, CALLS_REAL_METHODS);
        bonds.when(() -> VillageBondService.villages(world, id)).thenReturn(List.of());
        GuildTownProgress.beginStory(data, GuildTownStory.SPARKS_FOR_THE_ROAD, 0, GuildTownProgress.PREVENTIVE);
        GuildTownProgress.markStoryReady(data, GuildTownStory.SPARKS_FOR_THE_ROAD);
        GuildTownProgress.grantReward(data, "story_memory_sparks_for_the_road");
    }
    @AfterEach void close() { tracker.close(); book.close(); social.close(); life.close(); bonds.close(); states.close(); }
    @Test void saveReloadThenAutomaticDeliveryConsumesExactlyOnceAndPreservesForeignPlayer() {
        var other = UUID.randomUUID(); state.getPlayerData(other).setCurrencyBalance(88);
        state = QuestState.fromNbt(QuestState.toNbt(state)); amount.set(12);
        GuildTownService.autoCompleteReady(world, player);
        assertEquals(4, amount.get());
        assertTrue(GuildTownProgress.storyCompleted(state.getPlayerData(id), GuildTownStory.SPARKS_FOR_THE_ROAD));
        GuildTownService.autoCompleteReady(world, player);
        assertEquals(4, amount.get());
        verify(stack, times(1)).shrink(8);
        assertEquals(1, GuildTownProgress.personalChronicle(state.getPlayerData(id)).size());
        assertEquals(88, state.getPlayerData(other).getCurrencyBalance());
    }
    @Test void missingBundleAndOutOfRangeNeverConsumeOrFinish() {
        amount.set(7); GuildTownService.autoCompleteReady(world, player);
        assertEquals(7, amount.get());
        assertEquals(GuildTownProgress.READY, GuildTownProgress.storyState(state.getPlayerData(id), GuildTownStory.SPARKS_FOR_THE_ROAD));
        amount.set(8); when(player.getBlockX()).thenReturn(229);
        GuildTownService.autoCompleteReady(world, player);
        assertEquals(8, amount.get()); verify(stack, never()).shrink(anyInt());
    }
    @Test void deadOrSpectatorCannotAutoDeliver() {
        amount.set(8); when(player.isAlive()).thenReturn(false); GuildTownService.autoCompleteReady(world, player);
        when(player.isAlive()).thenReturn(true); when(player.isSpectator()).thenReturn(true); GuildTownService.autoCompleteReady(world, player);
        assertEquals(8, amount.get()); verify(stack, never()).shrink(anyInt());
    }
    @Test void sharedTableCannotSkipItsExplicitChoice() {
        var data = state.getPlayerData(id);
        data.setStoryInt("guild_town.active_story", 0);
        GuildTownProgress.beginStory(data, GuildTownStory.SHARED_TABLE, 0, GuildTownProgress.PREVENTIVE);
        GuildTownProgress.addStoryProgress(data, GuildTownStory.SHARED_TABLE, 1, 24, 24);
        amount.set(64); GuildTownService.autoCompleteReady(world, player);
        assertEquals(GuildTownProgress.ACTIVE, GuildTownProgress.storyState(data, GuildTownStory.SHARED_TABLE));
        verify(stack, never()).shrink(anyInt());
    }
}
