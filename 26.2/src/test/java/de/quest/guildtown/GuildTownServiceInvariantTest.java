package de.quest.guildtown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import de.quest.caravan.TradeRouteService;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageBondLevel;
import de.quest.shrine.VillageContactService;
import de.quest.shrine.VillageRequestType;
import de.quest.shrine.VillageBondService;
import de.quest.village.LivingVillageNetworkState;
import de.quest.village.VillageCondition;
import de.quest.village.VillageNeed;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildTownServiceInvariantTest {
    private static final UUID OWNER = UUID.fromString("f16859a5-5aaa-472d-bd46-25fc58664e72");
    private static final UUID GUEST = UUID.fromString("dd65ea8d-a004-48b4-a1fb-5c3bb7ce7bb2");
    private static final UUID GUILD = UUID.fromString("b696826d-bc1f-4c4b-8204-52f87979a8fd");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void commissionNetworkAccessIsRevalidatedAfterAcceptance() {
        assertTrue(GuildTownService.networkAccessAllowed(OWNER, OWNER, null, null));
        assertTrue(GuildTownService.networkAccessAllowed(GUEST, OWNER, GUILD, GUILD));
        assertFalse(GuildTownService.networkAccessAllowed(GUEST, OWNER, null, GUILD));
        assertFalse(GuildTownService.networkAccessAllowed(GUEST, OWNER, GUILD, UUID.randomUUID()));
        assertFalse(GuildTownService.networkAccessAllowed(GUEST, OWNER, null, null));

        PlayerQuestData accepted = new PlayerQuestData();
        assertTrue(GuildTownProgress.beginCommission(accepted, GuildTownCommission.WINTER_FEED, OWNER, 2, 4));
        assertFalse(GuildTownService.revalidateCommissionAccess(
                accepted, GUEST, OWNER, GUILD, UUID.randomUUID()));
        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.commissionState(accepted, GuildTownCommission.WINTER_FEED));
    }

    @Test
    void pendingProjectRewardsProduceAVisibleClickableClaimLine() {
        assertNull(GuildTownService.pendingClaimsLine(0));
        var line = GuildTownService.pendingClaimsLine(2);
        assertNotNull(line);
        assertNotNull(line.getStyle().getClickEvent());
    }

    @Test
    void contactOnlyVillageCanStartLocalStoryWithoutCreatingTradeRouteState() {
        PlayerQuestData data = new PlayerQuestData();
        VillageContactService.ContactResult contact = VillageContactService.establish(
                data, 144, -208, VillageBondType.PASTURE);
        Map<String, Integer> contactInts = Map.copyOf(data.getTradeRouteIntState());
        Map<String, String> contactStrings = Map.copyOf(data.getTradeRouteStringState());
        Set<String> contactFlags = Set.copyOf(data.getTradeRouteFlags());

        GuildTownStoryVillageResolver.StoryVillage local =
                GuildTownStoryVillageResolver.fromContact(contact.contact());

        assertNotNull(local);
        assertFalse(local.connectedRoute());
        assertFalse(local.needsRecovery());
        assertEquals(VillageBondType.PASTURE, local.type());
        assertTrue(GuildTownProgress.beginStory(
                data, GuildTownStory.LONG_DRIVE, local.index(), GuildTownProgress.PREVENTIVE));
        assertEquals(contactInts, data.getTradeRouteIntState());
        assertEquals(contactStrings, data.getTradeRouteStringState());
        assertEquals(contactFlags, data.getTradeRouteFlags());
        assertEquals(0, data.getTradeRouteInt("route_count"));
        assertEquals(1, data.getTradeRouteInt("bond_village_count"));
    }

    @Test
    void storyCompletionUsesCapturedVillageAfterActiveStateIsCleared() {
        PlayerQuestData data = new PlayerQuestData();
        GuildTownProgress.VillageIdentity village =
                new GuildTownProgress.VillageIdentity(OWNER, 144, -208);
        assertTrue(GuildTownProgress.beginStory(
                data, GuildTownStory.LONG_DRIVE, 6, GuildTownProgress.PREVENTIVE));
        assertTrue(GuildTownProgress.markStoryReady(data, GuildTownStory.LONG_DRIVE));

        assertTrue(GuildTownService.completeStoryWithChronicle(
                data, GuildTownStory.LONG_DRIVE, village, 420L));

        assertEquals(-1, GuildTownProgress.activeStoryVillage(data));
        assertEquals(village, GuildTownProgress.personalChronicle(data).getFirst().village());
    }

    @Test
    void missingLegacyStoryVillageMetadataNeverFallsBackToVillageZero() {
        PlayerQuestData data = new PlayerQuestData();
        GuildTownProgress.VillageIdentity captured =
                new GuildTownProgress.VillageIdentity(OWNER, -512, 704);
        assertTrue(GuildTownProgress.beginStory(
                data, GuildTownStory.LANTERNS_IN_BLOOM, 8, GuildTownProgress.RECOVERY));
        assertTrue(GuildTownProgress.markStoryReady(data, GuildTownStory.LANTERNS_IN_BLOOM));
        data.setStoryInt("guild_town.story.lanterns_in_bloom.village", 0);

        assertTrue(GuildTownService.completeStoryWithChronicle(
                data, GuildTownStory.LANTERNS_IN_BLOOM, captured, 840L));

        var entry = GuildTownProgress.personalChronicle(data).getFirst();
        assertEquals(captured, entry.village());
        assertFalse(entry.village().x() == 0 && entry.village().z() == 0);
    }

    @Test
    void removedCommissionRoutePausesWithoutRetargetingOrLosingProgress() {
        PlayerQuestData data = new PlayerQuestData();
        GuildTownProgress.VillageIdentity first =
                new GuildTownProgress.VillageIdentity(OWNER, 128, 256);
        GuildTownProgress.VillageIdentity second =
                new GuildTownProgress.VillageIdentity(OWNER, -384, 640);
        assertTrue(GuildTownProgress.beginCommission(data, GuildTownCommission.WINTER_FEED,
                first, 2, second, 4));
        assertEquals(11, GuildTownProgress.addCommissionProgress(
                data, GuildTownCommission.WINTER_FEED, 1, 11, 32));
        data.setTradeRouteInt("route_count", 2);
        data.setTradeRouteInt("route_0_x", first.x());
        data.setTradeRouteInt("route_0_z", first.z());
        data.setTradeRouteInt("route_0_freight", 17);
        data.setTradeRouteInt("route_0_slot", 3);
        data.setTradeRouteInt("route_1_x", second.x());
        data.setTradeRouteInt("route_1_z", second.z());
        data.setTradeRouteInt("route_1_freight", 29);
        data.setTradeRouteInt("route_1_slot", 4);
        data.setTradeRouteString("route_0_name", "Granary Road");
        data.setTradeRouteString("route_1_name", "Pasture Road");
        List<VillageBondService.VillageBondView> villages = List.of(
                routeView(2, first.x(), first.z(), VillageBondType.GRANARY),
                routeView(4, second.x(), second.z(), VillageBondType.PASTURE));

        assertTrue(TradeRouteService.removeRoute(data, 1));
        Map<String, Integer> intsAfterRemoval = networkInts(data);
        Map<String, String> stringsAfterRemoval = networkStrings(data);
        Set<String> flagsAfterRemoval = networkFlags(data);

        assertEquals(GuildTownService.CommissionProgressResult.PAUSED,
                GuildTownService.revalidateAndProgressCommission(
                        data, GuildTownCommission.Mechanic.HARVEST, 5, ignored -> true, villages,
                        (x, z) -> TradeRouteService.isRegisteredDestination(data, x, z)));
        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.commissionState(data, GuildTownCommission.WINTER_FEED));
        assertEquals(11, GuildTownProgress.commissionProgress(data, GuildTownCommission.WINTER_FEED, 1));
        assertEquals(first, GuildTownProgress.commissionFirstIdentity(data));
        assertEquals(second, GuildTownProgress.commissionSecondIdentity(data));
        assertEquals(intsAfterRemoval, networkInts(data));
        assertEquals(stringsAfterRemoval, networkStrings(data));
        assertEquals(flagsAfterRemoval, networkFlags(data));
        assertEquals(1, data.getTradeRouteInt("route_count"));
        assertEquals(17, data.getTradeRouteInt("route_0_freight"));
        assertEquals(3, data.getTradeRouteInt("route_0_slot"));
        assertEquals(0, data.getTradeRouteInt("route_1_freight"));
        assertEquals(0, data.getTradeRouteInt("route_1_slot"));

        data.setTradeRouteInt("route_count", 2);
        data.setTradeRouteInt("route_1_x", second.x());
        data.setTradeRouteInt("route_1_z", second.z());
        data.setTradeRouteInt("route_1_freight", 29);
        data.setTradeRouteInt("route_1_slot", 4);
        assertTrue(GuildTownProgress.setCommissionPaused(data, false));
        assertEquals(GuildTownService.CommissionProgressResult.PROGRESSED,
                GuildTownService.revalidateAndProgressCommission(
                        data, GuildTownCommission.Mechanic.HARVEST, 5, ignored -> true, villages,
                        (x, z) -> TradeRouteService.isRegisteredDestination(data, x, z)));
        assertEquals(16, GuildTownProgress.commissionProgress(
                data, GuildTownCommission.WINTER_FEED, 1));
        assertEquals(first, GuildTownProgress.commissionFirstIdentity(data));
        assertEquals(second, GuildTownProgress.commissionSecondIdentity(data));
        assertEquals(2, data.getTradeRouteInt("route_count"));
    }

    @Test
    void removingRouteZeroCannotRetargetCommissionAfterCompaction() {
        PlayerQuestData data = new PlayerQuestData();
        GuildTownProgress.VillageIdentity first =
                new GuildTownProgress.VillageIdentity(OWNER, 128, 256);
        GuildTownProgress.VillageIdentity second =
                new GuildTownProgress.VillageIdentity(OWNER, -384, 640);
        assertTrue(GuildTownProgress.beginCommission(data, GuildTownCommission.WINTER_FEED,
                first, 2, second, 4));
        assertEquals(11, GuildTownProgress.addCommissionProgress(
                data, GuildTownCommission.WINTER_FEED, 1, 11, 32));
        data.setTradeRouteInt("route_count", 2);
        data.setTradeRouteInt("route_0_x", first.x());
        data.setTradeRouteInt("route_0_z", first.z());
        data.setTradeRouteInt("route_0_freight", 17);
        data.setTradeRouteInt("route_0_slot", 3);
        data.setTradeRouteString("route_0_name", "Granary Road");
        data.setTradeRouteInt("route_1_x", second.x());
        data.setTradeRouteInt("route_1_z", second.z());
        data.setTradeRouteInt("route_1_freight", 29);
        data.setTradeRouteInt("route_1_slot", 4);
        data.setTradeRouteString("route_1_name", "Pasture Road");
        List<VillageBondService.VillageBondView> villages = List.of(
                routeView(2, first.x(), first.z(), VillageBondType.GRANARY),
                routeView(4, second.x(), second.z(), VillageBondType.PASTURE));

        assertTrue(TradeRouteService.removeRoute(data, 0));
        assertEquals(1, data.getTradeRouteInt("route_count"));
        assertEquals(second.x(), data.getTradeRouteInt("route_0_x"));
        assertEquals(second.z(), data.getTradeRouteInt("route_0_z"));
        assertEquals(29, data.getTradeRouteInt("route_0_freight"));
        assertEquals(4, data.getTradeRouteInt("route_0_slot"));
        assertEquals("Pasture Road", data.getTradeRouteString("route_0_name"));
        assertEquals(0, data.getTradeRouteInt("route_1_freight"));
        assertEquals(0, data.getTradeRouteInt("route_1_slot"));
        Map<String, Integer> intsAfterCompaction = networkInts(data);
        Map<String, String> stringsAfterCompaction = networkStrings(data);
        Set<String> flagsAfterCompaction = networkFlags(data);

        assertEquals(GuildTownService.CommissionProgressResult.PAUSED,
                GuildTownService.revalidateAndProgressCommission(
                        data, GuildTownCommission.Mechanic.HARVEST, 5, ignored -> true, villages,
                        (x, z) -> TradeRouteService.isRegisteredDestination(data, x, z)));

        assertEquals(GuildTownProgress.PAUSED,
                GuildTownProgress.commissionState(data, GuildTownCommission.WINTER_FEED));
        assertEquals(11, GuildTownProgress.commissionProgress(
                data, GuildTownCommission.WINTER_FEED, 1));
        assertEquals(first, GuildTownProgress.commissionFirstIdentity(data));
        assertEquals(second, GuildTownProgress.commissionSecondIdentity(data));
        assertEquals(intsAfterCompaction, networkInts(data));
        assertEquals(stringsAfterCompaction, networkStrings(data));
        assertEquals(flagsAfterCompaction, networkFlags(data));
        assertFalse(TradeRouteService.isRegisteredDestination(data, first.x(), first.z()));
        assertTrue(TradeRouteService.isRegisteredDestination(data, second.x(), second.z()));
    }

    @Test
    void contactOnlyNoticePostStaysDormantUntilWelcomeThenOffersLocalStory() {
        PlayerQuestData data = new PlayerQuestData();
        VillageContactService.ContactResult contact = VillageContactService.establish(
                data, 144, -208, VillageBondType.PASTURE);
        GuildTownStoryVillageResolver.StoryVillage local =
                GuildTownStoryVillageResolver.fromContact(contact.contact());
        GuildTownStory story = GuildTownStory.forVillage(local.type());

        assertEquals(GuildTownService.NoticePostStoryState.DORMANT,
                GuildTownService.noticePostStoryState(data, story));
        data.setTradeRouteFlag("guild_intro.welcome_completed", true);
        assertEquals(GuildTownService.NoticePostStoryState.AVAILABLE,
                GuildTownService.noticePostStoryState(data, story));
        assertEquals(0, data.getTradeRouteInt("route_count"));
    }

    @Test
    void connectedRouteKeepsItsLiveNetworkConditionInLocalStoryResolver() {
        LivingVillageNetworkState.VillageSnapshot network =
                new LivingVillageNetworkState.VillageSnapshot(
                        4, 80, 96, VillageBondType.FORGE, VillageNeed.FORGE_FUEL_AND_ORE,
                        12, VillageCondition.CRISIS, 0, 0L, 0, 0, 0, 1);
        VillageBondService.VillageBondView route = new VillageBondService.VillageBondView(
                4, 80, 96, VillageBondType.FORGE, VillageBondLevel.TRUSTED,
                VillageRequestType.FORGE_IRON, 2, network);

        GuildTownStoryVillageResolver.StoryVillage resolved =
                GuildTownStoryVillageResolver.fromRoute(route);

        assertTrue(resolved.connectedRoute());
        assertTrue(resolved.needsRecovery());
        assertEquals(VillageCondition.CRISIS, resolved.condition());
        assertEquals(route.index(), resolved.index());
    }

    private static VillageBondService.VillageBondView routeView(
            int index, int x, int z, VillageBondType type) {
        VillageNeed need = type == VillageBondType.PASTURE
                ? VillageNeed.PASTURE_FODDER : VillageNeed.GRANARY_SEED_RESERVES;
        VillageRequestType request = type == VillageBondType.PASTURE
                ? VillageRequestType.PASTURE_FODDER : VillageRequestType.GRANARY_WHEAT;
        LivingVillageNetworkState.VillageSnapshot network =
                new LivingVillageNetworkState.VillageSnapshot(
                        index, x, z, type, need, 55, VillageCondition.STABLE,
                        0, 0L, 0, 0, 0, 1);
        return new VillageBondService.VillageBondView(
                index, x, z, type, VillageBondLevel.TRUSTED, request, 0, network);
    }

    private static Map<String, Integer> networkInts(PlayerQuestData data) {
        return data.getTradeRouteIntState().entrySet().stream()
                .filter(entry -> isNetworkKey(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, String> networkStrings(PlayerQuestData data) {
        return data.getTradeRouteStringState().entrySet().stream()
                .filter(entry -> isNetworkKey(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Set<String> networkFlags(PlayerQuestData data) {
        return data.getTradeRouteFlags().stream()
                .filter(GuildTownServiceInvariantTest::isNetworkKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isNetworkKey(String key) {
        return key.startsWith("route_") || key.startsWith("network_")
                || key.contains("freight") || key.contains("slot");
    }
}
