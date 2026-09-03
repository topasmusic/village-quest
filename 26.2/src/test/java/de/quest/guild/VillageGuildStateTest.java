package de.quest.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
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
}
