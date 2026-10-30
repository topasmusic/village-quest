package de.quest.pilgrim;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.economy.*;
import de.quest.party.QuestPartyService;
import de.quest.quest.*;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.reward.VanillaRewardService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

final class PilgrimPeacefulGuidanceTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private static String key(Component c) { return ((TranslatableContents)c.getContents()).getKey(); }
    private static List<String> keys(Component c) {
        var found=new ArrayList<String>();
        if(c.getContents() instanceof TranslatableContents t) {
            found.add(t.getKey());
            for(var a:t.getArgs()) if(a instanceof Component child) found.addAll(keys(child));
        }
        for(var child:c.getSiblings()) found.addAll(keys(child));
        return found;
    }
    @Test void sevenActiveExpeditionsExplainSavedModeDimensionExactMaterialAndReturnWithoutChangingProgress() {
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        UUID id=UUID.randomUUID(); when(player.getUUID()).thenReturn(id); when(world.getServer()).thenReturn(server);
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow(); var data=quests.getPlayerData(id);
        var materials=List.of(Items.IRON_INGOT,Items.WOOL.white(),Items.REDSTONE,Items.STRING,Items.NETHER_WART,Items.BLACKSTONE,Items.CHORUS_FRUIT);
        try(var states=mockStatic(QuestState.class); var rewards=mockStatic(VanillaRewardService.class);
            var projects=mockStatic(VillageProjectService.class); var inventory=mockStatic(DailyQuestService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            rewards.when(()->VanillaRewardService.withSaved(eq(world),eq(id),eq("pilgrim"),anyList())).thenAnswer(c->c.getArgument(3));
            projects.when(()->VillageProjectService.formatQuestEchoLine(any(),any(),any())).thenReturn(Component.empty());
            for(int i=0;i<7;i++) {
                var type=PilgrimContractType.values()[i+1]; data.setActivePilgrimContractId(type.id());
                data.setPilgrimInt("pilgrim_objective_mode",DifficultyObjectiveMode.PEACEFUL.serializedId());
                data.setPilgrimInt("pilgrim_expedition_distance",192);
                var view=PilgrimContractService.buildViews(world,player).getFirst();
                assertEquals("quest.village-quest.pilgrim.contract.expedition.offer",key(view.descriptionLines().getFirst()));
                var offer=(TranslatableContents)view.descriptionLines().getFirst().getContents();
                assertEquals("quest.village-quest.tracker.dimension."+(i<4?"overworld":i<6?"nether":"end"),key((Component)offer.getArgs()[0]));
                assertEquals(320,offer.getArgs()[1]);
                String returnKey="quest.village-quest.pilgrim.contract.expedition.return"+(i<4?"_origin":"");
                assertTrue(view.descriptionLines().stream().anyMatch(c->key(c).equals(returnKey)));
                var row=(TranslatableContents)view.objectiveLines().stream().filter(c->key(c).equals("screen.village-quest.ui_review.objective_row")).findFirst().orElseThrow().getContents();
                assertEquals(materials.get(i).getDescriptionId(),key((Component)row.getArgs()[0]));
                assertEquals(0,row.getArgs()[1]); assertEquals(i==1?16:i==5?32:12,row.getArgs()[2]);
                assertEquals(192,data.getPilgrimInt("pilgrim_expedition_distance"));
                assertEquals(DifficultyObjectiveMode.PEACEFUL.serializedId(),data.getPilgrimInt("pilgrim_objective_mode"));
                when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
                data.setPilgrimInt("pilgrim_objective_mode",DifficultyObjectiveMode.COMBAT.serializedId());
                var combat=PilgrimContractService.buildViews(world,player).getFirst();
                assertEquals("quest.village-quest.pilgrim.contract."+type.id()+".offer.1",key(combat.descriptionLines().getFirst()));
                when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
            }
        }
    }
    @Test void completionUsesSavedExpeditionModeBeforeClearingItAndKeepsTheNormalPayout() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var player=mock(ServerPlayer.class);
        UUID id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        when(world.getServer()).thenReturn(server);when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();var data=quests.getPlayerData(id);
        try(var states=mockStatic(QuestState.class);var rewards=mockStatic(VanillaRewardService.class);
            var projects=mockStatic(VillageProjectService.class);var inventory=mockStatic(DailyQuestService.class);
            var party=mockStatic(QuestPartyService.class);var intro=mockStatic(de.quest.shrine.VillageIntroRequestService.class);
            var prosperity=mockStatic(ProsperityService.class);var books=mockStatic(QuestBookHelper.class);
            var tracker=mockStatic(QuestTrackerService.class);var pilgrim=mockStatic(PilgrimService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            rewards.when(()->VanillaRewardService.state(world,id)).thenReturn(data.vanillaRewards());
            prosperity.when(()->ProsperityService.applyFestivalBonus(eq(world),eq(id),anyLong())).thenAnswer(c->c.getArgument(2));
            inventory.when(()->DailyQuestService.countInventoryItem(eq(player),any(Item.class))).thenReturn(64);
            inventory.when(()->DailyQuestService.consumeInventoryItem(eq(player),any(Item.class),anyInt())).thenReturn(true);
            projects.when(()->VillageProjectService.formatBonusRewardLine(any(),any(),any())).thenReturn(Component.empty());
            projects.when(()->VillageProjectService.formatRewardEchoLine(any(),any(),any())).thenReturn(Component.empty());
            for(var type:PilgrimContractType.values()) {
                if(type==PilgrimContractType.ROADMARKS_FOR_THE_COMPASS)continue;
                clearInvocations(player);data.setActivePilgrimContractId(type.id());
                data.setPilgrimInt("pilgrim_objective_mode",DifficultyObjectiveMode.PEACEFUL.serializedId());
                data.setPilgrimInt("pilgrim_expedition_entered",1);data.setPilgrimInt("pilgrim_expedition_distance",320);
                data.setPilgrimInt("pilgrim_expedition_checkpoints",3);data.setPilgrimInt("pilgrim_expedition_returned",1);
                long before=data.getCurrencyBalance();
                assertTrue(PilgrimContractService.handleContractAction(world,player,type.id()));
                assertTrue(data.getCurrencyBalance()>before,"The regular base payment still applies");
                var message=ArgumentCaptor.forClass(Component.class);verify(player).sendSystemMessage(message.capture(),eq(false));
                var shown=keys(message.getValue());
                assertTrue(shown.contains("quest.village-quest.pilgrim.contract.expedition.complete.1"));
                assertFalse(shown.contains("quest.village-quest.pilgrim.contract."+type.id()+".complete.1"));
                assertNull(data.getActivePilgrimContractId());assertEquals(0,data.getPilgrimInt("pilgrim_objective_mode"));
                assertTrue(PilgrimContractService.hasCompletedContract(world,id,type));
            }
        }
    }

    @Test void offeredExpeditionsFollowCurrentDifficultyAndNewEndContractsExplainTheCityGate() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var player=mock(ServerPlayer.class);
        UUID id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);when(world.getServer()).thenReturn(server);
        when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);when(world.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(17));
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();var data=quests.getPlayerData(id);
        data.setPilgrimFlag("pilgrim_contract_admin_unlock",true);
        try(var states=mockStatic(QuestState.class);var rewards=mockStatic(VanillaRewardService.class);
            var projects=mockStatic(VillageProjectService.class);var inventory=mockStatic(DailyQuestService.class);
            var surveyor=mockStatic(de.quest.quest.special.SurveyorCompassQuestService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(quests);
            rewards.when(()->VanillaRewardService.withSaved(eq(world),eq(id),eq("pilgrim"),anyList())).thenAnswer(c->c.getArgument(3));
            projects.when(()->VillageProjectService.formatQuestEchoLine(any(),any(),any())).thenReturn(Component.empty());
            for(var view:PilgrimContractService.buildViews(world,player))
                assertEquals("quest.village-quest.pilgrim.contract.expedition.offer",key(view.descriptionLines().getFirst()));
            assertNull(data.getActivePilgrimContractId());assertEquals(0,data.getPilgrimInt("pilgrim_objective_mode"));
            data.setActivePilgrimContractId(PilgrimContractType.STILLNESS_BEYOND_THE_GATE.id());
            var saved=new CompoundTag();saved.putString("key","pilgrim:stillness_beyond_the_gate");saved.putInt("version",1);
            assertTrue(data.vanillaRewards().accept("pilgrim","test-end",saved));
            var view=PilgrimContractService.buildViews(world,player).getFirst();
            assertTrue(view.descriptionLines().stream().anyMatch(c->key(c).equals("quest.village-quest.pilgrim.contract.expedition.end_city")));
            assertTrue(view.objectiveLines().stream().anyMatch(c->key(c).equals("reward.village-quest.end_city")));
        }
    }
}
