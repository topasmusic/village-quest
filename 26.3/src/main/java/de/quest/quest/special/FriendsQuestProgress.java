package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import java.util.*;

/** Personal additive state in the existing persisted quest ledger. No daily resets or party sharing. */
public final class FriendsQuestProgress {
    private final PlayerQuestData data;
    public FriendsQuestProgress(PlayerQuestData data) { this.data=Objects.requireNonNull(data); }
    private String key(String suffix) { return "friends."+suffix; }
    public int number(String suffix) { return data.getTradeRouteInt(key(suffix)); }
    public void number(String suffix,int value) { data.setTradeRouteInt(key(suffix),value); }
    public String text(String suffix) { return data.getTradeRouteString(key(suffix)); }
    public void text(String suffix,String value) { data.setTradeRouteString(key(suffix),value); }
    public int stage(String who) { return number(who+".stage"); }
    public boolean active(String who) { return number(who+".active")==1; }
    public boolean accept(String who,int walk,int sprint,int crouch) {
        if(stage(who)<0 || stage(who)>=3 || active(who)) return false;
        number(who+".active",1);
        if(who.equals("simon") && stage(who)==1) {
            number("walk.base",walk); number("sprint.base",sprint); number("crouch.base",crouch);
        }
        return true;
    }
    public boolean complete(String who) {
        if(!active(who) || stage(who)>=3) return false;
        number(who+".active",0); number(who+".stage",stage(who)+1); return true;
    }
    public int walking(int walk,int sprint,int crouch) {
        long cm=Math.max(0L,(long)walk-number("walk.base"))+Math.max(0L,(long)sprint-number("sprint.base"))+Math.max(0L,(long)crouch-number("crouch.base"));
        int blocks=(int)Math.min(20000,cm/100);
        if(blocks>number("simon.walk")) number("simon.walk",blocks);
        return number("simon.walk");
    }
    public boolean village(UUID id,String biome) {
        if(!active("simon") || stage("simon")!=2 || id==null || biome==null || biome.isBlank()) return false;
        var ids=new ArrayList<>(values("simon.villages"));
        var biomes=new LinkedHashSet<>(values("simon.biomes"));
        if(ids.contains(id.toString()) || friendsReady() || ids.size()>=3 && biomes.contains(biome)) return false;
        ids.add(id.toString()); text("simon.villages",String.join(",",ids));
        biomes.add(biome);
        text("simon.biomes",String.join(",",biomes.stream().limit(2).toList())); return true;
    }
    public List<String> values(String suffix) { String raw=text(suffix); return raw.isBlank()?List.of():List.of(raw.split(",")); }
    public boolean biome(String biome) {
        if(!active("lutte") || stage("lutte")!=0 || biome==null || biome.isBlank()) return false;
        var found=new ArrayList<>(values("lutte.biomes"));
        if(found.size()>=3 || found.contains(biome)) return false;
        found.add(biome); text("lutte.biomes",String.join(",",found)); return true;
    }
    public boolean friendsReady() { return values("simon.villages").size()>=3 && values("simon.biomes").size()>=2; }
    public boolean rollHerbs(String visit,int roll) {
        if(stage("simon")!=3 || visit==null || visit.isBlank() || visit.equals(text("simon.lastVisit"))) return false;
        int separator=visit.lastIndexOf(':');
        if(separator>0) {
            String village=visit.substring(0,separator); long day;
            try { day=Long.parseLong(visit.substring(separator+1)); } catch(NumberFormatException invalid) { return false; }
            long last=-1; try { last=Long.parseLong(text("simon.roll."+village)); } catch(NumberFormatException ignored) { }
            if(day<0 || day<=last) return false;
            text("simon.roll."+village,Long.toString(day));
        }
        text("simon.lastVisit",visit);
        if(number("simon.herbs")==0 && !visit.equals(text("simon.finishVisit")) && roll==0) number("simon.herbs",1);
        return number("simon.herbs")==1;
    }
    public boolean orderReplacement(long now) {
        int count=number("lutte.replacements"),generation=number("lutte.generation");
        if(!active("lutte") || stage("lutte")!=1 || !text("lutte.replacementDue").isBlank() || now<0 || count<0 || count==Integer.MAX_VALUE || generation<1 || generation==Integer.MAX_VALUE) return false;
        long wait=(count>=2?14L:2L)*24000;
        if(now>Long.MAX_VALUE-wait) return false;
        number("lutte.replacements",count+1);number("lutte.generation",generation+1);text("lutte.replacementDue",Long.toString(now+wait));return true;
    }
    public boolean replacementReady(long now) {
        try {long due=Long.parseLong(text("lutte.replacementDue"));return due>=0 && now>=due;}catch(NumberFormatException invalid){return false;}
    }

}
