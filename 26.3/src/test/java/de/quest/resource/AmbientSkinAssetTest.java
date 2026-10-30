package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.caravan.NpcNames;
import de.quest.kingdom.NpcSkinCatalog;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Actual production PNG/UV checks, no game/server/entity simulation. */
final class AmbientSkinAssetTest {
    private static final int[][] CUBES={{0,0,8,8,8},{16,16,8,12,4},{40,16,4,12,4},{0,16,4,12,4},{32,48,4,12,4},{16,48,4,12,4}};
    private BufferedImage image(NpcNames.AmbientProfile profile) throws Exception {
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/village-quest/textures/entity/"+profile.texture()+".png")) {
            assertNotNull(in,profile.id()); var image=ImageIO.read(in); assertNotNull(image);return image;
        }
    }
    @Test void sixSkinsHaveOpaqueWideBaseFacesBinaryAlphaAndOnlyHeadOverlays() throws Exception {
        assertEquals(6,NpcNames.ambientProfiles().size());
        var heads=new HashSet<String>();
        for(var profile:NpcNames.ambientProfiles()) {
            var im=image(profile);assertEquals(64,im.getWidth());assertEquals(64,im.getHeight());
            var mask=new boolean[64][64];
            for(var c:CUBES) {
                int u=c[0],v=c[1],w=c[2],h=c[3],d=c[4];
                for(var f:new int[][]{{u+d,v,w,d},{u+d+w,v,w,d},{u,v+d,d,h},{u+d,v+d,w,h},{u+d+w,v+d,d,h},{u+2*d+w,v+d,w,h}})
                    for(int y=f[1];y<f[1]+f[3];y++)for(int x=f[0];x<f[0]+f[2];x++) {
                        mask[x][y]=true;assertEquals(255,im.getRGB(x,y)>>>24,profile.id()+" base UV "+x+","+y);
                    }
            }
            int raisedHair=0,lowerSides=0;var identity=new StringBuilder();
            for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
                int alpha=im.getRGB(x,y)>>>24;
                assertTrue(alpha==0||alpha==255,profile.id()+" alpha");
                if(!mask[x][y]&&!(x>=32&&y<16))assertEquals(0,alpha,profile.id()+" unused UV");
                if(x>=32&&y<16&&alpha==255)raisedHair++;
                if(y>=12&&y<16&&(x>=32&&x<40||x>=48&&x<56)&&alpha==255)lowerSides++;
                if(y>=8&&y<16&&x>=8&&x<16)identity.append(im.getRGB(x,y)).append(',');
            }
            assertTrue(raisedHair>0,profile.id()+" missing raised hair");assertTrue(lowerSides<=24,profile.id()+" closed hair shell");
            assertTrue(heads.add(identity.toString()),profile.id()+" duplicate face");
            assertTrue(NpcSkinCatalog.FILES.contains(profile.texture()),profile.id()+" gallery entry");
        }
    }
    @Test void eachRoleHasPixelIdenticalOutfitsAndDifferentRoleOutfits() throws Exception {
        for(var pool:java.util.List.of(NpcNames.WANDERERS,NpcNames.NIGHTRUNNERS)) {
            var master=image(pool.getFirst());
            for(var profile:pool) {
                var other=image(profile);
                for(int y=16;y<64;y++)for(int x=0;x<64;x++)assertEquals(master.getRGB(x,y),other.getRGB(x,y),profile.id()+" clothing "+x+","+y);
            }
        }
        var day=image(NpcNames.WANDERERS.getFirst());var night=image(NpcNames.NIGHTRUNNERS.getFirst());int different=0;
        for(int y=20;y<32;y++)for(int x=20;x<28;x++)if(day.getRGB(x,y)!=night.getRGB(x,y))different++;
        assertTrue(different>=64,"Roles must be visibly distinct on the actual chest UV");
    }
}
