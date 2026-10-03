package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.caravan.TradeContractRefundLedger;
import de.quest.caravan.TradeContractType;
import de.quest.caravan.TradeGuildService;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaravanFreightDeliveryTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components = net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        Items.STONE.builtInRegistryHolder().bindComponents(components);
        for (var type : TradeContractType.values()) type.item().builtInRegistryHolder().bindComponents(components);
    }
    @Test void aFullInventoryRetainsFreightAndLaterFreeSpaceClaimsOnlyWhatFits() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class); ServerLevel world = mock(ServerLevel.class); ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = mock(Inventory.class); UUID id = UUID.randomUUID();
        when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(id); when(player.getInventory()).thenReturn(inventory);
        var menuField = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        menuField.setAccessible(true); menuField.set(player, mock(InventoryMenu.class));
        ItemStack[] slots = new ItemStack[41]; Arrays.setAll(slots, i -> i < 36 ? new ItemStack(Items.STONE, 64) : ItemStack.EMPTY);
        when(inventory.getContainerSize()).thenReturn(41); when(inventory.getItem(anyInt())).thenAnswer(call -> slots[call.getArgument(0, Integer.class)]);
        doAnswer(call -> { slots[call.getArgument(0, Integer.class)] = call.getArgument(1); return null; }).when(inventory).setItem(anyInt(), any(ItemStack.class));
        QuestState state = QuestState.fromNbt(new CompoundTag()); var data = state.getPlayerData(id);
        assertTrue(TradeContractRefundLedger.queue(data, 1, 32));
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            TradeGuildService.deliverPendingRefund(world, player);
            assertEquals(32, TradeContractRefundLedger.peek(data).getFirst().amount()); verify(inventory, never()).setItem(anyInt(), any());
            var cargo = TradeContractType.values()[0].item(); slots[0] = new ItemStack(cargo, new ItemStack(cargo).getMaxStackSize() - 1);
            TradeGuildService.deliverPendingRefund(world, player);
            assertEquals(31, TradeContractRefundLedger.peek(data).getFirst().amount());
            slots[1] = ItemStack.EMPTY; TradeGuildService.deliverPendingRefund(world, player);
            assertTrue(TradeContractRefundLedger.peek(data).isEmpty()); assertEquals(31, slots[1].getCount());
            TradeGuildService.deliverPendingRefund(world, player); assertEquals(31, slots[1].getCount());
        }
    }
}
