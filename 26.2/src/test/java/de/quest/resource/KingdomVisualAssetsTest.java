package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Real packaged image references and UI readability surfaces; no world or engine execution. */
final class KingdomVisualAssetsTest {
    @Test void managementSkinHasQuietPaperAndAllSixteenGeneratedIconsHaveTransparentExteriors() throws Exception {
        var frame=image("textures/gui/living_kingdoms/management_frame.png");
        assertEquals(1448,frame.getWidth()); assertEquals(1086,frame.getHeight());
        for(int y=200;y<1000;y+=100) for(int x=100;x<1400;x+=100) {
            int color=frame.getRGB(x,y);
            assertTrue((color>>>16&255)>200 && (color>>>8&255)>170 && (color&255)>120,"Readable paper at "+x+","+y);
        }
        var atlas=image("textures/gui/living_kingdoms/management_icons.png");
        assertEquals(1254,atlas.getWidth()); assertEquals(1254,atlas.getHeight());
        for(int row=0;row<4;row++) for(int col=0;col<4;col++) {
            int left=Math.round(col*1254/4f),right=Math.round((col+1)*1254/4f);
            int top=Math.round(row*1254/4f),bottom=Math.round((row+1)*1254/4f),visible=0,clear=0;
            for(int y=top;y<bottom;y+=3) for(int x=left;x<right;x+=3) {
                int alpha=atlas.getRGB(x,y)>>>24; if(alpha>200) visible++; if(alpha<20) clear++;
            }
            assertTrue(visible>1000 && clear>500,"Empty or solid cell "+col+","+row);
        }
    }
    private java.awt.image.BufferedImage image(String path) throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("assets/village-quest/"+path)) {
            assertNotNull(stream,path);return ImageIO.read(stream);
        }
    }
    @Test void anchorMaterialsHaveNativeResolutionAndSolidSurfaces() throws Exception {
        for(var name:new String[]{"council_seat","inn_barrel","market_desk","storehouse_anchor"}) {
            var kinds=name.equals("inn_barrel")?new String[]{"barrel","top","dark","paper"}:new String[]{"wood","dark","paper","metal"};
            for(var kind:kinds) {
                var im=image("textures/block/living_kingdoms/"+name+"_"+kind+".png");
                assertEquals(16,im.getWidth());assertEquals(16,im.getHeight());
                for(int y=0;y<16;y++)for(int x=0;x<16;x++)assertEquals(255,im.getRGB(x,y)>>>24);
            }
        }
    }
    @Test void allNineGlyphsHaveContentAndClearPaddingWithoutNeighborBleed() throws Exception {
        var im=image("textures/gui/living_kingdoms/icons.png");assertEquals(96,im.getWidth());assertEquals(96,im.getHeight());
        for(int row=0;row<3;row++)for(int col=0;col<3;col++) {
            int opaque=0;
            for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                int alpha=im.getRGB(col*32+x,row*32+y)>>>24;
                assertTrue(alpha==0 || alpha==255);
                if(x<2 || y<2 || x>=30 || y>=30)assertEquals(0,alpha);
                if(alpha==255)opaque++;
            }
            assertTrue(opaque>=40 && opaque<784,"Empty or completely solid glyph "+row+","+col);
        }
    }
    @Test void rectangularSpeechLeavesQuietLightPaperAndSeparateCenteredTail() throws Exception {
        var im=image("textures/gui/living_kingdoms/speech_frame.png");assertEquals(256,im.getWidth());assertEquals(64,im.getHeight());
        for(int y=12;y<42;y++)for(int x=20;x<236;x++) {
            int pixel=im.getRGB(x,y);assertEquals(255,pixel>>>24);
            assertTrue((pixel>>>16 & 255)>200 && (pixel>>>8 & 255)>190 && (pixel & 255)>150,"Quiet text surface");
        }
        boolean tail=false;
        for(int y=54;y<64;y++)for(int x=0;x<256;x++) {
            int alpha=im.getRGB(x,y)>>>24;
            if(x<120 || x>=136)assertEquals(0,alpha);else tail|=alpha==255;
        }
        assertTrue(tail);
    }
    @Test void newArtifactHasAVisibleNativeIconAndTransparentExterior() throws Exception {
        var im=image("textures/item/village_check.png");assertEquals(32,im.getWidth());assertEquals(32,im.getHeight());
        assertEquals(0,im.getRGB(0,0)>>>24);assertEquals(255,im.getRGB(16,16)>>>24);
    }
}
