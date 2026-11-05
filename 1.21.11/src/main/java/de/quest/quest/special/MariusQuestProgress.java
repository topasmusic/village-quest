package de.quest.quest.special;
import java.util.*;
/** Uses the same persisted personal friend ledger; only accepted stages receive evidence. */
public final class MariusQuestProgress {
 private final FriendsQuestProgress p;
 public MariusQuestProgress(FriendsQuestProgress progress) {p=progress;}
 public boolean nightKill(UUID mob) {
  if(!p.active("marius") || p.stage("marius")!=0 || mob==null) return false;
  var ids=new ArrayList<>(p.values("marius.nightKills"));if(ids.size()>=15 || ids.contains(mob.toString()))return false;
  ids.add(mob.toString());p.text("marius.nightKills",String.join(",",ids));return true;
 }
 public boolean repair(UUID golem,float healed) {
  if(!p.active("marius") || p.stage("marius")!=1 || golem==null || !Float.isFinite(healed) || healed<=0)return false;
  int before=p.number("marius.healed");if(before>=5000)return false;
  p.number("marius.healed",Math.min(5000,before+Math.round(Math.min(healed,100)*100)));return p.number("marius.healed")>before;
 }
 public boolean raidKill(String dimension,int raid,UUID mob) {
  if(!p.active("marius") || p.stage("marius")!=2 || raid<0 || dimension==null || dimension.isBlank() || mob==null || p.number("marius.victory")==1)return false;
  String identity=dimension+":"+raid;
  if(p.number("marius.raidPhase")!=2 || !identity.equals(p.text("marius.raid")))return false;
  var ids=new ArrayList<>(p.values("marius.raiders"));if(ids.size()>=3 || ids.contains(mob.toString()))return false;
  ids.add(mob.toString());p.text("marius.raiders",String.join(",",ids));return true;
 }
 public boolean victory(String dimension,int raid) {
  if(!p.active("marius") || p.stage("marius")!=2 || p.number("marius.raidPhase")!=2 || p.values("marius.raiders").size()<3 || p.number("marius.victory")==1 || !(dimension+":"+raid).equals(p.text("marius.raid")))return false;
  p.number("marius.victory",1);return true;
 }
 public boolean ready() {return p.active("marius") && switch(p.stage("marius")){case 0 -> p.values("marius.nightKills").size()>=15;case 1 -> p.number("marius.healed")>=5000;case 2 -> p.number("marius.victory")==1;default -> false;};}
 public boolean sealReady(long now) {
  if(p.stage("marius")!=3 || now<0)return false;
  if(p.text("marius.sealUntil").isBlank())return true;
  try {return now>=Long.parseLong(p.text("marius.sealUntil"));}catch(NumberFormatException malformed){return false;}
 }
 public boolean useSeal(long now) {if(!sealReady(now) || now>Long.MAX_VALUE-12000)return false;p.text("marius.sealUntil",Long.toString(now+12000));return true;}
 public long sealRemaining(long now) {
  if(p.stage("marius")!=3 || now<0)return -1;
  if(p.text("marius.sealUntil").isBlank())return 0;
  try {long until=Long.parseLong(p.text("marius.sealUntil"));return until<0?-1:Math.max(0,until-now);}catch(NumberFormatException malformed){return -1;}
 }
 public boolean schedule(long clock) {
  if(!p.active("marius") || p.stage("marius")!=2 || p.number("marius.raidPhase")!=0 || p.number("marius.victory")==1 || clock<0 || clock>Long.MAX_VALUE-24000)return false;
  long day=clock/24000;long due=day*24000+13000;if(due<=clock)due+=24000;
  p.text("marius.raidDue",Long.toString(due));p.number("marius.raidPhase",1);return true;
 }

}
