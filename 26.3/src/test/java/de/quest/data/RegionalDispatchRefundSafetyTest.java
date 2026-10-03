package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.caravan.RegionalDispatchLedger;
import de.quest.caravan.RegionalDispatchService;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

final class RegionalDispatchRefundSafetyTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Items.BREAD.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build());
    }
    @Test void fullOrdinaryInventoryKeepsTheWholeClaimEvenWithEmptyEquipmentSlots() throws Exception {
        var fixture = new Fixture(); fixture.claim();
        assertNotNull(RegionalDispatchLedger.read(fixture.data()));
        assertEquals(8, RegionalDispatchLedger.read(fixture.data()).amount());
        assertTrue(fixture.slots[40].isEmpty());
    }
    @Test void partialClaimSurvivesReloadAndOnlyRemainingFreightIsReturned() throws Exception {
        var fixture = new Fixture(); fixture.slots[0] = new ItemStack(Items.BREAD, 61);
        fixture.claim(); assertEquals(64, fixture.slots[0].getCount());
        assertEquals(5, RegionalDispatchLedger.read(fixture.data()).amount());
        fixture.state = QuestState.fromNbt(QuestState.toNbt(fixture.state)); fixture.slots[1] = ItemStack.EMPTY;
        fixture.claim(); assertEquals(5, fixture.slots[1].getCount()); assertNull(RegionalDispatchLedger.read(fixture.data()));
        fixture.claim(); assertEquals(5, fixture.slots[1].getCount());
    }
    private static final class Fixture {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        final UUID id = UUID.randomUUID(); final MinecraftServer server = mock(MinecraftServer.class);
        final ServerLevel world = mock(ServerLevel.class); final ServerPlayer player = mock(ServerPlayer.class);
        final ItemStack[] slots = new ItemStack[41];
        Fixture() throws Exception {
            for (int i = 0; i < 36; i++) slots[i] = new ItemStack(Items.BREAD, 64);
            for (int i = 36; i < slots.length; i++) slots[i] = ItemStack.EMPTY;
            var inventory = mock(Inventory.class); when(player.getUUID()).thenReturn(id);
            when(world.getServer()).thenReturn(server); when(player.getInventory()).thenReturn(inventory);
            when(inventory.getContainerSize()).thenReturn(slots.length);
            when(inventory.getItem(anyInt())).thenAnswer(call -> slots[call.getArgument(0, Integer.class)]);
            doAnswer(call -> { slots[call.getArgument(0, Integer.class)] = call.getArgument(1); return null; }).when(inventory).setItem(anyInt(), any());
            var menu = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu"); menu.setAccessible(true); menu.set(player, mock(InventoryMenu.class));
            UUID source = UUID.randomUUID(), target = UUID.randomUUID();
            assertTrue(RegionalDispatchLedger.start(data(), new RegionalDispatchLedger.Dispatch(UUID.randomUUID(), source, target,
                    "minecraft:overworld", 0, 0, 100, 100, 1, 1, "minecraft:bread", 8, 12, RegionalDispatchLedger.Stage.WAITING_AT_SOURCE)));
            assertTrue(RegionalDispatchLedger.cancelForRemovedRoute(data(), source));
        }
        PlayerQuestData data() { return state.getPlayerData(id); }
        void claim() throws Exception {
            var method = RegionalDispatchService.class.getDeclaredMethod("claim", ServerLevel.class, ServerPlayer.class); method.setAccessible(true);
            try (var lookup = mockStatic(QuestState.class)) {
                lookup.when(() -> QuestState.get(server)).thenReturn(state); method.invoke(null, world, player);
            }
        }
    }
}
