package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.PlayerQuestData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

final class FriendsShortObjectiveTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    private String key(Component c) { return ((TranslatableContents)c.getContents()).getKey(); }
    @Test void friendsStagesUseTheirRealCountersAndReadyConversation() {
        for(String who:List.of("simon","lutte","marius")) for(int stage=0;stage<3;stage++) {
            var p=new FriendsQuestProgress(new PlayerQuestData());p.number(who+".stage",stage);
            var objective=FriendsQuestService.shortObjective(null,p,who);
            for(var row:de.quest.quest.QuestShortObjective.rows(objective))
                assertTrue(key(row).startsWith("quest.village-quest.tracker.") || key(row).equals("screen.village-quest.ui_review.objective_row"));
        }
        var p=new FriendsQuestProgress(new PlayerQuestData());p.number("simon.stage",1);p.number("simon.walk",20000);
        // A completed walking counter must point to hand-in, not remain a completed HUD task.
        assertEquals("quest.village-quest.tracker.meet_friend",key(FriendsQuestService.shortObjective(null,p,"simon")));
        p.number("marius.stage",2);p.number("marius.victory",1);
        assertEquals("quest.village-quest.tracker.meet_friend",key(FriendsQuestService.shortObjective(null,p,"marius")));
    }
    @Test void luttesMissingTargetIsNeverPresentedAsAnAlreadyAvailableBuildingSite() {
        var data=new PlayerQuestData();var p=new FriendsQuestProgress(data);p.number("lutte.stage",1);
        assertEquals("quest.village-quest.tracker.coast_search",key(FriendsQuestService.shortObjective(null,p,"lutte")));
        p.number("lutte.cursor",8192);
        assertEquals("quest.village-quest.tracker.coast_search_exhausted",key(FriendsQuestService.shortObjective(null,p,"lutte")));
        p.number("lutte.targets",1);p.number("lutte.target.0.x",125);p.number("lutte.target.0.z",600);
        var objective=(TranslatableContents)FriendsQuestService.shortObjective(null,p,"lutte").getContents();
        assertEquals("quest.village-quest.tracker.coast_build",objective.getKey());
        assertArrayEquals(new Object[]{1,3,125,600},objective.getArgs());
        p.number("lutte.signals",1);
        assertEquals("quest.village-quest.tracker.coast_search_exhausted",key(FriendsQuestService.shortObjective(null,p,"lutte")));
        p.number("lutte.signals",3);
        assertEquals("quest.village-quest.tracker.meet_friend",key(FriendsQuestService.shortObjective(null,p,"lutte")));
    }
}
