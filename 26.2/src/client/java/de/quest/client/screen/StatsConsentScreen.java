package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.config.GameplayStats;
import de.quest.client.ui.VillageUiTheme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Short player-facing overview; the complete notice remains one click away. */
public final class StatsConsentScreen extends CompatScreen {
    private static final int W=450, INK=0xFF493721, GREEN=0xFF2D6837, RED=0xFF9C3C32;
    private final Screen back;
    private boolean failed, details;
    public StatsConsentScreen(Screen back) {
        super(Component.translatable("screen.village-quest.stats.title")); this.back=back;
    }
    private int panelHeight() { return details ? 390 : 304; }
    @Override public boolean isPauseScreen() { return false; }
    private static String text(String key) { return Component.translatable("screen.village-quest.stats."+key).getString(); }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        int h=panelHeight(); float scale=beginResponsivePanel(g,W,h);
        try {
            int x=(width-W)/2,y=(height-h)/2;
            int mx=responsiveMouseX(mouseX,W,h),my=responsiveMouseY(mouseY,W,h);
            VillageUiTheme.drawPaper(g,x,y,W,h);
            VillageUiTheme.drawStringScaled(g,font,text("title"),x+14,y+12,INK,1);
            if (details) {
                VillageUiTheme.drawWrappedScaled(g,font,text(GameplayStats.localMode()?"consent.local":"consent"),x+14,y+35,W-28,INK,.85f,29);
                VillageUiTheme.drawWrappedScaled(g,font,GameplayStats.privacyUrl(),x+14,y+328,W-28,INK,.7f,2);
            } else {
                VillageUiTheme.drawWrappedScaled(g,font,text(GameplayStats.localMode()?"summary.intro.local":"summary.intro"),x+14,y+34,W-28,INK,.85f,2);
                section(g,x,y+60,"collect",GREEN,0x1835683D);
                section(g,x,y+124,"private",RED,0x189C3C32);
                VillageUiTheme.drawWrappedScaled(g,font,text(GameplayStats.localMode()?"summary.note.local":"summary.note"),x+14,y+191,W-28,INK,.85f,4);
            }
            int infoY=y+(details?304:236),buttonY=y+(details?360:274);
            VillageUiTheme.drawButton(g,font,x+14,infoY,422,18,text(details?"summary.back":"summary.details"),true,
                    mx>=x+14&&mx<x+436&&my>=infoY&&my<infoY+18,false);
            if(failed) VillageUiTheme.drawStringScaled(g,font,text("error"),x+14,y+(details?346:260),RED,.7f);
            for(int i=0;i<2;i++) {
                int bx=x+14+i*214;
                VillageUiTheme.drawButton(g,font,bx,buttonY,208,22,text(i==0?"adult_yes":"no"),true,
                        mx>=bx&&mx<bx+208&&my>=buttonY&&my<buttonY+22,false);
            }
        } finally { endResponsivePanel(g,scale); }
    }
    private void section(GuiGraphics g,int x,int y,String key,int color,int tint) {
        g.fill(x+14,y,x+436,y+58,tint);
        g.fill(x+14,y,x+17,y+58,color);
        VillageUiTheme.drawStringScaled(g,font,text("summary."+key+".title"),x+24,y+6,color,.9f);
        VillageUiTheme.drawWrappedScaled(g,font,text("summary."+key+".body"),x+24,y+22,402,color,.85f,4);
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click,boolean doubled) {
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT)return true;
        int h=panelHeight(),mx=responsiveMouseX(click.x(),W,h),my=responsiveMouseY(click.y(),W,h);
        int x=(width-W)/2,y=(height-h)/2,infoY=y+(details?304:236),buttonY=y+(details?360:274);
        if(mx>=x+14&&mx<x+436&&my>=infoY&&my<infoY+18){details=!details;return true;}
        if(my>=buttonY&&my<buttonY+22) {
            if(mx>=x+14&&mx<x+222){if(GameplayStats.choose(true))onClose();else failed=true;}
            else if(mx>=x+228&&mx<x+436)onClose();
        }
        return true;
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.gui.setScreen(back);}
}
