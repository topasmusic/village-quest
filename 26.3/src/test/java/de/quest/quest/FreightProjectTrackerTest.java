package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.caravan.*;
import de.quest.data.*;
import de.quest.guild.*;
import de.quest.guildtown.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

/** Real saved project/contract states and native journal; no world, route ticks or disk writes. */
final class FreightProjectTrackerTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private static TranslatableContents text(Component value) { return (TranslatableContents)value.getContents(); }

    @Test void journalExposesRealFreightAndProjectPhasesWithoutDeliveringOrExpiringAnything() {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var guilds=VillageGuildState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var shared=GuildTownSharedState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var owner=UUID.randomUUID(); var guild=guilds.create(owner,"Carriers");
        assertTrue(shared.accept(guild.id(),GuildTownSharedProject.COMMON_STOREHOUSE));
        var data=quests.getPlayerData(owner);
        data.setTradeRouteInt("guild_contract_type",1); data.setTradeRouteInt("guild_contract_route",1);
        data.setTradeRouteString("guild_contract_instance",UUID.randomUUID().toString());
        data.setTradeRouteInt("guild_contract_due_day",Integer.MAX_VALUE);
        var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
        when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(owner);
        var inventory=mock(Inventory.class); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContainerSize()).thenReturn(41); when(inventory.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
        when(inventory.getItem(0)).thenReturn(new ItemStack(Items.WHEAT,20));
        // No encounter exists in this fixture; Fabric attachment registration requires mixins.
        try(var encounters=mockStatic(de.quest.ambient.EncounterQuestService.class);
                var q=mockStatic(QuestState.class,CALLS_REAL_METHODS);
                var g=mockStatic(VillageGuildState.class,CALLS_REAL_METHODS);
                var s=mockStatic(GuildTownSharedState.class,CALLS_REAL_METHODS)) {
            q.when(()->QuestState.get(server)).thenReturn(quests);
            g.when(()->VillageGuildState.get(server)).thenReturn(guilds);
            s.when(()->GuildTownSharedState.get(server)).thenReturn(shared);
            var before=Map.copyOf(data.getTradeRouteIntState());
            var entries=QuestTrackerService.journalEntries(world,player);
            assertEquals(2,entries.size(),"Accepted freight and shared work must both be visible");
            var freight=entries.stream().filter(e->e.entry().id().startsWith("special:freight:")).findFirst().orElseThrow();
            var project=entries.stream().filter(e->e.entry().id().startsWith("special:guild_project:")).findFirst().orElseThrow();
            assertEquals("quest.village-quest.tracker.freight.supply",text(freight.entry().shortObjective()).getKey());
            assertEquals(20,text(freight.entry().shortObjective()).getArgs()[0]);
            assertEquals(64,text(freight.entry().shortObjective()).getArgs()[1]);
            assertEquals("quest.village-quest.tracker.project.contribute",text(project.entry().shortObjective()).getKey());
            assertEquals(before,data.getTradeRouteIntState()); assertEquals(20,inventory.getItem(0).getCount());
            data.setTradeRouteFlag("guild_contract_supplied",true);
            var loaded=QuestTrackerService.journalEntries(world,player).stream().filter(e->e.entry().id().equals(freight.entry().id())).findFirst().orElseThrow();
            assertEquals("quest.village-quest.tracker.freight.arrival",text(loaded.entry().shortObjective()).getKey());
            assertEquals(1,text(loaded.entry().shortObjective()).getArgs()[0]);
            assertTrue(TradeContractHold.begin(data,1));
            var held=QuestTrackerService.journalEntries(world,player).stream().filter(e->e.entry().id().equals(freight.entry().id())).findFirst().orElseThrow();
            assertEquals("message.village-quest.trade_guild.contract_settlement_hold",text(held.entry().shortObjective()).getKey());
            assertTrue(shared.contribute(guild.id(),owner,UUID.randomUUID(),96).completedNow());
            var ready=QuestTrackerService.journalEntries(world,player);
            assertFalse(ready.stream().anyMatch(e->e.entry().id().equals(project.entry().id())));
            assertTrue(ready.stream().anyMatch(e->text(e.entry().shortObjective()).getKey().equals("quest.village-quest.tracker.project.claim")));
            assertEquals(1,shared.pendingClaims(owner),"Reading does not collect the earned reward");
            guilds.leave(owner);
            assertTrue(QuestTrackerService.journalEntries(world,player).stream().anyMatch(e->e.entry().id().equals("special:guild_project_rewards")),
                    "Leaving a guild cannot hide earned personal claims");
            TradeContractHold.end(data,1); data.setTradeRouteInt("guild_contract_due_day",0);
            var expiredBefore=Map.copyOf(data.getTradeRouteIntState());
            var expired=QuestTrackerService.journalEntries(world,player).stream().filter(e->e.entry().id().equals(freight.entry().id())).findFirst().orElseThrow();
            assertEquals("message.village-quest.trade_guild.contract_expired",text(expired.entry().shortObjective()).getKey());
            assertEquals(expiredBefore,data.getTradeRouteIntState(),"Reading cannot expire/refund accepted freight");
            assertEquals(0,data.getCurrencyBalance()); assertEquals(20,inventory.getItem(0).getCount());
        }
    }
}
