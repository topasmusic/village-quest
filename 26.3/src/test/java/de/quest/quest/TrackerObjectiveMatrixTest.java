package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import de.quest.guildtown.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.*;

final class TrackerObjectiveMatrixTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        bindDeliveryItems();
    }
    static void bindDeliveryItems() {
        for(var item:List.of(net.minecraft.world.item.Items.BREAD,net.minecraft.world.item.Items.HAY_BLOCK,
                net.minecraft.world.item.Items.RAIL,net.minecraft.world.item.Items.LEAD,net.minecraft.world.item.Items.CANDLE,
                net.minecraft.world.item.Items.WRITABLE_BOOK,net.minecraft.world.item.Items.IRON_INGOT))
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private static TranslatableContents text(Component line) { return (TranslatableContents)line.getContents(); }

    @Test void allLocalStoriesMoveFromFirstWorkToSecondWorkPauseAndActualTurnIn() {
        int[] firstTargets={24,12,2,6,3};
        for(var story:GuildTownStory.values()) {
            var data=new PlayerQuestData();
            assertTrue(GuildTownProgress.beginStory(data,story,2,GuildTownProgress.PREVENTIVE));
            var initial=GuildTownService.journalEntries(data).getFirst();
            var first=text(initial.entry().shortObjective());
            assertEquals(firstTargets[story.id()],first.getArgs()[2]);
            GuildTownProgress.addStoryProgress(data,story,1,firstTargets[story.id()],firstTargets[story.id()]);
            var second=text(GuildTownService.journalEntries(data).getFirst().entry().shortObjective());
            if(story==GuildTownStory.SHARED_TABLE) assertEquals("message.village-quest.guild_town.story.shared_table.choose",second.getKey());
            else assertEquals("quest.village-quest.tracker.local."+story.key()+".2",text((Component)second.getArgs()[0]).getKey());
            assertTrue(GuildTownProgress.setStoryPaused(data,true));
            assertEquals("quest.village-quest.tracker.paused",text(GuildTownService.journalEntries(data).getFirst().entry().shortObjective()).getKey());
            assertTrue(GuildTownProgress.setStoryPaused(data,false));assertTrue(GuildTownProgress.markStoryReady(data,story));
            var ready=GuildTownService.journalEntries(data).getFirst();
            assertEquals(initial.entry().id(),ready.entry().id());assertEquals("quest.village-quest.tracker.deliver",text(ready.entry().shortObjective()).getKey());
            assertFalse(ready.details().isEmpty());
        }
    }

    @Test void everyActualCommissionSelectsTheOtherVillageWorkThenItsSeparateDelivery() {
        for(var commission:GuildTownCommission.values()) {
            var data=new PlayerQuestData();var owner=UUID.randomUUID();
            assertTrue(GuildTownProgress.beginCommission(data,commission,new GuildTownProgress.VillageIdentity(owner,10,20),
                    new GuildTownProgress.VillageIdentity(owner,100,20)));
            var initial=GuildTownService.journalEntries(data).getFirst();
            assertEquals(commission.firstMechanic().label(),text(initial.entry().shortObjective()).getArgs()[0]);
            GuildTownProgress.addCommissionProgress(data,commission,1,commission.firstTarget(),commission.firstTarget());
            assertEquals(commission.secondMechanic().label(),text(GuildTownService.journalEntries(data).getFirst().entry().shortObjective()).getArgs()[0]);
            assertTrue(GuildTownProgress.setCommissionPaused(data,true));
            assertEquals("quest.village-quest.tracker.paused",text(GuildTownService.journalEntries(data).getFirst().entry().shortObjective()).getKey());
            assertTrue(GuildTownProgress.setCommissionPaused(data,false));assertTrue(GuildTownProgress.markCommissionReady(data,commission));
            var ready=GuildTownService.journalEntries(data).getFirst();
            assertEquals(initial.entry().id(),ready.entry().id());var delivery=text(ready.entry().shortObjective());
            assertEquals("quest.village-quest.tracker.deliver",delivery.getKey());assertEquals(commission.deliveryAmount(),delivery.getArgs()[0]);
            assertTrue(ready.details().size()>=3);
        }
    }
    @Test void completedFirstCountersYieldTheNextRealMaterialWithoutSendingTheWholeDescription() {
        for(String key:List.of("quest.village-quest.daily.wheat.progress","quest.village-quest.story.failing_harvest.chapter_2.progress",
                "quest.village-quest.story.silent_forge.chapter_3.progress.2","quest.village-quest.weekly.harvest.stage.1a")) {
            var input=Component.translatable(key,8,8,2,6);
            var shortLine=QuestShortObjective.first(List.of(input));
            assertEquals("screen.village-quest.ui_review.objective_row",text(shortLine).getKey());
            assertEquals(2,text(shortLine).getArgs()[1]); assertEquals(6,text(shortLine).getArgs()[2]);
        }
        var coal=Component.translatable("quest.village-quest.story.silent_forge.chapter_1.progress.1",16,16);
        var iron=Component.translatable("quest.village-quest.story.silent_forge.chapter_1.progress.2",3,12);
        assertEquals(iron,QuestShortObjective.first(List.of(Component.translatable("text.village-quest.story.chapter_label",1,Component.literal("Title")),coal,iron)));
    }
    @Test void localStoriesAndCommissionsHaveDifferentStableIdsAndKeepTheirFullTurnInDetails() {
        for(var story:GuildTownStory.values()) {
            var data=new PlayerQuestData();
            assertTrue(GuildTownProgress.beginStory(data,story,2,GuildTownProgress.PREVENTIVE));
            var commission=GuildTownCommission.REINFORCED_PLOUGHS; var owner=UUID.randomUUID();
            assertTrue(GuildTownProgress.beginCommission(data,commission,new GuildTownProgress.VillageIdentity(owner,10,20),
                    new GuildTownProgress.VillageIdentity(owner,100,20)));
            var before=Map.copyOf(data.getStoryIntState());
            var entries=GuildTownService.journalEntries(data);
            assertEquals(2,entries.size()); assertNotEquals(entries.get(0).entry().id(),entries.get(1).entry().id());
            assertTrue(entries.get(0).entry().id().startsWith("story:local:2:"));
            assertTrue(entries.get(1).entry().id().startsWith("story:commission:"));
            assertEquals("screen.village-quest.ui_review.objective_row",text(entries.get(0).entry().shortObjective()).getKey());
            assertTrue(entries.get(0).details().size()>=2); assertTrue(entries.get(1).details().size()>=3);
            assertEquals(before,data.getStoryIntState(),"Presentation must not advance local tasks");
            GuildTownProgress.markStoryReady(data,story);
            var ready=GuildTownService.journalEntries(data).getFirst();
            assertEquals(entries.getFirst().entry().id(),ready.entry().id());
            assertEquals("quest.village-quest.tracker.deliver",text(ready.entry().shortObjective()).getKey());
        }
    }
    @Test void authoredNonCounterActionsRemainAccessibleAndEmptyCompletedCountersPointToClaim() {
        for(String family:List.of("friends.simon.1.objective","story.living_kingdoms.next.market_trade")) {
            var action=Component.translatable("quest.village-quest."+family);
            assertEquals(action,QuestShortObjective.first(List.of(action)));
        }
        assertEquals("quest.village-quest.guidance.claim",text(QuestShortObjective.first(List.of(
                Component.translatable("quest.village-quest.story.the_empty_caravan.chapter_3.progress.paper",12,12)))).getKey());
    }

    @Test void allAuthoredTwoCounterProgressContractsSelectTheUnfinishedSecondPart() throws Exception {
        var catalog=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("assets/village-quest/lang/en_us.json")),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        int checked=0;
        for(var entry:catalog.entrySet()) {
            String key=entry.getKey(), value=entry.getValue().getAsString();
            if(!key.startsWith("quest.village-quest.") || !(key.contains(".progress") || key.contains(".stage."))
                    || value.chars().filter(c -> c=='%').count()!=4) continue;
            var line=Component.translatable(key,8,8,2,6);
            var parts=QuestObjectiveProgress.parts(line);
            assertEquals(2,parts.size(),key);
            assertFalse(parts.getLast().complete(),key);
            var next=text(QuestShortObjective.first(List.of(line)));
            assertEquals("screen.village-quest.ui_review.objective_row",next.getKey(),key);
            assertEquals(2,next.getArgs()[1],key); assertEquals(6,next.getArgs()[2],key);
            checked++;
        }
        assertTrue(checked>=30,"The matrix must cover actual authored family contracts");
    }

    @Test void artifactJourneySelectsTheNextActualDeedInEachOfItsFiveChapters() {
        for(int chapter=0;chapter<5;chapter++) {
            var data=new PlayerQuestData(); data.setActiveStoryArc(de.quest.quest.story.StoryArcType.ARTIFACT_JOURNEY);
            data.setStoryChapterProgress("artifact_journey",chapter);
            var target=UUID.randomUUID(); assertTrue(de.quest.kingdom.ArtifactJourneyProgress.begin(data,target));
            for(var deed:de.quest.kingdom.ArtifactJourneyProgress.required(chapter)) {
                assertEquals("quest.village-quest.tracker.artifact."+deed.name().toLowerCase(Locale.ROOT),
                        text(de.quest.kingdom.ArtifactJourneyService.shortObjective(data)).getKey());
                assertTrue(de.quest.kingdom.ArtifactJourneyProgress.record(data,target,de.quest.kingdom.ArtifactJourneyProgress.epoch(data),deed,"real-service-"+deed));
            }
            assertEquals("quest.village-quest.tracker.artifact."+(chapter==4?"choice":"claim"),
                    text(de.quest.kingdom.ArtifactJourneyService.shortObjective(data)).getKey());
        }
    }
}
