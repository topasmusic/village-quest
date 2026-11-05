package de.quest.caravan;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
/** Historical guild count must not erase live cooldowns. */
final class GuildConvoyCooldownRegressionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void cooldownsBeyond1024HistoricalGuildsSurviveReload() {
        var state = new GuildConvoyState(); var owner = UUID.randomUUID();
        var routes = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (int i = 0; i < 1025; i++) {
            UUID guild = new UUID(0, i + 1);
            assertTrue(state.start(guild, owner, routes, 10)); assertNotNull(state.cancelGuild(guild));
        }
        var saved = GuildConvoyState.toNbt(state); assertEquals(1025, saved.getListOrEmpty("cooldown").size());
        UUID omitted = UUID.fromString(saved.getListOrEmpty("cooldown").getCompoundOrEmpty(1024).getStringOr("guild", ""));
        assertFalse(state.start(omitted, owner, routes, 11));
        var loaded = GuildConvoyState.fromNbt(saved);
        assertEquals(1025, GuildConvoyState.toNbt(loaded).getListOrEmpty("cooldown").size());
        assertFalse(loaded.start(omitted, owner, routes, 11));
    }
    @Test void expiredHistoricalCooldownsAreRemovedWhenTheNextConvoyStarts() {
        var state = new GuildConvoyState(); var owner = UUID.randomUUID();
        var routes = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var old = UUID.randomUUID(); assertTrue(state.start(old, owner, routes, 10)); state.cancelGuild(old);
        assertTrue(state.start(UUID.randomUUID(), owner, routes, 168010));
        assertEquals(1, GuildConvoyState.toNbt(state).getListOrEmpty("cooldown").size());
        state = GuildConvoyState.fromNbt(GuildConvoyState.toNbt(state));
        assertTrue(state.start(old, owner, routes, 168010));
    }
}
