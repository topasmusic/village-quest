package de.quest.quest;

import de.quest.config.ClientPreferenceService;
import de.quest.data.QuestState;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.daily.DailyQuestService.DailyQuestType;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.quest.weekly.WeeklyQuestService.WeeklyQuestType;
import de.quest.quest.story.*;
import de.quest.questmaster.*;
import de.quest.util.TimeUtil;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class QuestAvailabilityNotifierTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void reset() { QuestAvailabilityNotifier.resetRuntimeState(); }

    @Test void simultaneousOffersProduceOneMessageAndSoundWithoutRepeatingOnTheNextCheck() {
        for (int mask = 1; mask <= 7; mask++) checkOffers(mask, true);
    }

    @Test void disabledChatStillRecordsOffersAndPlaysOnlyOneSound() { checkOffers(7, false); }

    @Test void noNewOffersProducesNoMessageOrSound() { checkOffers(0, true); }
    @Test void reconnectStartsANewDelayAndPendingOffersAreNotMarkedDuringTheWait() { checkOffers(1, true, true); }

    private void checkOffers(int mask, boolean chatEnabled) {
        checkOffers(mask, chatEnabled, false);
    }
    private void checkOffers(int mask, boolean chatEnabled, boolean reconnect) {
        var server = mock(MinecraftServer.class);
        var world = mock(ServerLevel.class);
        var players = mock(PlayerList.class);
        var player = mock(ServerPlayer.class);
        UUID id = UUID.randomUUID();
        when(server.getTickCount()).thenReturn(20);
        when(server.overworld()).thenReturn(world);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(List.of(player));
        when(player.getUUID()).thenReturn(id);
        when(player.level()).thenReturn(world);
        when(world.getServer()).thenReturn(server);
        var state = mock(QuestState.class);
        var data = new de.quest.data.PlayerQuestData();
        when(state.getPlayerData(id)).thenReturn(data);
        var story = StoryArcType.values()[0];
        var chapter = mock(StoryChapterDefinition.class);
        when(chapter.canAccept(world, player)).thenReturn(true);
        try (var lookup = mockStatic(QuestState.class);
             var daily = mockStatic(DailyQuestService.class);
             var weekly = mockStatic(WeeklyQuestService.class);
             var stories = mockStatic(StoryQuestService.class);
             var progression = mockStatic(QuestMasterProgressionService.class);
             var ui = mockStatic(QuestMasterUiService.class);
             var time = mockStatic(TimeUtil.class);
             var preferences = mockStatic(ClientPreferenceService.class);
             var sound = mockStatic(QuestSoundFeedback.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            time.when(TimeUtil::currentDay).thenReturn(123L);
            time.when(TimeUtil::currentWeekCycle).thenReturn(45L);
            preferences.when(() -> ClientPreferenceService.questAvailableChatNotifications(player)).thenReturn(chatEnabled);
            daily.when(() -> DailyQuestService.canShowDailyOffer(world, id)).thenReturn((mask & 1) != 0);
            daily.when(() -> DailyQuestService.previewQuestChoice(world, id)).thenReturn(DailyQuestType.values()[0]);
            weekly.when(() -> WeeklyQuestService.previewQuestChoice(world, id))
                    .thenReturn((mask & 2) != 0 ? WeeklyQuestType.values()[0] : null);
            progression.when(() -> QuestMasterProgressionService.isStoryCategoryUnlocked(world, id)).thenReturn((mask & 4) != 0);
            stories.when(() -> StoryQuestService.availableArcType(world, id)).thenReturn(story);
            stories.when(() -> StoryQuestService.chapter(world, id, story)).thenReturn(chapter);
            if (reconnect) QuestAvailabilityNotifier.handleJoin(player);
            QuestAvailabilityNotifier.onServerTick(server);
            verify(player, never()).sendSystemMessage(any(Component.class), anyBoolean());
            sound.verifyNoInteractions();
            assertNotEquals(123L, data.getDailyOfferNoticeDay());
            assertNotEquals(45L, data.getWeeklyOfferNoticeCycle());
            when(server.getTickCount()).thenReturn(400);
            QuestAvailabilityNotifier.onServerTick(server);
            verify(player, never()).sendSystemMessage(any(Component.class), anyBoolean());
            sound.verifyNoInteractions();
            when(server.getTickCount()).thenReturn(420);
            QuestAvailabilityNotifier.onServerTick(server);
            when(server.getTickCount()).thenReturn(440);
            QuestAvailabilityNotifier.onServerTick(server);
            if (mask == 0) {
                verify(player, never()).sendSystemMessage(any(Component.class), anyBoolean());
                sound.verifyNoInteractions();
                return;
            }
            if (chatEnabled) {
                var message = ArgumentCaptor.forClass(Component.class);
                verify(player, times(1)).sendSystemMessage(message.capture(), eq(false));
                String suffix = switch (mask) {
                    case 1 -> "daily"; case 2 -> "weekly"; case 3 -> "daily_weekly";
                    case 4 -> "story"; case 5 -> "daily_story"; case 6 -> "weekly_story";
                    default -> "daily_weekly_story";
                };
                assertEquals("message.village-quest.questmaster.new_offers." + suffix,
                        ((TranslatableContents) message.getValue().getContents()).getKey());
            } else verify(player, never()).sendSystemMessage(any(Component.class), anyBoolean());
            sound.verify(() -> QuestSoundFeedback.playNewOffer(world, player), times(1));
            if ((mask & 1) != 0) assertEquals(123L, data.getDailyOfferNoticeDay());
            if ((mask & 2) != 0) assertEquals(45L, data.getWeeklyOfferNoticeCycle());
            if ((mask & 4) != 0) assertEquals(story.id() + ":0", data.getStoryOfferNoticeKey());
            if (reconnect) {
                QuestAvailabilityNotifier.handleDisconnect(id);
                QuestAvailabilityNotifier.handleJoin(player);
                time.when(TimeUtil::currentDay).thenReturn(124L);
                when(server.getTickCount()).thenReturn(820);
                QuestAvailabilityNotifier.onServerTick(server);
                assertEquals(123L, data.getDailyOfferNoticeDay());
                verify(player, times(1)).sendSystemMessage(any(Component.class), eq(false));
                when(server.getTickCount()).thenReturn(840);
                QuestAvailabilityNotifier.onServerTick(server);
                QuestAvailabilityNotifier.onServerTick(server);
                assertEquals(124L, data.getDailyOfferNoticeDay());
                verify(player, times(2)).sendSystemMessage(any(Component.class), eq(false));
                sound.verify(() -> QuestSoundFeedback.playNewOffer(world, player), times(2));
            }
        }
    }
}
