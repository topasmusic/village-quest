package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.*;
import java.util.List;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

final class PlayerTrustGuidanceTest {
    @Test void currentStandingUsesNextActualRankThreshold() {
        var card=ReputationPanel.trustCards(ReputationViewService.build(new SocialReputationData(),List.of(),0,true))
                .stream().filter(c->c.id().equals("social_guild")).findFirst().orElseThrow();
        var args=((TranslatableContents)card.subtitle().getContents()).getArgs();
        assertEquals(3,args.length);assertEquals(0,args[1]);assertEquals(20,args[2]);
        assertEquals(60,ReputationPanel.progressTarget(20));
        assertEquals(-59,ReputationPanel.progressTarget(-100));
        assertEquals(-19,ReputationPanel.progressTarget(-59));
        assertEquals(20,ReputationPanel.progressTarget(-19));
        assertEquals(100,ReputationPanel.progressTarget(60));
    }
    @Test void guildCardExplainsQuestSourcesAndTheSeparateVillageMandateBond() {
        var view=ReputationViewService.build(new SocialReputationData(),List.of(),0,true);
        var card=ReputationPanel.trustCards(view).stream().filter(c -> c.id().equals("social_guild")).findFirst().orElseThrow();
        var keys=card.details().stream().filter(c -> c.getContents() instanceof TranslatableContents)
                .map(c -> ((TranslatableContents)c.getContents()).getKey()).toList();
        assertTrue(keys.contains("screen.village-quest.reputation.quest_sources"));
        assertTrue(keys.contains("screen.village-quest.reputation.mandate_bond"));
        assertEquals(20,ReputationPanel.progressTarget(11));
    }
}
