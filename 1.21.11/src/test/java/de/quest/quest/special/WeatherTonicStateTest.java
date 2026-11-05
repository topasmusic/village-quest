package de.quest.quest.special;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class WeatherTonicStateTest {
 @Test void clearBecomesRainForExactlyTwentyActiveMinutesAcrossReload() {
  var s=new WeatherTonicState(); assertTrue(s.start(false)); assertTrue(s.rain()); assertFalse(s.start(true));
  for(int i=0;i<123;i++) assertTrue(s.tick());s=WeatherTonicState.fromNbt(s.toNbt()); assertEquals(23877,s.remaining());
  for(int i=0;i<23877;i++) assertTrue(s.tick());assertEquals(0,s.remaining());assertFalse(s.tick()); assertTrue(s.start(true));assertFalse(s.rain());
 }
 @Test void unknownAndMalformedStateIsPreservedReadOnly() {
  var tag=new CompoundTag();tag.putInt("schema",2);tag.putString("private","keep");var s=WeatherTonicState.fromNbt(tag);
  assertFalse(s.writable());assertFalse(s.start(false));assertEquals(tag,s.toNbt());
 }
}
