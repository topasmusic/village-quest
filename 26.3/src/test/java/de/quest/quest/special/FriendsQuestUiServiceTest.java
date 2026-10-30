package de.quest.quest.special;

import de.quest.network.FriendsQuestPayloads.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class FriendsQuestUiServiceTest {
    private View view(UUID id,long revision,int chapter,boolean active,boolean enabled) {
        var c=Component.literal("test");return new View(id,revision,false,true,"simon",chapter,active,c,c,c,List.of(),c,List.of(),List.of(),List.of(new Option(active?"claim":"accept",c,enabled)));
    }
    @Test void onlyTheDisplayedEnabledActionAtTheSameStageCanExecute() {
        var id=UUID.randomUUID();var shown=view(id,7,1,true,true);var action=new Action(id,7,"claim");
        assertTrue(FriendsQuestUiService.matches(shown,action,shown));
        assertFalse(FriendsQuestUiService.matches(shown,new Action(id,6,"claim"),shown));
        assertFalse(FriendsQuestUiService.matches(shown,new Action(UUID.randomUUID(),7,"claim"),shown));
        assertFalse(FriendsQuestUiService.matches(shown,new Action(id,7,"accept"),shown));
        assertFalse(FriendsQuestUiService.matches(shown,action,view(id,7,2,false,true)));
        assertFalse(FriendsQuestUiService.matches(shown,action,view(id,7,1,true,false)));
    }
    @Test void paidFriendReplayHidesOriginalMoneyExperienceAndRewardCaption() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:List.of(net.minecraft.world.item.Items.DANDELION,net.minecraft.world.item.Items.POPPY,net.minecraft.world.item.Items.FERN))
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
        var state=de.quest.data.QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new net.minecraft.nbt.CompoundTag()).getOrThrow();
        var owner=UUID.randomUUID();var data=state.getPlayerData(owner);de.quest.data.PersonalResetReceipts.friendPaidThrough(data,"simon",3);
        var world=mock(net.minecraft.server.level.ServerLevel.class);var server=mock(net.minecraft.server.MinecraftServer.class);when(world.getServer()).thenReturn(server);
        var player=mock(net.minecraft.server.level.ServerPlayer.class);when(player.getUUID()).thenReturn(owner);
        var bag=mock(net.minecraft.world.entity.player.Inventory.class);when(player.getInventory()).thenReturn(bag);
        when(bag.getItem(anyInt())).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        try(var states=mockStatic(de.quest.data.QuestState.class,CALLS_REAL_METHODS);var goods=mockStatic(FriendsQuestRewards.class)) {
            states.when(()->de.quest.data.QuestState.get(server)).thenReturn(state);
            goods.when(()->FriendsQuestRewards.create(any(),eq("simon"),eq(1),eq(false),eq(owner))).thenReturn(new Rewards(10,1.5,List.of(),false));
            var shown=FriendsQuestUiService.snapshot(world,player,UUID.randomUUID(),1,false,true,"simon",Component.literal("Simon"),new FriendsQuestProgress(data));
            assertEquals(Rewards.EMPTY,shown.rewards());assertTrue(shown.reward().getString().contains("personal_reset.replay"));
        }
    }
}
