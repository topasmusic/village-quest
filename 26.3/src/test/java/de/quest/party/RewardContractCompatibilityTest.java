package de.quest.party;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.daily.*;
import de.quest.reward.VanillaRewardService;
import de.quest.util.TimeUtil;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

final class RewardContractCompatibilityTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void partySeedingCannotMergeOldSmithIntoNewSmith() throws Exception {
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);
        var load=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);load.setAccessible(true);var state=(QuestState)load.invoke(null,new CompoundTag());
        var source=UUID.randomUUID();var guest=UUID.randomUUID();var type=DailyQuestService.DailyQuestType.SMITH_SMELTING;var day=TimeUtil.currentDay();
        for(var id : java.util.List.of(source,guest)) { var data=state.getPlayerData(id);data.setDailyChoice(type);data.setDailyChoiceDay(day);data.setAcceptedDay(day); }
        var snapshot=new CompoundTag();snapshot.putInt("version",1);snapshot.putString("key","daily:SMITH_SMELTING");state.getPlayerData(source).vanillaRewards().accept("daily",VanillaRewardService.dailyIdentity(type.name()),snapshot);
        state.getPlayerData(guest).setDailyInt(DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS,7);
        var session=new SharedQuestRuntime();session.bind(type.name(),day);session.markSynced(source);session.setInt(DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS,20);
        try(var lookup=mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            var merge=QuestPartyService.class.getDeclaredMethod("mergeDailyProgressIntoSession",ServerLevel.class,UUID.class,DailyQuestService.DailyQuestType.class,SharedQuestRuntime.class);merge.setAccessible(true);merge.invoke(null,world,guest,type,session);
            assertFalse(session.hasSynced(guest));assertEquals(20,session.getInt(DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS));
        }
    }
    @Test void progressCopyCannotOverwriteAnUnsyncedPersonalContract() throws Exception {
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);
        var load=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);load.setAccessible(true);var state=(QuestState)load.invoke(null,new CompoundTag());
        var guest=UUID.randomUUID();var data=state.getPlayerData(guest);var type=DailyQuestService.DailyQuestType.SMITH_SMELTING;var day=TimeUtil.currentDay();
        data.setDailyChoice(type);data.setDailyChoiceDay(day);data.setAcceptedDay(day);data.setDailyInt(DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS,7);
        var party=new PartyRuntime(UUID.randomUUID(),guest);party.members().add(guest);party.daily().bind(type.name(),day);party.daily().setInt(DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS,20);
        try(var lookup=mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            var copy=QuestPartyService.class.getDeclaredMethod("copySharedProgressToPlayer",ServerLevel.class,PartyRuntime.class,UUID.class);copy.setAccessible(true);copy.invoke(null,world,party,guest);
            assertEquals(7,data.getDailyInt(DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS));
        }
    }
}
