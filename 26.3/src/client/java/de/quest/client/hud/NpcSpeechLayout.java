package de.quest.client.hud;

/** Pure presentation geometry. Coordinates are logical pixels before distance scaling. */
public final class NpcSpeechLayout {
    public static final int WIDTH=208,LINES=4,BODY_Y=23,LINE_STEP=10,TAIL=10,BORDER=8;
    private NpcSpeechLayout() {}
    public static float scale(double distance) {
        if(!Double.isFinite(distance) || distance<0 || distance>16) return 0;
        return (float)(.75*Math.min(1,3/Math.max(3,distance)));
    }
    public static int height(int shown,boolean paged) {
        return BODY_Y+Math.max(1,shown)*LINE_STEP+6+(paged?12:0)+BORDER+TAIL;
    }
    public static int page(int lines,long elapsed,int duration) {
        int pages=Math.max(1,(lines+LINES-1)/LINES);
        return (int)Math.min(pages-1,Math.max(0,elapsed)*pages/Math.max(1,duration));
    }
    public static String body(String speaker,String line) {
        String prefix=speaker+": ";
        return line.startsWith(prefix)?line.substring(prefix.length()):line;
    }
}
