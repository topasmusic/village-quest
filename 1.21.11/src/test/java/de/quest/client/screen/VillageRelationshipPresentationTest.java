package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

final class VillageRelationshipPresentationTest {
    static String key(Component text) { return ((TranslatableContents)text.getContents()).getKey(); }
    @Test void disabledAndUnavailableNeverMasqueradeAsNeutralStanding() {
        assertEquals("screen.village-quest.relationship.disabled",key(ReputationPanel.localStanding(0,new VillageRelationship.Hints(false,true,1,Component.empty(),Component.empty()))));
        assertEquals("screen.village-quest.relationship.unavailable",key(ReputationPanel.localStanding(0,VillageRelationship.UNKNOWN)));
    }
    @Test void neutralStandingNeverHidesAttackMemoryAndCaseStepsStaySeparateFromBond() {
        var hints=new VillageRelationship.Hints(true,true,2,Component.literal("Earned"),Component.literal("Repair"));
        var lines=ReputationPanel.relationshipDetails(Component.literal("Allied"),0,true,hints);
        var keys=lines.stream().filter(c->c.getContents() instanceof TranslatableContents).map(VillageRelationshipPresentationTest::key).toList();
        assertTrue(keys.contains("screen.village-quest.relationship.bond"));assertTrue(keys.contains("screen.village-quest.reputation.local_standing"));
        assertTrue(keys.contains("screen.village-quest.relationship.conflict"));assertTrue(keys.contains("screen.village-quest.reputation.local_case"));
        assertTrue(keys.contains("screen.village-quest.reputation.case_locations"));assertTrue(lines.contains(hints.repair()));assertTrue(lines.contains(hints.nextBond()));
        var args=((TranslatableContents)ReputationPanel.relationshipSummary(Component.literal("Allied"),0,hints,false).getContents()).getArgs();
        assertEquals("screen.village-quest.relationship.conflict_short",key((Component)args[2]));
    }
}
