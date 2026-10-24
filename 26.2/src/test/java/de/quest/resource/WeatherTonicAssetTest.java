package de.quest.resource;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class WeatherTonicAssetTest {
 @Test void goldenBottleHasNativeResolutionAndBinaryTransparency() throws Exception {
  try(var stream=getClass().getResourceAsStream("/assets/village-quest/textures/item/weather_tonic.png")) {
   assertNotNull(stream);var im=ImageIO.read(stream);assertEquals(16,im.getWidth());assertEquals(16,im.getHeight());int gold=0,clear=0;
   for(int y=0;y<16;y++)for(int x=0;x<16;x++) {int rgb=im.getRGB(x,y),alpha=rgb>>>24;assertTrue(alpha==0 || alpha==255);if(alpha==0)clear++;else if((rgb>>16&255)>180 && (rgb>>8&255)>100 && (rgb&255)<120)gold++;}
   assertTrue(gold>=15);assertTrue(clear>=60);
  }
 }
}
