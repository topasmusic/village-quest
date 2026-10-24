package de.quest.resource;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class WatchSealAssetTest {
 @Test void sealIsANativeTransparentItemWithGoldAndBlue() throws Exception {
  try(var stream=getClass().getResourceAsStream("/assets/village-quest/textures/item/village_watch_seal.png")) {
   assertNotNull(stream);var im=ImageIO.read(stream);assertEquals(16,im.getWidth());assertEquals(16,im.getHeight());int gold=0,blue=0,clear=0;
   for(int y=0;y<16;y++)for(int x=0;x<16;x++) {int rgb=im.getRGB(x,y),alpha=rgb>>>24;assertTrue(alpha==0 || alpha==255);if(alpha==0)clear++;else {int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(r>160 && g>90 && b<140)gold++;if(b>r && b>g)blue++;}}
   assertTrue(gold>=10);assertTrue(blue>=5);assertTrue(clear>=30);
  }
 }
}
