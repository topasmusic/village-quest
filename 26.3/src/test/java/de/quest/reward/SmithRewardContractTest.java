package de.quest.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.util.TimeUtil;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.*;

final class SmithRewardContractTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void freshSmithOfferRequires51OreAndAcceptedLegacyStillRequires102() throws Exception {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class); when(world.getServer()).thenReturn(server);
        var method = QuestState.class.getDeclaredMethod("fromNbt", CompoundTag.class); method.setAccessible(true);
        var state = (QuestState)method.invoke(null, new CompoundTag()); var id = UUID.randomUUID(); var data = state.getPlayerData(id);
        data.setDailyChoice(DailyQuestService.DailyQuestType.SMITH_SMELTING); data.setDailyChoiceDay(TimeUtil.currentDay());
        try (var access = mockStatic(QuestState.class)) {
            access.when(() -> QuestState.get(server)).thenReturn(state);
            assertEquals(51, target(world, id));
            data.setAcceptedDay(TimeUtil.currentDay());
            assertEquals(102, target(world, id));
            var snapshot = new CompoundTag(); snapshot.putInt("version", 1);
            data.vanillaRewards().accept("daily", VanillaRewardService.dailyIdentity("SMITH_SMELTING"), snapshot);
            assertEquals(51, target(world, id));
        }
    }
    private int target(ServerLevel world, UUID id) {
        var line = DailyQuestService.previewProgressLine(world, id, false);
        return ((Number)((TranslatableContents)line.getContents()).getArgs()[1]).intValue();
    }
    @Test void activeQuestObjectivesExcludeSavedBequestsWhileRewardSummaryRetainsThem() throws Exception {
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);
        var load=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);load.setAccessible(true);var quests=(QuestState)load.invoke(null,new CompoundTag());
        var id=UUID.randomUUID();var data=quests.getPlayerData(id);data.setDailyChoice(DailyQuestService.DailyQuestType.SMITH_SMELTING);
        data.setDailyChoiceDay(TimeUtil.currentDay());data.setAcceptedDay(TimeUtil.currentDay());
        var snapshot=new CompoundTag();snapshot.putInt("version",1);var choices=new net.minecraft.nbt.ListTag();var option=new CompoundTag();option.putString("option","coins");choices.add(option);snapshot.put("choices",choices);
        assertTrue(data.vanillaRewards().accept("daily",VanillaRewardService.dailyIdentity("SMITH_SMELTING"),snapshot));
        try(var lookup=mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(quests);
            var status=DailyQuestService.openQuestStatus(world,id);assertNotNull(status);
            assertFalse(status.progressLine().getString().contains("reward.village-quest.after_turn_in"));
            assertEquals(1,VanillaRewardService.withSaved(world,id,"daily",java.util.List.of()).size());
        }
    }

}
