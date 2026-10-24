package de.quest.client.ui;

import de.quest.client.compat.GuiGraphics;
import de.quest.kingdom.BuildingScanner;
import net.minecraft.resources.Identifier;

/** Shared Living Kingdoms art; translated text and server data remain screen-owned. */
public final class KingdomUiAssets {
    public enum Icon { COUNCIL, INN, MARKET, STOREHOUSE, TRADE, SUPPLY, FRIENDSHIP, VILLAGE_CHECK, SPEECH }
    public static final Identifier ICONS=Identifier.fromNamespaceAndPath("village-quest","textures/gui/living_kingdoms/icons.png");
    public static final Identifier SPEECH=Identifier.fromNamespaceAndPath("village-quest","textures/gui/living_kingdoms/speech_frame.png");
    public static final Identifier MANAGEMENT_FRAME=Identifier.fromNamespaceAndPath("village-quest","textures/gui/living_kingdoms/management_frame.png");
    public static final Identifier MANAGEMENT_ICONS=Identifier.fromNamespaceAndPath("village-quest","textures/gui/living_kingdoms/management_icons.png");
    public enum DetailIcon { THRONE, FOUNDING, STALL_GREEN, STALL_RED, STALL_BLUE, STALL_YELLOW, STALL_LIME, ADULT, BED, JOB, LANTERN, TORCH, ROOF, TABLE, CHAIR, BOOK }
    private KingdomUiAssets() {}
    public static Icon role(BuildingScanner.Role role) {
        return switch(role) { case COUNCIL -> Icon.COUNCIL; case INN -> Icon.INN; case MARKET -> Icon.MARKET; case STOREHOUSE -> Icon.STOREHOUSE; };
    }
    public static void icon(GuiGraphics g,Icon icon,int x,int y,int size) {
        int slot=icon.ordinal();
        VillageUiTheme.blitRegionScaled(g,ICONS,x,y,size,size,(slot%3)*32,(slot/3)*32,32,32,96,96);
    }
    public static void panel(GuiGraphics g,int x,int y,int width,int height) {
        VillageUiTheme.drawPanelShadow(g,x,y,width,height);
        VillageUiTheme.drawBoard(g,x,y,width,height);
    }
    public record CloseBounds(int x,int y,int width,int height) {
        public boolean contains(int mx,int my) { return mx>=x && mx<x+width && my>=y && my<y+height; }
    }
    /** One fixed close target, independent of the panel's aspect ratio. */
    public static CloseBounds closeBounds(int x,int y,int width,int height) {
        return new CloseBounds(x+width-25,y+3,20,20);
    }
    public static void closeButton(GuiGraphics g,int x,int y,int width,int height,boolean hovered) {
        var b=closeBounds(x,y,width,height);
        JournalV3Visuals.close(g,b.x(),b.y(),b.width(),b.height(),hovered);
    }
    /** Shared live title plaque, leaving the role icon and close target free. */
    public static void header(GuiGraphics g,net.minecraft.client.gui.Font font,String title,int x,int y,int width) {
        if(font==null) return;
        int available=width-152;
        JournalV3Visuals.titlePlate(g,x+68,y+7,width-136);
        JournalV3Visuals.centeredHeading(g,font,title,x+76,y+16,available,1.4f,VillageUiTheme.INK);
    }
    public static void managementHeader(GuiGraphics g,net.minecraft.client.gui.Font font,String title,DetailIcon icon,int x,int y,int width) {
        if(font==null) return;
        int available=width-136;
        JournalV3Visuals.titlePlate(g,x+68,y+3,available);
        float scale=Math.min(1.4f,(available-44f)/Math.max(1,font.width(title)));
        int textWidth=Math.round(font.width(title)*scale),start=x+(width-textWidth-32)/2;
        // Match the visible artwork to the live title baseline, not the atlas tile padding.
        int iconOffset=switch(icon) {
            case THRONE, JOB -> 2;
            case LANTERN -> 0;
            case BOOK -> 3;
            default -> -1;
        };
        detailIcon(g,icon,start,y+iconOffset,23);
        JournalV3Visuals.heading(g,font,title,start+32,y+10,textWidth+2,scale,VillageUiTheme.INK);
    }
    public static void detailIcon(GuiGraphics g,DetailIcon icon,int x,int y,int size) {
        int slot=icon.ordinal();
        int sx=Math.round((slot%4)*1254/4f),sy=Math.round((slot/4)*1254/4f);
        int sw=Math.round((slot%4+1)*1254/4f)-sx,sh=Math.round((slot/4+1)*1254/4f)-sy;
        VillageUiTheme.blitRegionScaled(g,MANAGEMENT_ICONS,x,y,size,size,sx,sy,sw,sh,1254,1254);
    }
    public static DetailIcon stall(int slot) { return DetailIcon.values()[DetailIcon.STALL_GREEN.ordinal()+Math.floorMod(slot,5)]; }
    public static DetailIcon buildingIcon(BuildingScanner.Role role) {
        return switch(role) { case COUNCIL -> DetailIcon.THRONE; case INN -> DetailIcon.LANTERN; case MARKET -> DetailIcon.STALL_GREEN; case STOREHOUSE -> DetailIcon.JOB; };
    }
    public static DetailIcon checkIcon(BuildingScanner.Check check) {
        return switch(check) {
            case LIGHT -> DetailIcon.LANTERN; case ROOF -> DetailIcon.ROOF; case TABLE,COUNTER -> DetailIcon.TABLE;
            case SEATS -> DetailIcon.CHAIR; case BOOKS -> DetailIcon.BOOK; case BED -> DetailIcon.BED;
            case BARRELS,CONTAINERS,HANDOVER -> DetailIcon.JOB; case WORKSTATION -> DetailIcon.STALL_GREEN;
            default -> DetailIcon.FOUNDING;
        };
    }
    /** A recognizable picture, a short label and a textual status: color is never the sole signal. */
    public static void metric(GuiGraphics g,net.minecraft.client.gui.Font font,DetailIcon icon,String label,String value,
                              int x,int y,int width,int height,boolean complete) {
        card(g,x,y,width,height,complete);
        int size=Math.min(26,height-8); detailIcon(g,icon,x+5,y+(height-size)/2,size);
        VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,label,(int)((width-size-17)/.65f)),x+size+10,y+6,0xFF3E2918,.65f);
        VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,value,(int)((width-size-17)/.72f)),x+size+10,y+height-12,
                complete?0xFF306C42:0xFF80694F,.72f);
    }
    public static void statTile(GuiGraphics g,net.minecraft.client.gui.Font font,DetailIcon icon,String label,String value,
                                int x,int y,int width,int height,boolean hovered) {
        card(g,x,y,width,height,hovered);detailIcon(g,icon,x+(width-28)/2,y+5,28);
        String text=VillageUiTheme.ellipsize(font,label,(int)((width-8)/.65f));
        VillageUiTheme.drawStringScaled(g,font,text,x+(width-font.width(text)*.65f)/2,y+38,0xFF3E2918,.65f);
        VillageUiTheme.drawStringScaled(g,font,value,x+(width-font.width(value)*.85f)/2,y+52,0xFF3E2918,.85f);
    }
    /** Exact server amounts, with the same coins as the inventory; compact numbers retain a full tooltip at callers. */
    public static void money(GuiGraphics g,net.minecraft.client.gui.Font font,long amount,int right,int y,boolean light) {
        long value=Math.max(0,amount),crowns=value/de.quest.economy.CurrencyService.CROWN,silver=value%de.quest.economy.CurrencyService.CROWN;
        String a=Long.toString(crowns),b=Long.toString(silver); float scale=.9f;
        if(a.length()>6) a=String.format(java.util.Locale.ROOT,"%.1fM",crowns/1000000.0);
        int width=(crowns>0?19+Math.round(font.width(a)*scale):0)+16+Math.round(font.width(b)*scale),x=right-width;
        int color=light?0xFFFFE9BE:0xFF3E2918;
        if(crowns>0) { coin(g,new net.minecraft.world.item.ItemStack(de.quest.registry.ModItems.CROWN),x,y);
            VillageUiTheme.drawStringScaled(g,font,a,x+15,y+2,color,scale);x+=19+Math.round(font.width(a)*scale); }
        coin(g,new net.minecraft.world.item.ItemStack(de.quest.registry.ModItems.SILVERMARK),x,y);
        VillageUiTheme.drawStringScaled(g,font,b,x+15,y+2,color,scale);
    }
    /** Center the whole native coin/amount group inside the actual price target. */
    public static void moneyCentered(GuiGraphics g,net.minecraft.client.gui.Font font,long amount,int x,int y,int width,boolean light) {
        long value=Math.max(0,amount),crowns=value/de.quest.economy.CurrencyService.CROWN;
        String a=crowns>999999?String.format(java.util.Locale.ROOT,"%.1fM",crowns/1000000.0):Long.toString(crowns);
        String b=Long.toString(value%de.quest.economy.CurrencyService.CROWN);
        int span=(crowns>0?19+Math.round(font.width(a)*.9f):0)+16+Math.round(font.width(b)*.9f);
        float scale=Math.min(1f,Math.max(1,width-8)/(float)Math.max(1,span));
        var pose=g.pose();pose.pushMatrix();pose.translate(x+width/2f,y);pose.scale(scale,scale);
        money(g,font,amount,span/2,0,light);pose.popMatrix();
    }
    private static void coin(GuiGraphics g,net.minecraft.world.item.ItemStack stack,int x,int y) {
        var pose=g.pose();pose.pushMatrix();pose.translate(x,y);pose.scale(.8f,.8f);g.renderItem(stack,0,0);pose.popMatrix();
    }
    public static void card(GuiGraphics g,int x,int y,int width,int height,boolean selected) {
        VillageUiTheme.drawCard(g,x,y,width,height,false,selected);
    }
    public static void section(GuiGraphics g,net.minecraft.client.gui.Font font,String title,int x,int y,int width,boolean complete) {
        g.fill(x,y,x+width,y+17,0xFF9A783F);
        g.fill(x+1,y+1,x+width-1,y+16,complete?0xFF315D56:0xFF526762);
        VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,title,(int)((width-14)/.78f)),x+7,y+5,0xFFFFE9BE,.78f);
    }
    /** Corners retain native size, only quiet paper/edge strips stretch; the tail stays centered. */
    public static void speechFrame(GuiGraphics g,int x,int y,int width,int height) {
        if(width<32 || height<26) return;
        int bodyHeight=height-10,cx=x+width/2;
        VillageUiTheme.drawPaper(g,x,y,width,bodyHeight);
        for(int row=0;row<10;row++) {
            int half=(10-row)/2;
            g.fill(cx-half-1,y+bodyHeight-1+row,cx+half+2,y+bodyHeight+row,VillageUiTheme.GOLD);
            g.fill(cx-half,y+bodyHeight-1+row,cx+half+1,y+bodyHeight+row,VillageUiTheme.PAPER);
        }
    }
}
