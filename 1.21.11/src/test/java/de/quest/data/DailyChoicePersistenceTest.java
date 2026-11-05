package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.party.QuestPartyService;
import de.quest.quest.daily.*;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import de.quest.reward.VanillaRewardService;
import de.quest.util.QuestCalendarService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

final class DailyChoicePersistenceTest {
    private final UUID id = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final AtomicLong day = new AtomicLong(20000);
    private ServerLevel world;
    private ServerPlayer player;
    private QuestState state;
    private MockedStatic<QuestCalendarService> calendar;
    private MockedStatic<QuestState> states;
    private MockedStatic<QuestPartyService> parties;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() {
        world = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);
        when(server.getPlayerList()).thenReturn(mock(PlayerList.class));
        when(world.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(7));
        player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(id);
        state = QuestState.fromNbt(new CompoundTag());
        var data = state.getPlayerData(id);
        data.setDailyDiscovered(true);
        data.setTradeRouteFlag("guild_intro.first_daily_completed", true);
        data.setDailyChoice(DailyQuestService.DailyQuestType.WOODCUTTING);
        data.setDailyChoiceDay(day.get());
        data.setDailyTargetProfile(RepeatableTargetProfile.HEAVY);
        calendar = mockStatic(QuestCalendarService.class);
        calendar.when(QuestCalendarService::currentDay).thenAnswer(call -> day.get());
        states = mockStatic(QuestState.class, call -> call.getMethod().getName().equals("get") ? state : call.callRealMethod());
        parties = mockStatic(QuestPartyService.class);
        parties.when(() -> QuestPartyService.rewardDailyProfile(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(3));
    }
    @AfterEach void close() { parties.close(); states.close(); calendar.close(); }

    @Test void threeDistinctOffersAndProfilesSurviveRefreshAndSaveReload() {
        var offers = DailyChoiceService.offers(world, id);
        assertEquals(3, offers.size());
        assertEquals(3, offers.stream().map(DailyChoiceService.Offer::type).distinct().count());
        assertEquals(RepeatableTargetProfile.HEAVY, offers.getFirst().profile());
        assertEquals(offers, DailyChoiceService.offers(world, id));
        state = QuestState.fromNbt(QuestState.toNbt(state));
        assertEquals(offers, DailyChoiceService.offers(world, id));
    }
    @Test void expiredForgedAndRerolledEntriesCannotMutateAcceptanceOrCaps() {
        var offers = DailyChoiceService.offers(world, id);
        assertFalse(DailyChoiceService.accept(world, player, offers.getFirst().entryId() + "_forged"));
        DailyChoiceService.invalidateOffers(state.getPlayerData(id));
        assertFalse(DailyChoiceService.accept(world, player, offers.getFirst().entryId()));
        day.incrementAndGet();
        assertFalse(DailyChoiceService.accept(world, player, offers.getLast().entryId()));
        assertEquals(PlayerQuestData.UNSET_DAY, state.getPlayerData(id).getAcceptedDay());
        assertEquals(PlayerQuestData.UNSET_DAY, state.getPlayerData(id).getLastRewardDay());
    }
    @Test void explicitChoiceIsNotReplacedByAnUnrelatedPartyQuestAndReplayIsHarmless() {
        var offer = DailyChoiceService.offers(world, id).getFirst();
        parties.when(() -> QuestPartyService.resolveSharedDailyChoice(any(), any(), any())).thenReturn(DailyQuestService.DailyQuestType.HONEY);
        try (var rewards = mockStatic(VanillaRewardService.class);
             var sounds = mockStatic(de.quest.quest.QuestSoundFeedback.class);
             var tracker = mockStatic(de.quest.quest.QuestTrackerService.class)) {
            rewards.when(() -> VanillaRewardService.prepare(any(), any(), anyString(), nullable(String.class), nullable(String.class), any(), any())).thenReturn(true);
            assertTrue(DailyChoiceService.accept(world, player, offer.entryId()));
            var data = state.getPlayerData(id);
            assertEquals(offer.type(), data.getDailyChoice());
            assertEquals(offer.profile(), data.getDailyTargetProfile());
            data.setDailyInt("saved", 7);
            assertFalse(DailyChoiceService.accept(world, player, offer.entryId()));
            assertEquals(7, data.getDailyInt("saved"));
            day.addAndGet(9);
            assertTrue(DailyChoiceService.offers(world, id).isEmpty());
            assertEquals(offer.type(), DailyQuestService.previewQuestChoice(world, id));
            assertEquals(offer.day(), data.getAcceptedDay());
        }
    }
    @Test void activeBonusAndCurrentCompletionExcludeAllNormalChoices() {
        var data = state.getPlayerData(id);
        data.setLastRewardDay(day.get());
        assertTrue(DailyChoiceService.offers(world, id).isEmpty());
        data.setBonusChoice(DailyQuestService.DailyQuestType.HONEY);
        data.setBonusAcceptedDay(day.get());
        day.incrementAndGet();
        assertTrue(DailyChoiceService.offers(world, id).isEmpty());
    }
    @Test void cancellationKeepsTheSameThreeOffersAndOriginalProfiles() {
        var offers = DailyChoiceService.offers(world, id);
        var data = state.getPlayerData(id);
        data.setDailyChoice(offers.getLast().type());
        data.setDailyTargetProfile(offers.getLast().profile());
        data.setAcceptedDay(day.get());
        assertTrue(DailyQuestService.cancelToday(world, id));
        assertEquals(offers, DailyChoiceService.offers(world, id));
    }

    @Test void eachNewCycleProducesThreeFreshOffersWithoutChangingPayoutReceipts() {
        var before = DailyChoiceService.offers(world, id);
        var data = state.getPlayerData(id);
        data.setLastRewardDay(day.get());
        data.setBonusRewardDay(day.get());
        day.incrementAndGet();
        var after = DailyChoiceService.offers(world, id);
        assertEquals(3, after.size());
        assertNotEquals(before.getFirst().entryId(), after.getFirst().entryId());
        assertEquals(20000, data.getLastRewardDay());
        assertEquals(20000, data.getBonusRewardDay());
    }

    @Test void resetIntroWithAnOldReceiptStillPaysLightBaseAndEarnsReservedExtra() throws Exception {
        var data=state.getPlayerData(id);data.setTradeRouteInt(PlayerQuestData.PROGRESS_GENERATION,2);
        data.setTradeRouteFlag("personal_reset.fresh_intro",true);data.setTradeRouteFlag("guild_intro.first_daily_active",true);
        data.setDailyChoice(DailyQuestService.DailyQuestType.WHEAT_HARVEST);data.setAcceptedDay(day.get());
        PersonalResetReceipts.record(data,"intro");
        var completion=DailyQuestService.previewFirstChoiceCompletion(world,DailyQuestService.DailyQuestType.WHEAT_HARVEST);
        try(var extra=mockStatic(VanillaRewardService.class);
            var currency=mockStatic(de.quest.economy.CurrencyService.class);
            var prosperity=mockStatic(de.quest.economy.ProsperityService.class);
            var projects=mockStatic(de.quest.quest.story.VillageProjectService.class);
            var xp=mockStatic(de.quest.economy.QuestExperienceService.class);
            var unlock=mockStatic(de.quest.questmaster.QuestMasterProgressionService.class);
            var inventory=mockStatic(Class.forName("de.quest.quest.daily.DailyQuestInventory"))) {
            prosperity.when(()->de.quest.economy.ProsperityService.applyFestivalBonus(eq(world),eq(id),anyLong())).thenAnswer(c->c.getArgument(2));
            currency.when(()->de.quest.economy.CurrencyService.formatDelta(anyLong())).thenReturn(net.minecraft.network.chat.Component.empty());
            xp.when(()->de.quest.economy.QuestExperienceService.rewardLine(anyInt(),anyInt(),any())).thenReturn(net.minecraft.network.chat.Component.empty());
            var method=DailyQuestService.class.getDeclaredMethod("deliverCompletion",ServerLevel.class,ServerPlayer.class,DailyQuestService.DailyQuestType.class,DailyQuestCompletion.class,boolean.class);
            method.setAccessible(true);method.invoke(null,world,player,DailyQuestService.DailyQuestType.WHEAT_HARVEST,completion,false);
            currency.verify(()->de.quest.economy.CurrencyService.addBalance(world,id,completion.currencyReward()));
            extra.verify(()->VanillaRewardService.complete(world,player,"daily","WHEAT_HARVEST:"+day.get()));
            xp.verify(()->de.quest.economy.QuestExperienceService.grant(player,completion.levels(),0,de.quest.economy.QuestExperienceService.RewardType.DAILY));
        }
    }
    @Test void alreadyPaidResetIntroCannotConsumeGoodsOrReserveAnotherReward() throws Exception {
        var data=state.getPlayerData(id);data.setTradeRouteInt(PlayerQuestData.PROGRESS_GENERATION,2);
        data.setTradeRouteFlag("personal_reset.fresh_intro",true);data.setTradeRouteFlag("guild_intro.first_daily_active",true);
        data.setDailyChoice(DailyQuestService.DailyQuestType.WHEAT_HARVEST);data.setAcceptedDay(day.get());data.setLastRewardDay(day.get());
        try(var intro=mockStatic(de.quest.shrine.VillageIntroRequestService.class);var extra=mockStatic(VanillaRewardService.class);var inventory=mockStatic(Class.forName("de.quest.quest.daily.DailyQuestInventory"))) {
            assertFalse(DailyQuestService.claimFromQuestMaster(world,player));extra.verifyNoInteractions();inventory.verifyNoInteractions();
        }
    }
    @Test void completedIntroCountersNeverLeakIntoUnacceptedOffersOrGetDeletedByPreview() {
        var data=state.getPlayerData(id);
        data.setDailyInt(DailyQuestKeys.WHEAT_PROGRESS,14);data.setDailyInt(DailyQuestKeys.BREAD_PROGRESS,2);
        data.setDailyFlag("old-proof",true);data.setProgressDay(day.get()-1);
        var before=java.util.Map.copyOf(data.getDailyIntState());
        var offer=new DailyChoiceService.Offer(day.get(),UUID.randomUUID().toString(),DailyQuestService.DailyQuestType.WHEAT_HARVEST,RepeatableTargetProfile.HEAVY);
        var line=DailyQuestService.previewOfferedProgress(world,id,offer);
        var content=(net.minecraft.network.chat.contents.TranslatableContents)line.getContents();
        assertEquals("quest.village-quest.daily.wheat.stage.1",content.getKey());assertEquals(0,content.getArgs()[0]);
        assertEquals(before,data.getDailyIntState());assertTrue(data.hasDailyFlag("old-proof"));assertEquals(day.get()-1,data.getProgressDay());
        assertEquals(0,DailyQuestService.previewFirstChoiceProgressLine(world,id,offer.type()).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents c ? c.getArgs()[0] : -1);
    }
    @Test void firstCompletionConsumesTodayAndOnlyNextCycleReturnsOffers() {
        var data=state.getPlayerData(id);data.setTradeRouteFlag("guild_intro.first_daily_active",true);
        data.setAcceptedDay(day.get());data.setDailyChoice(DailyQuestService.DailyQuestType.WHEAT_HARVEST);
        DailyQuestService.markCompletedToday(world,id);FirstDailyChoiceService.complete(data);
        assertFalse(data.hasActiveDaily());assertTrue(DailyChoiceService.offers(world,id).isEmpty());
        day.incrementAndGet();assertEquals(3,DailyChoiceService.offers(world,id).size());
    }
    @Test void introductoryRewardsUseLightProfileAndRemainSmallerThanRegularNormalRewards() {
        for(var type:FirstDailyChoiceService.choices()) {
            var intro=DailyQuestService.previewFirstChoiceCompletion(world,type);
            var regular=DailyQuestService.previewOfferedCompletion(world,id,new DailyChoiceService.Offer(day.get(),UUID.randomUUID().toString(),type,RepeatableTargetProfile.NORMAL));
            assertTrue(intro.currencyReward()<regular.currencyReward());assertTrue(intro.reputationAmount()<=regular.reputationAmount());assertTrue(intro.levels()<=regular.levels());
        }
    }
    @Test void previewsUseOfferedTargetsWithoutReplacingPersistedSelection() {
        var offers = DailyChoiceService.offers(world, id);
        var data = state.getPlayerData(id);
        var chosen = data.getDailyChoice();
        var profile = data.getDailyTargetProfile();
        for (var offer : offers) assertNotNull(DailyQuestService.previewOfferedProgress(world, id, offer));
        assertEquals(chosen, data.getDailyChoice());
        assertEquals(profile, data.getDailyTargetProfile());
        assertEquals(PlayerQuestData.UNSET_DAY, data.getAcceptedDay());
    }
}
