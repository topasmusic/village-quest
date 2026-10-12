package de.quest.reward;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.pilgrim.*;
import de.quest.quest.daily.DailyQuestService;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

final class PeacefulEndRewardTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        for (var item : java.util.List.of(Items.LANTERN,Items.CREEPER_HEAD,Items.ZOMBIE_HEAD,Items.SPIDER_EYE,Items.BLAZE_ROD,Items.GHAST_TEAR,Items.SHULKER_SHELL)) item.builtInRegistryHolder().bindComponents(components);
    }
    @Test void newPeacefulEndCannotFinishWithChorusOnlyWhileOldContractCan() throws Exception {
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);when(player.level()).thenReturn(world);
        var load=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);load.setAccessible(true);var state=(QuestState)load.invoke(null,new CompoundTag());var data=state.getPlayerData(id);
        data.setPilgrimInt("pilgrim_objective_mode",2);data.setPilgrimInt("pilgrim_expedition_entered",1);data.setPilgrimInt("pilgrim_expedition_distance",320);data.setPilgrimInt("pilgrim_expedition_checkpoints",3);data.setPilgrimInt("pilgrim_expedition_returned",1);
        var definitions=PilgrimContractService.class.getDeclaredField("DEFINITIONS");definitions.setAccessible(true);var definition=((Map<?,?>)definitions.get(null)).get(PilgrimContractType.STILLNESS_BEYOND_THE_GATE);
        var complete=definition.getClass().getDeclaredMethod("isComplete",ServerLevel.class,ServerPlayer.class);complete.setAccessible(true);
        try(var lookup=mockStatic(QuestState.class);var inventory=mockStatic(DailyQuestService.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);inventory.when(() -> DailyQuestService.countInventoryItem(player,Items.CHORUS_FRUIT)).thenReturn(12);
            assertEquals(true,complete.invoke(definition,world,player));
            var snapshot=new CompoundTag();snapshot.putInt("version",1);snapshot.putString("key","pilgrim:stillness_beyond_the_gate");data.vanillaRewards().accept("pilgrim","end:1",snapshot);
            assertEquals(false,complete.invoke(definition,world,player));
            data.setPilgrimFlag("reward.end_city_visited",true);assertEquals(true,complete.invoke(definition,world,player));
        }
    }
}
