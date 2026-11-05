package de.quest.client.ui;

import de.quest.client.compat.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Drawn last in the same panel transform as its controls, instead of a deferred vanilla tooltip. */
public final class KingdomUiHints {
    private List<Component> content=List.of();
    private int mouseX,mouseY;
    public void clear() { content=List.of(); }
    public void setTooltipForNextFrame(Font font,Component text,int x,int y) { setTooltipForNextFrame(font,List.of(text),x,y); }
    public void setTooltipForNextFrame(Font font,List<Component> text,int x,int y) { content=List.copyOf(text); mouseX=x;mouseY=y; }
    public record Layout(int x,int y,int width,int height,List<String> lines) {}
    public static Layout layout(Font font,List<Component> content,int mouseX,int mouseY,int left,int top,int width,int height) {
        int maxWidth=Math.min(254,width-48),maxLines=Math.max(1,Math.min(10,(height-72)/9));
        List<String> lines=new ArrayList<>();
        for(var paragraph:content) for(String part:paragraph.getString().split("\n")) {
            for(String wrapped:VillageUiTheme.wrapScaledLines(font,part,maxWidth,.75f)) {
                var row=new StringBuilder();
                for(int cp:wrapped.codePoints().toArray()) {
                    String glyph=new String(Character.toChars(cp));
                    if(!row.isEmpty() && font.width(row+glyph)*.75f>maxWidth) { lines.add(row.toString()); row.setLength(0); }
                    row.append(glyph);
                }
                if(!row.isEmpty()) lines.add(row.toString());
            }
        }
        if(lines.size()>maxLines) { lines=new ArrayList<>(lines.subList(0,maxLines)); lines.set(maxLines-1,VillageUiTheme.ellipsize(font,lines.getLast()+" ...",(int)(maxWidth/.75f))); }
        int boxWidth=Math.max(32,lines.stream().mapToInt(font::width).max().orElse(0)*3/4+16),boxHeight=lines.size()*9+14;
        int x=Math.max(left+18,Math.min(mouseX+12,left+width-18-boxWidth));
        int y=mouseY+15;
        if(y+boxHeight>top+height-18) y=mouseY-12-boxHeight;
        y=Math.max(top+43,Math.min(y,top+height-18-boxHeight));
        return new Layout(x,y,boxWidth,boxHeight,List.copyOf(lines));
    }
    public void draw(GuiGraphics g,Font font,int left,int top,int width,int height) {
        if(content.isEmpty()) return;
        var box=layout(font,content,mouseX,mouseY,left,top,width,height);
        g.fill(box.x()+2,box.y()+2,box.x()+box.width()+2,box.y()+box.height()+2,0x66000000);
        VillageUiTheme.drawPaper(g,box.x(),box.y(),box.width(),box.height());
        for(int i=0;i<box.lines().size();i++) VillageUiTheme.drawStringScaled(g,font,box.lines().get(i),box.x()+8,box.y()+7+i*9,VillageUiTheme.INK,.75f);
    }
}
