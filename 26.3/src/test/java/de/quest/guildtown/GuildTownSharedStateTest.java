package de.quest.guildtown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildTownSharedStateTest {
    private static final UUID GUILD = UUID.fromString("b696826d-bc1f-4c4b-8204-52f87979a8fd");
    private static final UUID ALICE = UUID.fromString("dd65ea8d-a004-48b4-a1fb-5c3bb7ce7bb2");
    private static final UUID BOB = UUID.fromString("bda906bf-0449-43e2-b070-d8f0d1c9e448");
    private static final UUID TOKEN = UUID.fromString("d2580194-e9cf-44c0-ad4b-a69444573e0b");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void projectMustBeAcceptedAndDeliveryTokenIsExactlyOnce() {
        GuildTownSharedState state = new GuildTownSharedState();
        assertEquals(0, state.contribute(GUILD, ALICE, TOKEN, 20).applied());
        assertTrue(state.accept(GUILD, GuildTownSharedProject.COMMON_STOREHOUSE));
        assertFalse(state.accept(GUILD, GuildTownSharedProject.ROAD_WAYMARKERS));
        assertEquals(20, state.contribute(GUILD, ALICE, TOKEN, 20).applied());
        assertEquals(0, state.contribute(GUILD, ALICE, TOKEN, 20).applied());
        assertEquals(20, state.project(GUILD).orElseThrow().progress());
    }

    @Test
    void completionFreesActiveSlotAndEachParticipantKeepsAnIndependentClaim() {
        GuildTownSharedState state = new GuildTownSharedState();
        state.accept(GUILD, GuildTownSharedProject.ROAD_WAYMARKERS);
        assertFalse(state.contribute(GUILD, ALICE, TOKEN, 24).completedNow());
        assertTrue(state.contribute(GUILD, BOB, UUID.randomUUID(), 24).completedNow());
        assertTrue(state.project(GUILD).isEmpty());
        assertTrue(state.accept(GUILD, GuildTownSharedProject.TRAVELLING_ARCHIVE));
        assertTrue(state.canClaim(GUILD, ALICE));
        assertTrue(state.claim(GUILD, ALICE));
        assertFalse(state.claim(GUILD, ALICE));
        assertTrue(state.claim(GUILD, BOB));
        assertEquals(0, state.pendingClaims(ALICE));
    }

    @Test
    void recordedParticipantCanClaimAfterLeavingTheGuild() {
        GuildTownSharedState state = new GuildTownSharedState();
        state.accept(GUILD, GuildTownSharedProject.COMMON_STOREHOUSE);
        state.contribute(GUILD, ALICE, TOKEN, 96);
        GuildTownSharedState.ClaimResult claim = state.claimForParticipant(ALICE, null);
        assertEquals(GUILD, claim.guildId());
        assertEquals(GuildTownSharedProject.COMMON_STOREHOUSE, claim.type());
    }

    @Test
    void saveReloadPreservesProgressParticipantsAndSeparateGuildChronicle() {
        GuildTownSharedState state = new GuildTownSharedState();
        state.accept(GUILD, GuildTownSharedProject.TRAVELLING_ARCHIVE);
        state.contribute(GUILD, ALICE, TOKEN, 20);
        state.addChronicle(GUILD, "commission.completed.seed_register");

        GuildTownSharedState loaded = GuildTownSharedState.fromNbt(GuildTownSharedState.toNbt(state));
        var project = loaded.project(GUILD).orElseThrow();
        assertEquals(20, project.progress());
        assertTrue(project.participants().contains(ALICE));
        assertEquals(2, loaded.chronicle(GUILD).size());
        assertFalse(GuildTownSharedState.toNbt(loaded).toString().contains("chronicle.personal"));
    }

    @Test
    void disbandRemovesGuildOwnedStateButPreservesEarnedPersonalEntitlements() {
        GuildTownSharedState state = new GuildTownSharedState();
        state.accept(GUILD, GuildTownSharedProject.COMMON_STOREHOUSE);
        state.contribute(GUILD, ALICE, TOKEN, 96);

        GuildTownSharedState loaded = GuildTownSharedState.fromNbt(GuildTownSharedState.toNbt(state));
        assertEquals(1, loaded.pendingClaims(ALICE));
        assertTrue(loaded.project(GUILD).isEmpty());
        loaded.removeGuild(GUILD);
        GuildTownSharedState afterDisbandReload = GuildTownSharedState.fromNbt(GuildTownSharedState.toNbt(loaded));
        assertEquals(1, afterDisbandReload.pendingClaims(ALICE));
        assertTrue(afterDisbandReload.chronicle(GUILD).isEmpty());
        GuildTownSharedState.ClaimResult claim = afterDisbandReload.claimForParticipant(ALICE, null);
        assertEquals(GUILD, claim.guildId());
        assertEquals(GuildTownSharedProject.COMMON_STOREHOUSE, claim.type());
    }

    @Test
    void completeResetStillRemovesPersonalEntitlementsWithAllSharedProgress() {
        GuildTownSharedState state = new GuildTownSharedState();
        state.accept(GUILD, GuildTownSharedProject.COMMON_STOREHOUSE);
        state.contribute(GUILD, ALICE, TOKEN, 96);
        state.accept(GUILD, GuildTownSharedProject.ROAD_WAYMARKERS);
        state.addChronicle(GUILD, "test.event");

        state.resetAllProgress();
        assertEquals(0, state.pendingClaims(ALICE));
        assertTrue(state.project(GUILD).isEmpty());
        assertTrue(state.chronicle(GUILD).isEmpty());
    }

    @Test
    void schemaOneCompletedProjectMigratesToDetachedClaims() {
        GuildTownSharedState state = new GuildTownSharedState();
        state.accept(GUILD, GuildTownSharedProject.COMMON_STOREHOUSE);
        state.contribute(GUILD, ALICE, TOKEN, 20);
        CompoundTag legacy = GuildTownSharedState.toNbt(state);
        legacy.putInt("schemaVersion", 1);
        CompoundTag project = legacy.getListOrEmpty("projects").getCompoundOrEmpty(0);
        project.putInt("progress", GuildTownSharedProject.COMMON_STOREHOUSE.target());
        project.putBoolean("completed", true);

        GuildTownSharedState loaded = GuildTownSharedState.fromNbt(legacy);
        assertTrue(loaded.project(GUILD).isEmpty());
        assertTrue(loaded.canClaim(GUILD, ALICE));
    }

    @Test
    void malformedNbtIsIgnoredWithoutCreatingProgress() {
        CompoundTag root = new CompoundTag();
        ListTag projects = new ListTag();
        CompoundTag malformed = new CompoundTag();
        malformed.putString("guild", "not-a-uuid");
        malformed.putInt("type", 999);
        malformed.putInt("progress", Integer.MAX_VALUE);
        malformed.putBoolean("completed", true);
        projects.add(malformed);
        root.put("projects", projects);
        GuildTownSharedState loaded = GuildTownSharedState.fromNbt(root);
        assertTrue(loaded.project(GUILD).isEmpty());
        assertTrue(loaded.chronicle(GUILD).isEmpty());
    }

    @Test
    void unknownFutureSchemaFallsBackToAnEmptySafeState() {
        GuildTownSharedState state = new GuildTownSharedState();
        state.accept(GUILD, GuildTownSharedProject.COMMON_STOREHOUSE);
        CompoundTag root = GuildTownSharedState.toNbt(state);
        root.putInt("schemaVersion", GuildTownSharedState.CURRENT_SCHEMA_VERSION + 1);
        assertTrue(GuildTownSharedState.fromNbt(root).project(GUILD).isEmpty());
    }

    @Test
    void sixtyFifthPendingRewardIsNeverDroppedAndAllClaimsSurviveReload() {
        GuildTownSharedState state = new GuildTownSharedState();
        for (int index = 0; index < 65; index++) {
            assertTrue(state.accept(GUILD, GuildTownSharedProject.COMMON_STOREHOUSE));
            assertTrue(state.contribute(GUILD, ALICE,
                    UUID.nameUUIDFromBytes(("delivery-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    GuildTownSharedProject.COMMON_STOREHOUSE.target()).completedNow());
        }
        assertEquals(65, state.pendingClaims(ALICE));

        GuildTownSharedState loaded = GuildTownSharedState.fromNbt(GuildTownSharedState.toNbt(state));
        assertEquals(65, loaded.pendingClaims(ALICE));
        for (int index = 0; index < 65; index++) assertTrue(loaded.claim(GUILD, ALICE));
        assertEquals(0, loaded.pendingClaims(ALICE));
    }
}
