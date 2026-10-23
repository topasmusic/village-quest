package de.quest.client.ui;

import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

/** V3 family reference materials. Text, data and hit targets stay screen-owned. */
public final class JournalV3Visuals {
    private JournalV3Visuals() {}
    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("village-quest","textures/gui/ui/v3/"+name+".png");
    }
    public static void sprite(GuiGraphics g,String name,int x,int y,int w,int h) {
        if(name.equals("market")) { KingdomUiAssets.detailIcon(g,KingdomUiAssets.DetailIcon.STALL_GREEN,x,y,Math.min(w,h)); return; }
        if(name.equals("wayshrine")) { VillageUiTheme.blitScaled(g,VillageUiTheme.marker("shrine"),x,y,w,h,48,48); return; }
        if(name.equals("danger")) { VillageUiTheme.blitScaled(g,VillageUiTheme.marker("danger"),x,y,w,h,48,48); return; }
        if(name.equals("caravan")) { VillageUiTheme.blitScaled(g,VillageUiTheme.marker("caravan"),x,y,w,h,48,48); return; }
        // Semantic family names share existing packaged art, never a guessed v3 filename.
        String legacy = switch(name) {
            case "special", "atlas", "guide", "quests" -> name;
            case "dispatch" -> "story";
            case "wayshrine" -> "atlas";
            case "guildpath" -> "guide";
            default -> null;
        };
        if(legacy!=null) {
            VillageUiTheme.blitScaled(g,VillageUiTheme.icon(legacy),x,y,w,h,32,32);
            return;
        }
        int sw=40,sh=name.equals("banner")?64:40;
        VillageUiTheme.blitScaled(g,texture(name),x,y,w,h,sw,sh);
    }
    public static void paper(GuiGraphics g,int x,int y,int w,int h) {
        // Sample only quiet parchment. The reference uses fine gold, never a heavy black card frame.
        VillageUiTheme.blitRegionScaled(g,VillageUiTheme.control("card"),x,y,w,h,9,9,46,46,64,64);
        g.fill(x,y,x+w,y+1,0xFFEAC477);g.fill(x,y+1,x+1,y+h,0xFFD3A451);
        g.fill(x,y+h-1,x+w,y+h,0xFF9A662C);g.fill(x+w-1,y+1,x+w,y+h,0xFFAE7937);
        if(w>18 && h>18) {
            g.fill(x+2,y+2,x+w-2,y+3,0x66FFF1C7);
            g.fill(x+2,y+h-3,x+w-2,y+h-2,0x2292602B);
        }
        for(int dx:new int[]{2,w-4}) for(int dy:new int[]{2,h-4}) {
            g.fill(x+dx,y+dy,x+dx+2,y+dy+2,0xFF956126);
            g.fill(x+dx,y+dy,x+dx+1,y+dy+1,0xFFFFDC83);
        }
    }
    public static void heading(GuiGraphics g,Font f,String text,float x,float y,int max,float scale,int color) {
        String label=VillageUiTheme.ellipsize(f,text,Math.max(1,(int)((max-1)/scale)));
        VillageUiTheme.drawStringScaled(g,f,label,x,y,color,scale);
        VillageUiTheme.drawStringScaled(g,f,label,x+scale*.5f,y,color,scale);
    }
    public static void centeredHeading(GuiGraphics g,Font f,String label,int x,int y,int w,float scale,int color) {
        scale=Math.min(scale,w/(float)Math.max(1,f.width(label)));
        heading(g,f,label,x+(w-f.width(label)*scale)/2,y,w+2,scale,color);
    }
    public static void title(GuiGraphics g,Font f,String label,int x,int y,int w) {
        title(g,f,label,x,y,w,"book");
    }
    public static void titlePlate(GuiGraphics g,int x,int y,int w) {
        int ivy=Math.round(w*.135f);
        VillageUiTheme.blitScaled(g,texture("title_plaque_v4"),x-ivy,y-2,w+ivy*2,49,264,76);
    }
    public static void logistics(GuiGraphics g,int x,int y,int w,int h) {
        framedScene(g,texture("dispatch_freight_v5"),x,y,w,h,new QuestArtwork.ContentArea(256,96,0,0,256,96));
    }
    /** One inset frame and one content crop for every large family illustration. */
    public static void illustration(GuiGraphics g,Identifier image,int x,int y,int w,int h) {
        framedScene(g,image,x,y,w,h,QuestArtwork.contentArea(image));
    }
    public static void thumbnail(GuiGraphics g,Identifier image,int x,int y,int w,int h) {
        scene(g,image,x,y,w,h,QuestArtwork.contentArea(image));
    }
    private static void framedScene(GuiGraphics g,Identifier image,int x,int y,int w,int h,QuestArtwork.ContentArea content) {
        if(w<=4 || h<=4)return;
        // Thin warm bevel and inset shadow; image owns the entire remaining opening.
        g.fill(x,y,x+w,y+h,0xFF93652F);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFFF0D4A0);
        g.fill(x+2,y+2,x+w-2,y+h-2,0xFF604731);
        scene(g,image,x+3,y+3,w-6,h-6,content);
    }
    private static void scene(GuiGraphics g,Identifier image,int x,int y,int w,int h,QuestArtwork.ContentArea content) {
        if(w<=0 || h<=0)return;
        double fit=Math.max(w/(double)content.width(),h/(double)content.height());
        int sw=Math.clamp((int)Math.round(w/fit),1,content.width());
        int sh=Math.clamp((int)Math.round(h/fit),1,content.height());
        VillageUiTheme.blitRegionScaled(g,image,x,y,w,h,content.x()+(content.width()-sw)/2,
                content.y()+(content.height()-sh)/2,sw,sh,content.textureWidth(),content.textureHeight());
    }
    /** Feathered local backing without a hard rectangle across the painted scene. */
    public static void textWash(GuiGraphics g,int x,int y,int w,int h) {
        VillageUiTheme.blitScaled(g,texture("hero_text_wash"),x,y,w,h,356,170);
    }
    /** Shared safe paper opening: live contents and footers stay inside the 32px rails. */
    public static void insetBoard(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+12,y+22,x+w-12,y+h-14,VillageUiTheme.WOOD);
        paper(g,x+28,y+39,w-56,h-78);
        VillageUiTheme.drawFrame(g,x,y,w,h);
        ornaments(g,x,y,w,h);
    }

    public static void title(GuiGraphics g,Font f,String label,int x,int y,int w,String icon) {
        titlePlate(g,x,y,w);
        float scale=Math.min(1.75f,Math.max(.9f,(w-46f)/Math.max(1,f.width(label))));
        String visible=VillageUiTheme.ellipsize(f,label,(int)((w-46)/scale));
        int size=icon.equals("compass")||icon.equals("wayshrine")?22:27;
        float group=size+7+f.width(visible)*scale;
        int start=x+Math.round((w-group)/2);
        sprite(g,icon,start,y+(icon.equals("market")?-5:(30-size)/2-2),size,size);
        heading(g,f,visible,start+size+7,y+(30-f.lineHeight*scale)/2-2,w-(start-x)-39,scale,VillageUiTheme.INK);
    }
    public static void ornaments(GuiGraphics g,int x,int y,int w,int h) {
        VillageUiTheme.blitScaled(g,texture("corner_lantern"),x-10,y+h-87,84,87,68,70);
        VillageUiTheme.blitScaled(g,texture("corner_ivy"),x+w-37,y+h-98,36,98,42,80);
    }
    /** The same rectangle is passed to rendering and input; never an action chevron. */
    public static void close(GuiGraphics g,int x,int y,int w,int h,boolean hover) {
        g.fill(x+2,y+2,x+w-2,y+h-2,hover?0xFF377B70:0xFF174A48);
        int cx=x+w/2,cy=y+h/2,r=Math.max(3,Math.min(w,h)/4);
        for(int i=-r;i<=r;i++) {
            g.fill(cx+i,cy+i,cx+i+2,cy+i+2,0xFFFFD366);
            g.fill(cx+i,cy-i,cx+i+2,cy-i+2,0xFFFFD366);
        }
    }
    public static void chevron(GuiGraphics g,int x,int y,int size,boolean expanded) {
        int r=Math.max(2,size/4),cx=x+size/2,cy=y+size/2;
        for(int i=-r;i<=r;i++) {
            int py=cy+(expanded?-1:1)*(r-Math.abs(i))-r/2;
            g.fill(cx+i,py,cx+i+2,py+2,0xFFFFE5A5);
        }
    }
    /** Quiet navigation surface; primary actions keep the petrol fill. */
    public static void secondary(GuiGraphics g,Font f,String label,int x,int y,int w,int h,boolean hover) {
        paper(g,x,y,w,h);
        if(hover)g.fill(x+2,y+2,x+w-2,y+h-2,0x22365E59);
        String visible=VillageUiTheme.ellipsize(f,label,(int)((w-12)/.8f));
        VillageUiTheme.drawStringScaled(g,f,visible,x+(w-f.width(visible)*.8f)/2,y+(h-7)/2f,VillageUiTheme.INK,.8f);
    }
    /** Teal footer controls keep their identity; unavailable actions remain visibly muted. */
    public static void tealButton(GuiGraphics g,Font f,String label,int x,int y,int w,int h,boolean enabled,boolean hover) {
        g.fill(x,y,x+w,y+h,0xFF9A662C);
        g.fill(x+1,y+1,x+w-1,y+h-1,enabled?0xFFF0C775:0xFFB6AB83);
        g.fill(x+2,y+2,x+w-2,y+h-2,!enabled?0xFF61746C:hover?0xFF346D65:0xFF234E4C);
        g.fill(x+3,y+3,x+w-3,y+4,enabled?0xFF527D71:0xFF84978A);
        float scale=h>=18?.92f:.8f;
        String visible=VillageUiTheme.ellipsize(f,label,Math.max(1,(int)((w-12)/scale)));
        VillageUiTheme.drawStringScaled(g,f,visible,x+(w-f.width(visible)*scale)/2,y+(h-f.lineHeight*scale)/2,
                enabled?VillageUiTheme.LIGHT_TEXT:0xFFD3DAC9,scale);
    }
    public static void action(GuiGraphics g,Font f,String label,int x,int y,int w,int h,boolean hover) {
        g.fill(x,y,x+w,y+h,0xFF80511E);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFFE5BA68);
        g.fill(x+2,y+2,x+w-2,y+h-2,hover?0xFF346D65:0xFF234E4C);
        g.fill(x+3,y+3,x+w-3,y+4,0xFF4C7770);
        boolean arrow=w>=60 && !label.isBlank();
        float scale=h>=18?.92f:.8f;
        String visible=VillageUiTheme.ellipsize(f,label,Math.max(1,(int)((w-(arrow?18:8))/scale)));
        VillageUiTheme.drawStringScaled(g,f,visible,x+(w-f.width(visible)*scale)/2-2,y+(h-7)/2f,VillageUiTheme.LIGHT_TEXT,scale);
        if(arrow)VillageUiTheme.drawStringScaled(g,f,"›",x+w-10,y+(h-7)/2f,0xFFF3C95D,.85f);
    }
    public static void section(GuiGraphics g,Font f,String text,int x,int y,int w) {
        section(g,f,text,x,y,w,.84f);
    }
    public static void section(GuiGraphics g,Font f,String text,int x,int y,int w,float scale) {
        section(g,f,text,x,y,w,scale,null);
    }
    public static void section(GuiGraphics g,Font f,String text,int x,int y,int w,float scale,String icon) {
        g.fill(x,y,x+w,y+18,0xFFBD9147);g.fill(x+1,y+1,x+w-1,y+17,0xFF315854);
        if(icon!=null)sprite(g,icon,x+3,y+1,16,16);
        int inset=icon==null?8:23;
        heading(g,f,text,x+inset,y+(scale>.9f?4:5),w-inset-8,scale,VillageUiTheme.LIGHT_TEXT);
    }
}
