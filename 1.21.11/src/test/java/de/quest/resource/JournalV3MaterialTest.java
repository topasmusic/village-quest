package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class JournalV3MaterialTest {
    @Test void frameHasTransparentOpeningAndSolidCornerHardware() throws Exception {
        var im = image("frame"); assertEquals(416, im.getWidth()); assertEquals(234, im.getHeight());
        // Generated matting leaves sparse alpha=1 pixels; the renderer omits this center entirely.
        for (int y=34;y<200;y++) for (int x=34;x<382;x++) assertTrue((im.getRGB(x,y)>>>24)<=1, "Live content opening");
        for (int[] xy : new int[][]{{14,14},{402,14},{14,218},{402,218}}) assertTrue((im.getRGB(xy[0],xy[1])>>>24)>200);
    }
    @Test void heroHasReadableWarmNegativeSpaceAndNoTransparentMattingFringe() throws Exception {
        var im=image("journal_hero"); assertEquals(288,im.getWidth()); assertEquals(96,im.getHeight());
        for(int y=0;y<96;y++) for(int x=0;x<288;x++) assertEquals(255, im.getRGB(x,y)>>>24);
        for(int y=10;y<45;y++) for(int x=12;x<110;x++) {
            int rgb=im.getRGB(x,y);
            assertTrue((rgb>>>16&255)>180 && (rgb>>>8&255)>145 && (rgb&255)>80,"Quiet live text area");
        }
    }
    @Test void lanternHasClearPaddingForItsNoninteractiveOuterCorner() throws Exception {
        var im=image("lantern"); assertEquals(32,im.getWidth()); assertEquals(40,im.getHeight());
        int visible=0;
        for(int y=0;y<40;y++) for(int x=0;x<32;x++) {
            int alpha=im.getRGB(x,y)>>>24;
            if(x<2 || x>=30 || y<2 || y>=38) assertEquals(0,alpha);
            if(alpha>128) visible++;
        }
        assertTrue(visible>100 && visible<1000);
    }
    private java.awt.image.BufferedImage image(String name) throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("assets/village-quest/textures/gui/ui/v3/"+name+".png")) {
            assertNotNull(stream,name); return ImageIO.read(stream);
        }
    }
}
