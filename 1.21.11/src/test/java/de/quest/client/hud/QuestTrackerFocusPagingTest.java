package de.quest.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class QuestTrackerFocusPagingTest {
    @Test void fittingPinnedQuestStaysOnEveryPageWhileOtherTasksRotate() {
        var pinned=List.of("Chosen title","Chosen objective");
        var others=List.of("Other title","Other objective","Third title","Third objective");
        for(long time:new long[]{0,7000,14000,21000}) {
            var page=QuestTrackerHud.page(pinned,others,4,time);
            assertEquals(pinned,page.lines().subList(0,2)); assertEquals(4,page.lines().size());
            assertEquals(2,page.count());
        }
        assertNotEquals(QuestTrackerHud.page(pinned,others,4,0).lines(),QuestTrackerHud.page(pinned,others,4,7000).lines());
    }
    @Test void tinyHudKeepsTheChosenHeadingAndEventuallyShowsEveryObjectiveAndOtherQuest() {
        var pinned=List.of("Chosen title","One","Two","Three","Four");
        var seen=new HashSet<String>();
        for(int i=0;i<7;i++) {
            var page=QuestTrackerHud.page(pinned,List.of("Other title","Other objective"),2,i*7000L);
            assertEquals("Chosen title",page.lines().getFirst()); assertTrue(page.lines().size()<=2);
            seen.addAll(page.lines());
        }
        assertEquals(Set.of("Chosen title","One","Two","Three","Four","Other title","Other objective"),seen);
    }
    @Test void pinnedOnlyAndNoPinFallbackKeepNormalPagingWithoutDroppingLines() {
        assertEquals(List.of("Title","Objective"),QuestTrackerHud.page(List.of("Title","Objective"),List.of(),8,7000).lines());
        assertEquals(1,QuestTrackerHud.page(List.of("Title","Objective"),List.of(),8,0).count());
        assertEquals(List.of("A","B"),QuestTrackerHud.page(List.of(),List.of("A","B","C"),2,0).lines());
        assertEquals(List.of("C"),QuestTrackerHud.page(List.of(),List.of("A","B","C"),2,7000).lines());
    }
    @Test void shorterLastPageKeepsTheSamePanelHeightForBottomAnchoredPins() {
        var first=QuestTrackerHud.page(List.of("Chosen title","Chosen objective"),List.of("One","Two","Three"),4,0);
        var last=QuestTrackerHud.page(List.of("Chosen title","Chosen objective"),List.of("One","Two","Three"),4,7000);
        assertEquals(4,first.lines().size()); assertEquals(3,last.lines().size());
        assertEquals(4,first.rows()); assertEquals(first.rows(),last.rows(),"Bottom-anchored heading must not jump as pages rotate");
    }
}
