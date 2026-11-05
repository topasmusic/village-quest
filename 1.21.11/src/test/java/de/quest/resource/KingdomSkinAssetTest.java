package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Resource/UV and promotion identity checks only; no engine or Ingame render. */
final class KingdomSkinAssetTest {
    private static final String ROOT="assets/village-quest/textures/entity/living_kingdoms/";
    private static final String[] FILES={"leader_speaker_m","leader_speaker_f","leader_mayor_m","leader_mayor_f",
            "tavern_keeper_m","tavern_keeper_f","warehouse_keeper_m","warehouse_keeper_f",
            "merchant_m_01","merchant_m_02","merchant_m_03","merchant_f_01","merchant_f_02","merchant_f_03","caravan_f_01","guest_lutren","guest_siven","guest_marven","guest_ravel"};
    private static final int[][] CUBES={{0,0,8,8,8},{16,16,8,12,4},{40,16,4,12,4},{0,16,4,12,4},{32,48,4,12,4},{16,48,4,12,4}};
    private BufferedImage image(String name) throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream(ROOT+name+".png")) {
            assertNotNull(stream,name); return ImageIO.read(stream);
        }
    }
    @Test void allWideAtlasesHaveOpaqueBaseSurfacesAndCleanAlpha() throws Exception {
        for(var name:FILES) {
            var skin=image(name); assertEquals(64,skin.getWidth(),name); assertEquals(64,skin.getHeight(),name);
            var mask=new boolean[64][64];
            for(var c:CUBES) {
                int u=c[0],v=c[1],w=c[2],h=c[3],d=c[4];
                for(var r:new int[][]{{u+d,v,w,d},{u+d+w,v,w,d},{u,v+d,d,h},{u+d,v+d,w,h},{u+d+w,v+d,d,h},{u+2*d+w,v+d,w,h}}) {
                    for(int y=r[1];y<r[1]+r[3];y++) for(int x=r[0];x<r[0]+r[2];x++) {
                        mask[x][y]=true; assertEquals(255,skin.getRGB(x,y)>>>24,name+" base UV "+x+","+y);
                    }
                }
            }
            for(int y=0;y<64;y++) for(int x=0;x<64;x++) {
                int alpha=skin.getRGB(x,y)>>>24;
                assertTrue(alpha==0 || alpha==255,name+" fractional alpha");
                boolean headOverlay=x>=32 && y<16;
                boolean garmentOverlay=name.startsWith("guest_") && x>=16 && x<40 && y>=32 && y<48;
                if(!mask[x][y] && !headOverlay && !garmentOverlay) assertEquals(0,alpha,name+" unused UV");
            }
        }
    }
    @Test void rankChangesKeepEveryBaseHeadTexelIdenticalAndOnlyAddTheHatAndClothes() throws Exception {
        for(var sex:new String[]{"m","f"}) {
            var speaker=image("leader_speaker_"+sex); var mayor=image("leader_mayor_"+sex);
            boolean clothesDiffer=false,hat=false;
            for(int y=0;y<16;y++) for(int x=0;x<32;x++) assertEquals(speaker.getRGB(x,y),mayor.getRGB(x,y),sex+" original identity");
            for(int y=16;y<64;y++) for(int x=0;x<64;x++) clothesDiffer|=speaker.getRGB(x,y)!=mayor.getRGB(x,y);
            for(int y=0;y<16;y++) for(int x=32;x<64;x++) hat|=(mayor.getRGB(x,y)>>>24)==255;
            assertTrue(clothesDiffer); assertTrue(hat);
        }
    }
    @Test void raisedHairLeavesLowerSideOpeningsAndRaphaelsBaldCrownUncovered() throws Exception {
        for (var name : FILES) {
            var skin = image(name); int opaque = 0;
            for (int y = 12; y < 16; y++) for (int side : new int[]{32,48})
                for (int x = side; x < side + 8; x++) if ((skin.getRGB(x,y) >>> 24) != 0) opaque++;
            assertTrue(opaque <= 24, name + ": lower side hair must leave ears/cheeks open, not form a solid shell");
        }
        var bald = image("guest_ravel");
        for (int y = 0; y < 8; y++) for (int x = 40; x < 48; x++) assertEquals(0, bald.getRGB(x,y) >>> 24);
        for (int y = 8; y < 16; y++) for (int x = 40; x < 48; x++) assertEquals(0, bald.getRGB(x,y) >>> 24);
    }
}
