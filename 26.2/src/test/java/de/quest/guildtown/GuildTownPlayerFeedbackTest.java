package de.quest.guildtown;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.shrine.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

final class GuildTownPlayerFeedbackTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    @Test void completedPastureFollowUpStaysOnceOnlyAfterWeeklyDailyAndNativeReload() {
        var state=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new net.minecraft.nbt.CompoundTag()).getOrThrow();
        var owner=UUID.randomUUID();var data=state.getPlayerData(owner);
        var contact=VillageContactService.establish(data,10,20,VillageBondType.PASTURE).contact();
        int index=contact.villageIndex();var story=GuildTownStory.LONG_DRIVE;
        assertTrue(GuildTownProgress.beginStory(data,story,index,GuildTownProgress.PREVENTIVE));
        assertTrue(GuildTownProgress.markStoryReady(data,story));assertTrue(GuildTownProgress.completeStory(data,story));
        var request=Arrays.stream(VillageRequestType.values()).filter(r -> r.bondType()==VillageBondType.PASTURE).findFirst().orElseThrow();
        assertTrue(GuildTownAfterstoryLedger.begin(data,index,request));
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);
        when(world.getServer()).thenReturn(server);when(player.getUUID()).thenReturn(owner);
        var inventory=mock(net.minecraft.world.entity.player.Inventory.class);when(player.getInventory()).thenReturn(inventory);
        var resolution=new GuildTownService.NoticePostStoryResolution(GuildTownStoryVillageResolver.fromContact(contact),story,GuildTownService.NoticePostStoryState.REMEMBERED);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            assertEquals(1,GuildTownAfterstoryService.journalEntries(data,player).size());
            assertTrue(GuildTownAfterstoryLedger.complete(data,index));
            for(int cycle:new int[]{10,11,12}) {
                data.setLastRewardDay(cycle);data.setWeeklyRewardCycle(cycle);
                var before=Map.copyOf(data.getTradeRouteIntState());
                var view=GuildTownAfterstoryService.view(world,player,resolution,null);
                assertEquals(GuildTownAfterstoryLedger.State.COMPLETE,view.state());
                assertTrue(view.detail().getSiblings().stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("screen.village-quest.afterstory.completed_hint")));
                assertTrue(GuildTownAfterstoryService.journalEntries(data,player).isEmpty());
                assertEquals(before,data.getTradeRouteIntState());
            }
            var nbt=QuestState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,state).getOrThrow();
            var loaded=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,nbt).getOrThrow().getPlayerData(owner);
            assertEquals(GuildTownAfterstoryLedger.State.COMPLETE,GuildTownAfterstoryLedger.state(loaded,index));
            assertFalse(GuildTownAfterstoryLedger.begin(loaded,index,request));
        }
    }
}
