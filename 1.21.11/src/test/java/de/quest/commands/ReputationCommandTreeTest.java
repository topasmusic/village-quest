package de.quest.commands;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ReputationCommandTreeTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void administrativeInspectSetAndPardonHaveExplicitPlayerAndVillageTargets() {
        var root = ReputationCommands.command().build();
        assertNotNull(root.getChild("inspect").getChild("player"));
        assertNotNull(root.getChild("set").getChild("guild").getChild("player").getChild("value"));
        assertNotNull(root.getChild("set").getChild("village").getChild("player").getChild("dimension").getChild("x").getChild("z").getChild("value"));
        assertNotNull(root.getChild("pardon").getChild("player"));
        assertNull(root.getChild("grantreward"));
    }
}
