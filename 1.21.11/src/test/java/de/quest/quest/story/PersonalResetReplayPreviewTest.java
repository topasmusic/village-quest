package de.quest.quest.story;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

final class PersonalResetReplayPreviewTest {
    @Test @SuppressWarnings("unchecked") void specialDetailsRetainBothOfferParagraphsAlongsideCurrentStatus() throws Exception {
        var method=de.quest.questmaster.QuestMasterUiService.class.getDeclaredMethod("specialInstructions",String.class,java.util.List.class);
        method.setAccessible(true);
        var offer=java.util.List.of(Component.literal("Narrative"),Component.literal("Complete quantities and consumption"));
        for(String status:java.util.List.of("active","ready","cache")) {
            var shown=(java.util.List<Component>)method.invoke(null,status,offer);
            assertEquals(3,shown.size());assertEquals(status,shown.getFirst().getString());
            assertEquals(offer,shown.subList(1,3));
        }
        assertEquals(2,offer.size());
    }

    @Test @SuppressWarnings("unchecked") void firstSurveyorPreviewShowsTheActualThreeIngotBonus() throws Exception {
        var method=de.quest.questmaster.QuestMasterUiService.class.getDeclaredMethod("compassRewards",PlayerQuestData.class);
        method.setAccessible(true);
        var shown=(java.util.List<Component>)method.invoke(null,new PlayerQuestData());
        var bonus=shown.stream().map(PersonalResetReplayPreviewTest::rewardItem).filter(java.util.Objects::nonNull).filter(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents name && name.getKey().equals("item.minecraft.netherite_ingot")).findFirst().orElseThrow();
        assertTrue(bonus.getString().contains(" x3"),bonus::getString);
    }
    @Test @SuppressWarnings("unchecked") void paidSurveyorPreviewNeverPromisesAnotherNetheriteBonus() throws Exception {
        var data=new PlayerQuestData();PersonalResetReceipts.record(data,"relic.surveyor");
        var method=de.quest.questmaster.QuestMasterUiService.class.getDeclaredMethod("compassRewards",PlayerQuestData.class);method.setAccessible(true);
        var shown=(java.util.List<Component>)method.invoke(null,data);
        assertTrue(shown.stream().map(PersonalResetReplayPreviewTest::rewardItem).filter(java.util.Objects::nonNull).noneMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents name && name.getKey().equals("item.minecraft.netherite_ingot")));
        assertTrue(shown.stream().anyMatch(c->c.getString().contains("personal_reset.replay")));
    }
    private static Component rewardItem(Component line) {
        if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents wrapper
                && wrapper.getKey().equals("screen.village-quest.questmaster.reward.item")
                && wrapper.getArgs().length == 1 && wrapper.getArgs()[0] instanceof Component item) return item;
        return null;
    }
    @Test void paidReplayPreviewDoesNotPromiseCurrencyExperienceOrAnOldSavedChoice() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();var owner=UUID.randomUUID();
        var arc=StoryArcType.LIVING_KINGDOMS;PersonalResetReceipts.storyPaidThrough(state.getPlayerData(owner),arc,1);
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);when(world.getServer()).thenReturn(server);
        var line=Component.literal("test");var completion=new StoryChapterCompletion(line,line,line,line,40,2,null,0,null);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS);var rewards=mockStatic(de.quest.reward.VanillaRewardService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            rewards.when(()->de.quest.reward.VanillaRewardService.withSaved(eq(world),eq(owner),anyString(),anyList())).thenAnswer(c->c.getArgument(3));
            var shown=StoryQuestService.previewRewardLines(world,owner,arc,completion);
            assertTrue(shown.stream().noneMatch(c->c.getString().contains("reward.currency") || c.getString().contains("reward.experience")));
            assertTrue(shown.stream().anyMatch(c->c.getString().contains("personal_reset.replay")));
            rewards.verifyNoInteractions();
        }
    }
}
