package de.quest.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import java.util.*;
import org.junit.jupiter.api.Test;

final class QuestTrackerBackgroundTest {
    @Test void paperHeaderAndBordersEachReceiveExactlyOneHalfOpacityLayer() {
        var graphics=mock(GuiGraphics.class);var fills=new ArrayList<int[]>();
        doAnswer(a->{fills.add(new int[]{a.getArgument(0),a.getArgument(1),a.getArgument(2),a.getArgument(3),a.getArgument(4)});return null;})
                .when(graphics).fill(anyInt(),anyInt(),anyInt(),anyInt(),anyInt());
        QuestTrackerHud.drawBackground(graphics,10,20,100,60,.5f);
        for(int y=20;y<80;y++)for(int x=10;x<110;x++) {
            int layers=0;
            for(var fill:fills)if(x>=fill[0]&&x<fill[2]&&y>=fill[1]&&y<fill[3]) {
                layers++;assertEquals(128,fill[4]>>>24,"Opacity must not be accumulated with paper/frame/shadow");
            }
            assertEquals(1,layers,"Every panel pixel must receive exactly one fill");
        }
    }
}
