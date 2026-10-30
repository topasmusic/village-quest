package de.quest.content.story;

import de.quest.quest.story.StoryQuestKeys;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;

/** Bounded exact sectors alongside opaque legacy evidence; no inverse hash guesses. */
public final class NightBellsMarkerProgress {
    private NightBellsMarkerProgress() {}
    private static String prefix(int chapter) { return "night_bells_peaceful_exact_"+chapter+"_"; }
    public static List<String> keys(int chapter) {
        var keys=new ArrayList<String>();keys.add(prefix(chapter)+"format");
        for(int i=0;i<4;i++) for(String field:List.of("set","x","z")) keys.add(prefix(chapter)+i+"_"+field);
        return List.copyOf(keys);
    }
    private static String legacy(int chapter,int slot) { return StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+chapter+"_"+slot; }
    private static int token(int x,int z) { int hash=31*x+z;return hash==0?1:hash; }
    public static boolean supported(ToIntFunction<String> get,int chapter) {
        int format=get.applyAsInt(prefix(chapter)+"format");return format==0 || format==1;
    }
    public static boolean isMarkerKey(String key,int chapter) {
        return key.startsWith(prefix(chapter)) || key.startsWith(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+chapter+"_");
    }
    public static void merge(ToIntFunction<String> source,ToIntFunction<String> target,ObjIntConsumer<String> set,int chapter,int limit) {
        if(!supported(source,chapter) || !supported(target,chapter))return;
        for(int i=0;i<4 && count(target,chapter)<limit;i++) {
            int value=source.applyAsInt(legacy(chapter,i));if(value==0)continue;
            boolean known=false;
            for(int j=0;j<4;j++) {
                String key=prefix(chapter)+j+"_";
                if(target.applyAsInt(legacy(chapter,j))==value || target.applyAsInt(key+"set")==1
                        && token(target.applyAsInt(key+"x"),target.applyAsInt(key+"z"))==value)known=true;
            }
            if(known)continue;
            for(int j=0;j<4;j++)if(target.applyAsInt(legacy(chapter,j))==0){set.accept(legacy(chapter,j),value);break;}
        }
        for(var pos:exactSectors(source,chapter))record(target,set,chapter,pos,limit);
    }
    public static int count(ToIntFunction<String> get,int chapter) {
        int total=0;
        for(int i=0;i<4;i++) {
            if(get.applyAsInt(legacy(chapter,i))!=0)total++;
            if(get.applyAsInt(prefix(chapter)+i+"_set")==1)total++;
        }
        return Math.min(4,total);
    }
    public static boolean available(ToIntFunction<String> get,int chapter,int x,int z) {
        if(!supported(get,chapter))return false;
        for(int i=0;i<4;i++) {
            // Old coordinates are unrecoverable: retain their normalized token as an exclusion.
            if(get.applyAsInt(legacy(chapter,i))==token(x,z))return false;
            String key=prefix(chapter)+i+"_";
            if(get.applyAsInt(key+"set")==1 && get.applyAsInt(key+"x")==x && get.applyAsInt(key+"z")==z)return false;
        }
        return true;
    }
    public static boolean record(ToIntFunction<String> get,ObjIntConsumer<String> set,int chapter,BlockPos pos,int target) {
        int x=Math.floorDiv(pos.getX(),8),z=Math.floorDiv(pos.getZ(),8);
        if(count(get,chapter)>=target || !available(get,chapter,x,z))return false;
        for(int i=0;i<4;i++) {
            String key=prefix(chapter)+i+"_";
            if(get.applyAsInt(key+"set")!=0)continue;
            set.accept(key+"x",x);set.accept(key+"z",z);set.accept(key+"set",1);
            set.accept(prefix(chapter)+"format",1);return true;
        }
        return false;
    }
    public static List<BlockPos> exactSectors(ToIntFunction<String> get,int chapter) {
        var result=new ArrayList<BlockPos>();
        for(int i=0;i<4;i++) {
            String key=prefix(chapter)+i+"_";
            if(get.applyAsInt(key+"set")==1)result.add(new BlockPos(get.applyAsInt(key+"x")*8,0,get.applyAsInt(key+"z")*8));
        }
        return List.copyOf(result);
    }
}
