package de.quest.ambient;

import java.util.*;

/** Encounter-local decisions. No saved identity, rewards, world access or budget reset. */
public final class AmbientAdventurerPolicy {
    public static final int HARD_TTL=14400, MAX_MOBS=3, MAX_STRIKES=15, MAX_COUNTERS=3;
    public enum Assault { WARN, RETREAT, DEFEND }
    private final boolean hunter;
    private final int lifetime;
    private int age, absent, strikes, counters, navigationFailures;
    private long lastStrike=-20, huntAfter, fightUntil, lostSight=-1, defenseUntil;
    private UUID fighting, defending;
    private final Set<UUID> committed=new HashSet<>(), aborted=new HashSet<>(), refused=new HashSet<>();
    private final Map<UUID,Long> hits=new HashMap<>();
    private final Map<UUID,Long> deadlines=new HashMap<>();
    private boolean fleeing, distrustAll;

    public AmbientAdventurerPolicy(boolean hunter,int lifetime) {
        this.hunter=hunter; this.lifetime=Math.clamp(lifetime,4800,9600);
    }
    public int age() { return age; }
    public boolean tick(boolean nearbyPlayer) {
        age++; absent=nearbyPlayer?0:absent+1;
        return age>=Math.min(lifetime,HARD_TTL) || absent>=600;
    }
    public boolean hunter() { return hunter; }
    public boolean fleeing() { return fleeing; }
    public UUID fighting() { return fighting; }
    public UUID defending() { return defending; }
    public int mobCommitments() { return committed.size(); }
    public int strikes() { return strikes; }
    public int counters() { return counters; }
    public boolean refuses(UUID player) { return distrustAll || refused.contains(player); }
    /** Terrain failure is a temporary travel problem, never a combat/flight commitment. */
    public int navigationFailed() { navigationFailures++; return Math.min(200,40+20*navigationFailures); }
    public void navigationProgress() { navigationFailures=0; }
    public int travelLength(int roll) { return navigationFailures>0?1+Math.floorMod(roll,3):6+Math.floorMod(roll,6); }
    public double retreatHeading(double away) {
        return away+switch(navigationFailures%5) { case 1 -> Math.PI/4; case 2 -> -Math.PI/4; case 3 -> Math.PI/2; case 4 -> -Math.PI/2; default -> 0; };
    }
    public boolean canHunt(long now) { return hunter && !fleeing && fighting==null && defending==null && now>=huntAfter && committed.size()<MAX_MOBS && strikes<MAX_STRIKES; }
    public boolean commit(UUID target,long now,boolean selfDefense) {
        if(!hunter || target==null || fleeing || defending!=null || strikes>=MAX_STRIKES || fighting!=null) return false;
        if(!selfDefense && (!canHunt(now) || aborted.contains(target))) return false;
        if(!committed.contains(target) && committed.size()>=MAX_MOBS) { flee(now); return false; }
        if(deadlines.containsKey(target) && now>=deadlines.get(target)) return false;
        committed.add(target); fighting=target; fightUntil=deadlines.computeIfAbsent(target,t -> now+400); lostSight=-1;
        return true;
    }
    public boolean continueFight(long now,double anchorDistanceSquared,boolean visible,boolean valid,float health,int crowd) {
        if(fighting==null) return false;
        if(visible) lostSight=-1; else if(lostSight<0) lostSight=now;
        if(!valid || health<=8 || crowd>=3 || now>=fightUntil || anchorDistanceSquared>144
                || lostSight>=0 && now-lostSight>=60 || strikes>=MAX_STRIKES) {
            abort(now); if(health<=8 || crowd>=3 || strikes>=MAX_STRIKES) fleeing=true; return false;
        }
        return true;
    }
    public boolean mobStrike(UUID target,long now) {
        if(!Objects.equals(fighting,target) || target==null || now>=fightUntil || strikes>=MAX_STRIKES || now-lastStrike<20) return false;
        lastStrike=now; strikes++; return true;
    }
    public void abort(long now) {
        if(fighting!=null) aborted.add(fighting);
        fighting=null; lostSight=-1; huntAfter=Math.max(huntAfter,now+600);
        if(committed.size()>=MAX_MOBS || strikes>=MAX_STRIKES) fleeing=true;
    }
    public void flee(long now) { abort(now); defending=null; defenseUntil=0; fleeing=true; }
    /** Damage must already be confirmed as health/absorption loss. Null means no reliable player owner. */
    public Assault assaulted(UUID player,long now,boolean pvp,float health) {
        abort(now);
        if(player==null) { defending=null; defenseUntil=0; return Assault.RETREAT; }
        Long previous=hits.get(player);
        if(hits.size()<64 || hits.containsKey(player)) hits.put(player,now); else distrustAll=true;
        boolean repeated=previous!=null && now>=previous && now-previous<=200;
        if(previous!=null) refused.add(player);
        if(repeated && hunter && pvp && health>8 && counters<MAX_COUNTERS && !fleeing) {
            // Continued hits never extend an already running episode.
            if(!player.equals(defending) || now>=defenseUntil) { defending=player; defenseUntil=now+160; }
            return Assault.DEFEND;
        }
        defending=null; defenseUntil=0;
        return previous==null?Assault.WARN:Assault.RETREAT;
    }
    public boolean counter(UUID player,long now,boolean melee,boolean playable,boolean pvp) {
        if(defending==null || !defending.equals(player) || now>=defenseUntil || counters>=MAX_COUNTERS || !pvp || !playable) { endDefense(now); return false; }
        if(!melee || now-lastStrike<20) return false;
        lastStrike=now; counters++; return true;
    }
    public boolean defenseActive(long now) { return defending!=null && now<defenseUntil && counters<MAX_COUNTERS; }
    public void endDefense(long now) { defending=null; defenseUntil=0; huntAfter=Math.max(huntAfter,now+600); }
    public static boolean acceptedDamage(float health,float absorption,float afterHealth,float afterAbsorption,boolean accepted) {
        return accepted && (afterHealth<health || afterAbsorption<absorption);
    }
    public static boolean night(long clock) { long t=Math.floorMod(clock,24000); return t>=13000 && t<23000; }
}
