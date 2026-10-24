package de.quest.quest.special;
import net.minecraft.nbt.*;
/** Active server ticks only. Raw unknown data is retained and use is disabled. */
public final class WeatherTonicState {
 public static final int DURATION=24000;
 private Tag retained; private int remaining; private boolean rain,writable=true;
 public int remaining() { return remaining; }
 public boolean rain() { return rain; }
 public boolean writable() { return writable; }
 public boolean start(boolean currentlyRaining) { if(!writable || remaining>0) return false; rain=!currentlyRaining; remaining=DURATION; return true; }
 public boolean tick() { if(!writable || remaining<=0) return false; remaining--; return true; }
 public Tag toNbt() { if(!writable) return retained.copy(); var t=new CompoundTag();t.putInt("schema",1);t.putInt("remaining",remaining);t.putBoolean("rain",rain);return t; }
 public static WeatherTonicState fromNbt(Tag payload) {
  var s=new WeatherTonicState(); if(payload==null) return s; s.retained=payload.copy();
  if(!(payload instanceof CompoundTag t) || t.getIntOr("schema",0)!=1 || !(t.get("remaining") instanceof IntTag) || t.getIntOr("remaining",-1)<0 || t.getIntOr("remaining",0)>DURATION || !(t.get("rain") instanceof ByteTag)) {s.writable=false;return s;}
  s.remaining=t.getIntOr("remaining",0);s.rain=t.getBooleanOr("rain",false);return s;
 }
}
