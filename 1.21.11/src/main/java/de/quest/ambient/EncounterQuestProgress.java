package de.quest.ambient;

import net.minecraft.nbt.CompoundTag;

/** Player-owned document, saved atomically with the player's inventory and experience. */
public final class EncounterQuestProgress {
    public enum Kind {
        PROVISIONS(false,2,1), COMPANY(false,2,45), GREETINGS(false,2,1),
        ZOMBIES(true,2,2), IRON(true,2,1), LIGHT(true,2,2);
        public final boolean hunter; public final int levels,target;
        Kind(boolean hunter,int levels,int target){this.hunter=hunter;this.levels=levels;this.target=target;}
        public String key(){return name().toLowerCase(java.util.Locale.ROOT);}
        public boolean delivery(){return this==PROVISIONS || this==IRON;}
        public static Kind parse(String value){try{return valueOf(value);}catch(IllegalArgumentException e){return null;}}
    }
    private final CompoundTag tag;
    public EncounterQuestProgress(CompoundTag input){tag=input==null?new CompoundTag():input.copy();if(input==null)tag.putInt("schema",1);}
    public boolean writable(){return tag.getIntOr("schema",-1)==1 && (text("kind").isEmpty() || kind()!=null);}
    public Kind kind(){return Kind.parse(text("kind"));}
    public boolean active(){return kind()!=null;}
    public boolean pending(){return tag.getBooleanOr("pending",false);}
    public int progress(){return Math.max(0,tag.getIntOr("progress",0));}
    public long paidDay(){return tag.getLongOr("completedDay",Long.MIN_VALUE);}
    public boolean canOffer(long day){return writable() && !active() && day>paidDay();}
    public String text(String key){return tag.getStringOr(key,"");}
    public long number(String key){return tag.getLongOr(key,0);}
    public void text(String key,String value){tag.putString(key,value);}
    public void number(String key,long value){tag.putLong(key,value);}
    public boolean accept(Kind kind,long day){
        if(!canOffer(day) || kind==null)return false;
        tag.putString("kind",kind.name());tag.putInt("progress",0);tag.putBoolean("pending",false);
        for(String key:java.util.List.of("companion","origin","dimension","village","villageName","target","torch0","torch1"))tag.remove(key);
        return true;
    }
    public boolean advance(int amount){
        if(!writable() || !active() || pending() || amount<=0)return false;
        int next=(int)Math.min(kind().target,(long)progress()+amount);if(next==progress())return false;
        tag.putInt("progress",next);return true;
    }
    public boolean complete(long day){
        if(!writable() || !active() || pending() || progress()<kind().target || day<=paidDay())return false;
        tag.putBoolean("pending",true);tag.putLong("completedDay",day);return true;
    }
    public void clear(){
        for(String key:java.util.List.of("kind","progress","pending","companion","origin","dimension","village","villageName","target","torch0","torch1"))tag.remove(key);
    }
    public CompoundTag snapshot(){return tag.copy();}
}
