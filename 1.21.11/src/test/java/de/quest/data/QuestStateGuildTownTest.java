package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.guildtown.GuildTownCommission;
import de.quest.guildtown.GuildTownProgress;
import de.quest.guildtown.GuildTownStory;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class QuestStateGuildTownTest {
    private static final UUID PLAYER = UUID.fromString("9ba5a458-4038-4c04-86b0-acde5c529fc1");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void abandoningPausedCommissionClearsSlotAndProgressWithoutCompletingOrRewarding() {
        PlayerQuestData data = new PlayerQuestData();
        GuildTownCommission commission = GuildTownCommission.REINFORCED_PLOUGHS;
        assertTrue(GuildTownProgress.beginCommission(data, commission, PLAYER, 2, 4));
        GuildTownProgress.addCommissionProgress(data, commission, 1, 9, commission.firstTarget());
        assertTrue(GuildTownProgress.setCommissionPaused(data, true));

        assertTrue(GuildTownProgress.abandonCommission(data));

        assertEquals(null, GuildTownProgress.activeCommission(data));
        assertEquals(0, GuildTownProgress.commissionState(data, commission));
        assertEquals(0, GuildTownProgress.commissionProgress(data, commission, 1));
        assertFalse(GuildTownProgress.commissionCompleted(data, commission));
        assertTrue(GuildTownProgress.beginCommission(data, commission, PLAYER, 2, 4));
    }

    @Test
    void malformedActiveIdsAreClearedWithoutCompletionOrRewardState() {
        PlayerQuestData data = new PlayerQuestData();
        data.setStoryInt("guild_town.active_story", 999);
        data.setStoryInt("guild_town.active_story_village", 4);
        data.setTradeRouteInt("guild_town.active_commission", 999);
        data.setTradeRouteString("guild_town.commission_owner", PLAYER.toString());
        data.setTradeRouteInt("guild_town.commission_first_village", 2);

        assertTrue(GuildTownProgress.sanitizeActiveState(data));

        assertEquals(-1, GuildTownProgress.activeStoryId(data));
        assertEquals(-1, GuildTownProgress.activeCommissionId(data));
        assertEquals(0, GuildTownProgress.completedStoryCount(data));
        assertEquals(0, GuildTownProgress.completedCommissionCount(data));
        assertEquals(null, GuildTownProgress.commissionOwner(data));
    }

    @Test
    void invalidKnownActiveStateClearsItsProgressInsteadOfCompletingIt() {
        PlayerQuestData data = new PlayerQuestData();
        GuildTownStory story = GuildTownStory.SHARED_TABLE;
        data.setStoryInt("guild_town.active_story", story.id() + 1);
        data.setStoryInt("guild_town.active_story_village", 3);
        data.setStoryInt("guild_town.story.shared_table.state", 99);
        data.setStoryInt("guild_town.story.shared_table.p1", 17);

        assertTrue(GuildTownProgress.sanitizeActiveState(data));

        assertEquals(-1, GuildTownProgress.activeStoryId(data));
        assertEquals(0, GuildTownProgress.storyState(data, story));
        assertEquals(0, GuildTownProgress.storyProgress(data, story, 1));
        assertFalse(GuildTownProgress.storyCompleted(data, story));
    }

    @Test
    void saveReloadPreservesStoriesPausedCommissionChronicleAndFinale() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        assertTrue(GuildTownProgress.beginStory(data, GuildTownStory.SHARED_TABLE, 4, GuildTownProgress.RECOVERY));
        GuildTownProgress.addStoryProgress(data, GuildTownStory.SHARED_TABLE, 1, 24, 24);
        GuildTownProgress.markStoryReady(data, GuildTownStory.SHARED_TABLE);
        assertTrue(GuildTownProgress.completeStory(data, GuildTownStory.SHARED_TABLE));
        assertTrue(GuildTownProgress.beginCommission(data, GuildTownCommission.REINFORCED_PLOUGHS,
                PLAYER, 4, 7));
        GuildTownProgress.addCommissionProgress(data, GuildTownCommission.REINFORCED_PLOUGHS, 1, 12, 24);
        assertTrue(GuildTownProgress.setCommissionPaused(data, true));
        assertTrue(GuildTownProgress.addPersonalChronicle(data, 4, "story.completed.shared_table"));
        assertTrue(GuildTownProgress.markFinaleClaimed(data));

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertTrue(GuildTownProgress.storyCompleted(loaded, GuildTownStory.SHARED_TABLE));
        assertEquals(GuildTownCommission.REINFORCED_PLOUGHS, GuildTownProgress.activeCommission(loaded));
        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.commissionState(loaded, GuildTownCommission.REINFORCED_PLOUGHS));
        assertEquals(12, GuildTownProgress.commissionProgress(loaded, GuildTownCommission.REINFORCED_PLOUGHS, 1));
        assertEquals(1, GuildTownProgress.personalChronicleCount(loaded, 4));
        assertTrue(GuildTownProgress.finaleClaimed(loaded));
    }

    @Test
    void terminalTransitionsAndRewardsAreIdempotent() {
        PlayerQuestData data = new PlayerQuestData();
        assertTrue(GuildTownProgress.beginStory(data, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.PREVENTIVE));
        assertFalse(GuildTownProgress.completeStory(data, GuildTownStory.LONG_DRIVE));
        assertTrue(GuildTownProgress.markStoryReady(data, GuildTownStory.LONG_DRIVE));
        assertTrue(GuildTownProgress.completeStory(data, GuildTownStory.LONG_DRIVE));
        assertFalse(GuildTownProgress.completeStory(data, GuildTownStory.LONG_DRIVE));
        assertFalse(GuildTownProgress.beginStory(data, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.RECOVERY));
        assertTrue(GuildTownProgress.grantReward(data, "pasture_memory"));
        assertFalse(GuildTownProgress.grantReward(data, "pasture_memory"));
    }

    @Test
    void pausedStoryIgnoresProgressAndSurvivesSaveReload() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        assertTrue(GuildTownProgress.beginStory(data, GuildTownStory.LANTERNS_IN_BLOOM, 3,
                GuildTownProgress.RECOVERY));
        assertTrue(GuildTownProgress.setStoryPaused(data, true));
        assertEquals(0, GuildTownProgress.addStoryProgress(data, GuildTownStory.LANTERNS_IN_BLOOM, 1, 6, 6));

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.storyState(loaded, GuildTownStory.LANTERNS_IN_BLOOM));
        assertTrue(GuildTownProgress.setStoryPaused(loaded, false));
        assertEquals(6, GuildTownProgress.addStoryProgress(
                loaded, GuildTownStory.LANTERNS_IN_BLOOM, 1, 6, 6));
    }

    @Test
    void pausedCommissionIgnoresProgressAndCompletionCannotRewardTwice() {
        PlayerQuestData data = new PlayerQuestData();
        assertTrue(GuildTownProgress.beginCommission(data, GuildTownCommission.WINTER_FEED, PLAYER, 1, 2));
        assertTrue(GuildTownProgress.setCommissionPaused(data, true));
        assertEquals(0, GuildTownProgress.addCommissionProgress(data, GuildTownCommission.WINTER_FEED, 1, 32, 32));
        assertTrue(GuildTownProgress.setCommissionPaused(data, false));
        GuildTownProgress.addCommissionProgress(data, GuildTownCommission.WINTER_FEED, 1, 32, 32);
        GuildTownProgress.addCommissionProgress(data, GuildTownCommission.WINTER_FEED, 2, 3, 3);
        assertTrue(GuildTownProgress.markCommissionReady(data, GuildTownCommission.WINTER_FEED));
        assertTrue(GuildTownProgress.completeCommission(data, GuildTownCommission.WINTER_FEED));
        assertFalse(GuildTownProgress.completeCommission(data, GuildTownCommission.WINTER_FEED));
        assertEquals(1, GuildTownProgress.completedCommissionCount(data));
    }

    @Test
    void routeLossPausePreservesOwnerQualifiedCommissionAcrossReload() {
        GuildTownProgress.VillageIdentity first =
                new GuildTownProgress.VillageIdentity(PLAYER, 128, 256);
        GuildTownProgress.VillageIdentity second =
                new GuildTownProgress.VillageIdentity(PLAYER, -384, 640);
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        assertTrue(GuildTownProgress.beginCommission(data, GuildTownCommission.WINTER_FEED,
                first, 2, second, 4));
        assertEquals(11, GuildTownProgress.addCommissionProgress(
                data, GuildTownCommission.WINTER_FEED, 1, 11, 32));
        assertTrue(GuildTownProgress.setCommissionPaused(data, true));

        PlayerQuestData restored = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.commissionState(restored, GuildTownCommission.WINTER_FEED));
        assertEquals(11, GuildTownProgress.commissionProgress(restored, GuildTownCommission.WINTER_FEED, 1));
        assertEquals(first, GuildTownProgress.commissionFirstIdentity(restored));
        assertEquals(second, GuildTownProgress.commissionSecondIdentity(restored));
        assertEquals(2, GuildTownProgress.commissionFirstVillage(restored));
        assertEquals(4, GuildTownProgress.commissionSecondVillage(restored));
        assertEquals(0, restored.getTradeRouteInt("route_count"));

        assertTrue(GuildTownProgress.setCommissionPaused(restored, false));
        assertEquals(16, GuildTownProgress.addCommissionProgress(
                restored, GuildTownCommission.WINTER_FEED, 1, 5, 32));
        assertEquals(first, GuildTownProgress.commissionFirstIdentity(restored));
        assertEquals(second, GuildTownProgress.commissionSecondIdentity(restored));
    }

    @Test
    void personalChronicleIsUniqueAndCappedPerVillage() {
        PlayerQuestData data = new PlayerQuestData();
        for (int i = 0; i < GuildTownProgress.PERSONAL_CHRONICLE_CAP; i++) {
            assertTrue(GuildTownProgress.addPersonalChronicle(data, 3, "event." + i));
        }
        assertFalse(GuildTownProgress.addPersonalChronicle(data, 3, "event.12"));
        assertFalse(GuildTownProgress.addPersonalChronicle(data, 3, "event.1"));
        assertEquals(12, GuildTownProgress.personalChronicleCount(data, 3));
        assertTrue(GuildTownProgress.addPersonalChronicle(data, 4, "event.12"));
    }

    @Test
    void milestoneChronicleSurvivesTheFreeEventCapAndRemainsReadableInOrder() {
        PlayerQuestData data = new PlayerQuestData();
        GuildTownProgress.VillageIdentity village = new GuildTownProgress.VillageIdentity(PLAYER, -120, 340);
        for (int i = 0; i < GuildTownProgress.PERSONAL_CHRONICLE_CAP; i++) {
            assertTrue(GuildTownProgress.addPersonalChronicle(data, village, "ambient." + i, false, 100L + i));
        }
        assertFalse(GuildTownProgress.addPersonalChronicle(data, village, "ambient.full", false, 200L));
        assertTrue(GuildTownProgress.addPersonalChronicle(data, village,
                "story.completed.long_drive", true, 250L));

        var entries = GuildTownProgress.personalChronicle(data);
        assertEquals(GuildTownProgress.PERSONAL_CHRONICLE_CAP + 1, entries.size());
        assertTrue(entries.get(entries.size() - 1).milestone());
        assertEquals(250L, entries.get(entries.size() - 1).gameTime());
        assertEquals(village, entries.get(entries.size() - 1).village());
    }

    @Test
    void guestCommissionPersistsOwnerQualifiedVillageIdentitiesAndMatchesOnlyItsRoute() {
        UUID owner = UUID.fromString("f16859a5-5aaa-472d-bd46-25fc58664e72");
        GuildTownProgress.VillageIdentity apiary = new GuildTownProgress.VillageIdentity(owner, 100, 200);
        GuildTownProgress.VillageIdentity archive = new GuildTownProgress.VillageIdentity(owner, -300, 450);
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        assertTrue(GuildTownProgress.beginCommission(data, GuildTownCommission.WAX_SEALED_ROAD_BOOKS,
                apiary, archive));
        assertEquals(-1, GuildTownProgress.commissionFirstVillage(data));
        assertEquals(-1, GuildTownProgress.commissionSecondVillage(data));

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertEquals(apiary, GuildTownProgress.commissionFirstIdentity(loaded));
        assertEquals(archive, GuildTownProgress.commissionSecondIdentity(loaded));
        assertTrue(GuildTownProgress.routeBelongsToCommission(loaded, owner, -300, 450));
        assertFalse(GuildTownProgress.routeBelongsToCommission(loaded, owner, 999, 999));
        assertFalse(GuildTownProgress.routeBelongsToCommission(loaded, PLAYER, -300, 450));

        PlayerQuestData malformed = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        malformed.setTradeRouteFlag("guild_town.commission_has_identities", false);
        assertEquals(-1, GuildTownProgress.commissionFirstVillage(malformed));
        assertEquals(-1, GuildTownProgress.commissionSecondVillage(malformed));
        assertEquals(null, GuildTownProgress.commissionFirstIdentity(malformed));
        assertFalse(GuildTownProgress.routeBelongsToCommission(malformed, owner, 100, 200));

        data.setTradeRouteInt("guild_town.commission_first_x", 0);
        data.setTradeRouteFlag("guild_town.commission_first_x_present", false);
        PlayerQuestData missingCoordinate = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertEquals(null, GuildTownProgress.commissionFirstIdentity(missingCoordinate));
        assertEquals(null, GuildTownProgress.commissionSecondIdentity(missingCoordinate));
        assertFalse(GuildTownProgress.routeBelongsToCommission(missingCoordinate, owner, 0, 200));

        PlayerQuestData invalidCoordinate = new PlayerQuestData();
        assertTrue(GuildTownProgress.beginCommission(invalidCoordinate,
                GuildTownCommission.WAX_SEALED_ROAD_BOOKS, apiary, archive));
        invalidCoordinate.setTradeRouteInt("guild_town.commission_first_x", Integer.MAX_VALUE);
        assertEquals(null, GuildTownProgress.commissionFirstIdentity(invalidCoordinate));
        assertFalse(GuildTownProgress.routeBelongsToCommission(
                invalidCoordinate, owner, Integer.MAX_VALUE, 200));
    }

    @Test
    void identityCommissionCanPersistRealLegacyIndicesForSafeMigration() {
        UUID owner = UUID.fromString("f16859a5-5aaa-472d-bd46-25fc58664e72");
        PlayerQuestData data = new PlayerQuestData();
        assertTrue(GuildTownProgress.beginCommission(data, GuildTownCommission.SEED_REGISTER,
                new GuildTownProgress.VillageIdentity(owner, 100, 200), 4,
                new GuildTownProgress.VillageIdentity(owner, -300, 450), 7));
        assertEquals(4, GuildTownProgress.commissionFirstVillage(data));
        assertEquals(7, GuildTownProgress.commissionSecondVillage(data));

        PlayerQuestData zeroCoordinate = new PlayerQuestData();
        GuildTownProgress.VillageIdentity atAxis = new GuildTownProgress.VillageIdentity(owner, 0, 200);
        assertTrue(GuildTownProgress.beginCommission(zeroCoordinate, GuildTownCommission.SEED_REGISTER,
                atAxis, 4, new GuildTownProgress.VillageIdentity(owner, -300, 0), 7));
        assertEquals(atAxis, GuildTownProgress.commissionFirstIdentity(zeroCoordinate));
    }

    @Test
    void longDriveEscortSelectionSurvivesReloadAndCanRecoverOrContinue() {
        UUID cow = UUID.fromString("bc6bdc42-ecce-42ac-802a-bef0a9ea7394");
        UUID sheep = UUID.fromString("02157165-0404-426f-8d68-13ac1089bec7");
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        GuildTownProgress.beginStory(data, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.PREVENTIVE);
        assertTrue(GuildTownProgress.registerLongDriveAnimal(data, cow));
        assertTrue(GuildTownProgress.registerLongDriveAnimal(data, sheep));
        assertTrue(GuildTownProgress.markLongDriveEscorted(data, cow));

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertEquals(java.util.List.of(cow, sheep), GuildTownProgress.longDriveAnimals(loaded));
        assertEquals(1, GuildTownProgress.storyProgress(loaded, GuildTownStory.LONG_DRIVE, 2));
        assertTrue(GuildTownProgress.markLongDriveEscorted(loaded, sheep));
        assertTrue(GuildTownProgress.markStoryReady(loaded, GuildTownStory.LONG_DRIVE));
        assertTrue(GuildTownProgress.longDriveAnimals(loaded).isEmpty());
        assertFalse(GuildTownProgress.registerLongDriveAnimal(loaded, UUID.randomUUID()));

        PlayerQuestData recovery = new PlayerQuestData();
        GuildTownProgress.beginStory(recovery, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.RECOVERY);
        GuildTownProgress.registerLongDriveAnimal(recovery, cow);
        assertTrue(GuildTownProgress.recoverLongDriveEscort(recovery));
        assertTrue(GuildTownProgress.longDriveAnimals(recovery).isEmpty());
        assertEquals(0, GuildTownProgress.storyProgress(recovery, GuildTownStory.LONG_DRIVE, 1));
    }

    @Test
    void longDriveCountsObservedSharedDistanceAndRecoversLastPositionAfterReload() {
        UUID cow = UUID.fromString("bc6bdc42-ecce-42ac-802a-bef0a9ea7394");
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        GuildTownProgress.beginStory(data, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.PREVENTIVE);
        assertTrue(GuildTownProgress.registerLongDriveAnimal(data, cow,
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 1), 100L));
        GuildTownProgress.trackLongDriveEscort(data, cow,
                new BlockPos(8, 64, 0), new BlockPos(8, 64, 1), 120L, true);
        assertEquals(8_000, GuildTownProgress.longDriveDistanceMillis(data, cow));
        assertEquals(64, data.getTradeRouteInt("guild_town.story.long_drive.start." + cow + ".y"));
        assertEquals(8, data.getTradeRouteInt("guild_town.story.long_drive.last." + cow + ".x"));
        assertEquals(8, data.getTradeRouteInt("guild_town.story.long_drive.player_last." + cow + ".x"));

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);
        assertEquals(8, loaded.getTradeRouteInt("guild_town.story.long_drive.last." + cow + ".x"));
        GuildTownProgress.trackLongDriveEscort(loaded, cow,
                new BlockPos(16, 64, 0), new BlockPos(16, 64, 1), 140L, true);
        assertEquals(16_000, GuildTownProgress.longDriveDistanceMillis(loaded, cow));

        GuildTownProgress.trackLongDriveEscort(loaded, cow,
                new BlockPos(24, 64, 0), new BlockPos(24, 64, 1), 160L, false);
        GuildTownProgress.trackLongDriveEscort(loaded, cow,
                new BlockPos(32, 64, 0), new BlockPos(32, 64, 1), 180L, true);
        assertEquals(16_000, GuildTownProgress.longDriveDistanceMillis(loaded, cow));
        GuildTownProgress.trackLongDriveEscort(loaded, cow,
                new BlockPos(40, 64, 0), new BlockPos(40, 64, 1), 200L, true);
        GuildTownProgress.LongDriveTrackResult complete = GuildTownProgress.trackLongDriveEscort(
                loaded, cow, new BlockPos(48, 64, 0), new BlockPos(48, 64, 1), 220L, true);
        assertTrue(complete.completedNow());
        assertEquals(1, GuildTownProgress.storyProgress(loaded, GuildTownStory.LONG_DRIVE, 2));
    }

    @Test
    void longDriveDoesNotCountDisconnectGapOrTeleportSizedSegment() {
        UUID sheep = UUID.fromString("02157165-0404-426f-8d68-13ac1089bec7");
        PlayerQuestData data = new PlayerQuestData();
        GuildTownProgress.beginStory(data, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.RECOVERY);
        GuildTownProgress.registerLongDriveAnimal(data, sheep,
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 1), 100L);
        assertTrue(GuildTownProgress.suspendLongDriveObservation(data));
        GuildTownProgress.trackLongDriveEscort(data, sheep,
                new BlockPos(8, 64, 0), new BlockPos(8, 64, 1), 120L, true);
        assertEquals(0, GuildTownProgress.longDriveDistanceMillis(data, sheep));
        GuildTownProgress.trackLongDriveEscort(data, sheep,
                new BlockPos(8, 64, 0), new BlockPos(8, 64, 1), 200L, true);
        GuildTownProgress.trackLongDriveEscort(data, sheep,
                new BlockPos(80, 64, 0), new BlockPos(80, 64, 1), 220L, true);
        assertEquals(0, GuildTownProgress.longDriveDistanceMillis(data, sheep));
    }

    @Test
    void longDriveCountsOnlyTheDistanceAnimalAndPlayerActuallyTravelTogether() {
        UUID cow = UUID.fromString("bc6bdc42-ecce-42ac-802a-bef0a9ea7394");
        PlayerQuestData data = new PlayerQuestData();
        GuildTownProgress.beginStory(data, GuildTownStory.LONG_DRIVE, 2, GuildTownProgress.PREVENTIVE);
        GuildTownProgress.registerLongDriveAnimal(data, cow,
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 1), 100L);

        GuildTownProgress.trackLongDriveEscort(data, cow,
                new BlockPos(8, 64, 0), new BlockPos(0, 64, 1), 120L, true);
        assertEquals(0, GuildTownProgress.longDriveDistanceMillis(data, cow));

        GuildTownProgress.trackLongDriveEscort(data, cow,
                new BlockPos(16, 64, 0), new BlockPos(4, 64, 1), 140L, true);
        assertEquals(4_000, GuildTownProgress.longDriveDistanceMillis(data, cow));
    }

    @Test
    void concordUsesExactThreeThreeThreeGateWithoutRetroactiveClaim() {
        PlayerQuestData data = new PlayerQuestData();
        for (GuildTownStory story : GuildTownStory.values()) {
            if (GuildTownProgress.completedStoryCount(data) == 3) break;
            GuildTownProgress.beginStory(data, story, story.id(), GuildTownProgress.PREVENTIVE);
            GuildTownProgress.markStoryReady(data, story);
            GuildTownProgress.completeStory(data, story);
        }
        for (GuildTownCommission commission : GuildTownCommission.values()) {
            if (GuildTownProgress.completedCommissionCount(data) == 3) break;
            GuildTownProgress.beginCommission(data, commission, PLAYER, commission.id(), commission.id() + 1);
            GuildTownProgress.markCommissionReady(data, commission);
            GuildTownProgress.completeCommission(data, commission);
        }
        assertFalse(GuildTownProgress.meetsConcordGate(data, 2));
        assertTrue(GuildTownProgress.meetsConcordGate(data, 3));
        assertFalse(GuildTownProgress.finaleClaimed(data));
    }

    @Test
    void unknownCompletionBitsAreIgnored() {
        PlayerQuestData data = new PlayerQuestData();
        data.setStoryInt("guild_town.story_mask", ~0);
        data.setTradeRouteInt("guild_town.commission_mask", ~0);

        assertEquals(GuildTownStory.values().length, GuildTownProgress.completedStoryCount(data));
        assertEquals(GuildTownCommission.values().length, GuildTownProgress.completedCommissionCount(data));
    }

    @Test
    void manipulatedProgressCannotOverflowBackwards() {
        PlayerQuestData story = new PlayerQuestData();
        GuildTownProgress.beginStory(story, GuildTownStory.SHARED_TABLE, 0, GuildTownProgress.PREVENTIVE);
        story.setStoryInt("guild_town.story.shared_table.p1", Integer.MAX_VALUE);
        assertEquals(24, GuildTownProgress.addStoryProgress(story, GuildTownStory.SHARED_TABLE, 1, 10, 24));

        PlayerQuestData commission = new PlayerQuestData();
        GuildTownProgress.beginCommission(commission, GuildTownCommission.REINFORCED_PLOUGHS, PLAYER, 0, 1);
        commission.setTradeRouteInt("guild_town.commission.reinforced_ploughs.p1", Integer.MAX_VALUE);
        assertEquals(24, GuildTownProgress.addCommissionProgress(
                commission, GuildTownCommission.REINFORCED_PLOUGHS, 1, 10, 24));
    }
}
