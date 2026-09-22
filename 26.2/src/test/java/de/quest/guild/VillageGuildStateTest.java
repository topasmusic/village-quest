package de.quest.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class VillageGuildStateTest {
    private static final UUID LEADER = UUID.fromString("e3724fcf-8cd9-46f0-b9b1-c65a22db6485");
    private static final UUID MEMBER = UUID.fromString("7007b5ec-9176-4a4c-bf2b-e5a39403494d");

    @Test
    void guildRolesProjectsAndPersistenceRemainShared() {
        VillageGuildState state = new VillageGuildState();
        assertNotNull(state.create(LEADER, "  Road  Friends  "));
        assertTrue(state.invite(LEADER, MEMBER));
        assertNotNull(state.accept(MEMBER));
        assertTrue(state.promote(LEADER, MEMBER));
        assertEquals(VillageGuildRole.STEWARD, state.guildFor(MEMBER).orElseThrow().role(MEMBER));
        assertFalse(state.selectProject(MEMBER, VillageGuildProject.WAYSTATION));
        for (int i = 0; i < 10; i++) state.addRenown(MEMBER, 8);
        assertTrue(state.selectProject(MEMBER, VillageGuildProject.WAYSTATION));

        VillageGuildState loaded = VillageGuildState.fromNbt(VillageGuildState.toNbt(state));
        var guild = loaded.guildFor(LEADER).orElseThrow();
        assertEquals("Road Friends", guild.name());
        assertEquals(2, guild.members().size());
        assertEquals(VillageGuildProject.WAYSTATION, guild.project());
    }

    @Test
    void personalMembershipRulesPreventDuplicateGuildState() {
        VillageGuildState state = new VillageGuildState();
        state.create(LEADER, "First");
        assertEquals(null, state.create(LEADER, "Second"));
        assertFalse(state.leave(LEADER) && state.guildFor(LEADER).isPresent());
    }

    @Test
    void leadershipCanBeRecoveredWhenAnotherMemberWouldBlockLeaving() {
        VillageGuildState state = new VillageGuildState();
        state.create(LEADER, "Roadwardens");
        state.invite(LEADER, MEMBER);
        state.accept(MEMBER);

        assertFalse(state.leave(LEADER));
        assertTrue(state.transferLeadership(LEADER, MEMBER));
        assertEquals(VillageGuildRole.LEADER, state.guildFor(MEMBER).orElseThrow().role(MEMBER));
        assertTrue(state.kick(MEMBER, LEADER));
        assertTrue(state.guildFor(LEADER).isEmpty());
        assertEquals(1, state.guildFor(MEMBER).orElseThrow().members().size());
    }

    @Test
    void duplicateGuildIdsAndMembershipsRecoverToOneCanonicalGuildWithoutNpe() {
        UUID duplicateGuild = UUID.fromString("57c7eb5a-3aa0-4ab3-859d-f7698f9fc14d");
        UUID otherGuild = UUID.fromString("807a2a5a-9c59-49df-882a-ead7f5d6b0be");
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", 1);
        ListTag guilds = new ListTag();
        guilds.add(guild(duplicateGuild, "Canonical", LEADER, VillageGuildRole.LEADER, Integer.MAX_VALUE));
        guilds.add(guild(duplicateGuild, "Ghost", MEMBER, VillageGuildRole.LEADER, 5));
        guilds.add(guild(otherGuild, "Collision", LEADER, VillageGuildRole.LEADER, 5));
        root.put("guilds", guilds);

        VillageGuildState loaded = VillageGuildState.fromNbt(root);
        var canonical = loaded.guildFor(LEADER).orElseThrow();
        assertEquals(duplicateGuild, canonical.id());
        assertEquals("Canonical", canonical.name());
        assertEquals(VillageGuildState.MAX_RENOWN, canonical.renown());
        assertTrue(loaded.guildFor(MEMBER).isEmpty());
        assertFalse(loaded.promote(LEADER, MEMBER));
        assertFalse(loaded.selectProject(UUID.randomUUID(), VillageGuildProject.WAYSTATION));
    }

    @Test
    void renownAdditionSaturatesWithoutIntegerOverflow() {
        VillageGuildState state = new VillageGuildState();
        state.create(LEADER, "Safe Renown");
        assertEquals(VillageGuildState.MAX_RENOWN, state.addRenown(LEADER, Integer.MAX_VALUE).renown());
        assertEquals(VillageGuildState.MAX_RENOWN, state.addRenown(LEADER, Integer.MAX_VALUE).renown());
    }

    @Test
    void unknownFutureSchemaDoesNotInventMemberships() {
        VillageGuildState state = new VillageGuildState();
        state.create(LEADER, "Future");
        CompoundTag root = VillageGuildState.toNbt(state);
        root.putInt("schemaVersion", VillageGuildState.CURRENT_SCHEMA_VERSION + 1);
        assertTrue(VillageGuildState.fromNbt(root).guildFor(LEADER).isEmpty());
    }

    @Test
    void invitationExpiresAcrossSaveReloadAndNeverAddsAnExpiredMember() {
        long issuedAt = System.currentTimeMillis();
        VillageGuildState state = new VillageGuildState();
        state.create(LEADER, "Roadwardens");
        assertTrue(state.invite(LEADER, MEMBER, issuedAt));
        VillageGuildState loaded = VillageGuildState.fromNbt(VillageGuildState.toNbt(state));
        assertTrue(loaded.invitationFor(MEMBER, issuedAt + VillageGuildState.INVITATION_LIFETIME_MILLIS - 1).isPresent());
        assertEquals(null, loaded.accept(MEMBER, issuedAt + VillageGuildState.INVITATION_LIFETIME_MILLIS));
        assertTrue(loaded.invitationFor(MEMBER, issuedAt + VillageGuildState.INVITATION_LIFETIME_MILLIS).isEmpty());
        assertTrue(loaded.guildFor(MEMBER).isEmpty());
    }

    @Test
    void staleInviteCleanupKeepsRecentInvitationAndMembershipRole() {
        long issuedAt = System.currentTimeMillis();
        UUID laterPlayer = UUID.fromString("217b9d4a-3868-4a37-b13b-32556879c3c0");
        VillageGuildState state = new VillageGuildState();
        state.create(LEADER, "Roadwardens");
        assertTrue(state.invite(LEADER, MEMBER, issuedAt));
        assertTrue(state.invite(LEADER, laterPlayer, issuedAt + VillageGuildState.INVITATION_LIFETIME_MILLIS - 10));
        state.cleanupExpiredInvitations(issuedAt + VillageGuildState.INVITATION_LIFETIME_MILLIS);
        assertTrue(state.invitationFor(MEMBER, issuedAt + VillageGuildState.INVITATION_LIFETIME_MILLIS).isEmpty());
        assertNotNull(state.accept(laterPlayer, issuedAt + VillageGuildState.INVITATION_LIFETIME_MILLIS));
        assertEquals(VillageGuildRole.MEMBER, state.guildFor(laterPlayer).orElseThrow().role(laterPlayer));
    }

    @Test
    void legacyInvitationWithoutTimestampGetsAFullGracePeriod() {
        VillageGuildState state = new VillageGuildState();
        state.create(LEADER, "Roadwardens");
        state.invite(LEADER, MEMBER);
        CompoundTag saved = VillageGuildState.toNbt(state);
        saved.getListOrEmpty("invitations").getCompoundOrEmpty(0).remove("createdAt");

        VillageGuildState loaded = VillageGuildState.fromNbt(saved);
        assertTrue(loaded.invitationFor(MEMBER).isPresent());
        assertNotNull(loaded.accept(MEMBER));
        assertEquals(VillageGuildRole.MEMBER, loaded.guildFor(MEMBER).orElseThrow().role(MEMBER));
    }

    private static CompoundTag guild(UUID id, String name, UUID memberId, VillageGuildRole role, int renown) {
        CompoundTag guild = new CompoundTag();
        guild.putString("id", id.toString());
        guild.putString("name", name);
        guild.putInt("renown", renown);
        ListTag members = new ListTag();
        CompoundTag member = new CompoundTag();
        member.putString("id", memberId.toString());
        member.putInt("role", role.id());
        members.add(member);
        guild.put("members", members);
        return guild;
    }
}
