package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

final class MariusPeacefulSuppliesTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:List.of(Items.TORCH,Items.IRON_INGOT,Items.BREAD,Items.SHIELD))
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    @Test void eachPeacefulStageUsesRealSuppliesInsteadOfFabricatedKillsHealingOrVictory() {
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);
        var id=UUID.randomUUID();var state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var data=state.getPlayerData(id);var p=new FriendsQuestProgress(data);
        when(world.getServer()).thenReturn(server);when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(player.getUUID()).thenReturn(id);when(player.level()).thenReturn(world);
        var inventory=mock(net.minecraft.world.entity.player.Inventory.class);when(player.getInventory()).thenReturn(inventory);
        var slots=new ArrayList<ItemStack>(Collections.nCopies(36,ItemStack.EMPTY));
        when(inventory.getItem(anyInt())).thenAnswer(c->slots.get(c.getArgument(0)));
        when(inventory.getContainerSize()).thenReturn(36);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
                var friends=mockStatic(FriendsQuestService.class,CALLS_REAL_METHODS);
                var rewards=mockStatic(de.quest.reward.VanillaRewardService.class);
                var previews=mockStatic(FriendsQuestRewards.class)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            previews.when(()->FriendsQuestRewards.create(any(),eq("marius"),anyInt(),eq(false),eq(id)))
                    .thenReturn(de.quest.network.FriendsQuestPayloads.Rewards.EMPTY);
            var bundles=List.of(Map.of(Items.TORCH,16),Map.of(Items.IRON_INGOT,8),Map.of(Items.BREAD,16,Items.SHIELD,1));
            for(int stage=0;stage<3;stage++) {
                assertEquals(stage,p.stage("marius"));assertTrue(p.accept("marius",0,0,0));
                assertFalse(FriendsQuestService.ready(world,player,"marius"));
                int slot=0;for(var cost:bundles.get(stage).entrySet())slots.set(slot++,new ItemStack(cost.getKey(),cost.getValue()));
                assertTrue(FriendsQuestService.ready(world,player,"marius"),"Peaceful stage "+stage+" must have a legal completion");
                when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
                assertFalse(FriendsQuestService.ready(world,player,"marius"),"Supplies do not grant combat progress after a mode switch");
                when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
                var shown=FriendsQuestUiService.snapshot(world,player,UUID.randomUUID(),1,true,true,"marius",Component.literal("Marius"),p);
                assertEquals("quest.village-quest.friends.marius.peaceful.dialogue."+stage,((TranslatableContents)shown.dialogue().getContents()).getKey());
                assertEquals(bundles.get(stage).size(),shown.progress().size());
                assertTrue(shown.options().stream().noneMatch(o->o.action().equals("ready") || o.action().equals("prepare")));
                assertTrue(shown.options().stream().anyMatch(o->o.action().equals("claim") && o.enabled()));
                if(stage==2) {
                    int shield=java.util.stream.IntStream.range(0,36).filter(i->slots.get(i).is(Items.SHIELD)).findFirst().orElseThrow();
                    var held=slots.set(shield,ItemStack.EMPTY);
                    assertFalse(FriendsQuestService.ready(world,player,"marius"));
                    assertFalse(MariusQuestService.turnIn(world,player,p));
                    assertEquals(16,slots.stream().filter(s->s.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum());
                    slots.set(shield,held);
                }
                assertEquals("quest.village-quest.tracker.meet_friend",((TranslatableContents)FriendsQuestService.shortObjective(player,p,"marius").getContents()).getKey());
                var before=Map.copyOf(data.getTradeRouteIntState());
                friends.when(()->FriendsQuestService.enqueue(eq(world),eq(player),anyList())).thenReturn(false);
                assertFalse(MariusQuestService.turnIn(world,player,p));
                assertEquals(before,data.getTradeRouteIntState());
                assertFalse(slots.getFirst().isEmpty(),"Queue refusal must not consume supplies");
                friends.when(()->FriendsQuestService.enqueue(eq(world),eq(player),anyList())).thenReturn(true);
                assertTrue(MariusQuestService.turnIn(world,player,p));
                assertEquals(stage+1,p.stage("marius"));assertTrue(slots.stream().allMatch(ItemStack::isEmpty));
                assertFalse(MariusQuestService.turnIn(world,player,p),"Retry cannot complete a second stage");
                assertTrue(de.quest.data.PersonalResetReceipts.friendPaid(data,"marius",stage));
                assertEquals(0,p.number("marius.healed"));assertEquals(0,p.number("marius.victory"));
                assertTrue(p.values("marius.nightKills").isEmpty());assertTrue(p.values("marius.raiders").isEmpty());
            }
            assertEquals(32,data.getCurrencyBalance());
        }
    }
    @Test void peacefulCannotScheduleACombatRaid() {
        var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);
        when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        var p=new FriendsQuestProgress(new PlayerQuestData());p.number("marius.stage",2);assertTrue(p.accept("marius",0,0,0));
        try(var friends=mockStatic(FriendsQuestService.class,CALLS_REAL_METHODS)) {
            friends.when(()->FriendsQuestService.changed(world,player)).thenAnswer(c->null);
            assertFalse(MariusQuestService.schedule(world,player,p,UUID.randomUUID(),net.minecraft.core.BlockPos.ZERO));
        }
        assertEquals(0,p.number("marius.raidPhase"));assertEquals("",p.text("marius.raidDue"));
    }
}
