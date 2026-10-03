package de.quest.commands;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildConvoyCommandTreeTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convoyActionsAreChildrenOfTownAlongsideConcord() {
        var town = VillageNetworkCommands.townCommand().build();
        var convoy = town.getChild("convoy");
        assertNotNull(convoy);
        assertNotNull(convoy.getChild("status"));
        assertNotNull(convoy.getChild("start"));
        assertNotNull(convoy.getChild("join"));
        assertNotNull(town.getChild("concord").getChild("claim"));
        assertNull(town.getChild("concord").getChild("convoy"));
    }
}
