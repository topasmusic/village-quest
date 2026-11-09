package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.config.GameplayStats;
import de.quest.client.ui.VillageUiTheme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Deliberate, fixed answers only; one answer per target/question group/UTC day. */
public final class StatsFeedbackScreen extends CompatScreen {
    private static final int W=450,H=420,INK=0xFF493721;
    private static final String[] TARGETS={"general","daily","weekly","story","special","town","commission","freight","shared","afterstory"};
    private static final String[] ANSWERS={"helpful","confusing","easy","balanced","hard","progress","rewards","ui"};
    private final Screen back;
    private int target;
    private String status="";
    private net.minecraft.client.gui.components.EditBox message;
    private boolean sending;
    @Override protected void init() {
        String old=message==null?"":message.getValue();
        message=new net.minecraft.client.gui.components.EditBox(font,(width-W)/2+18,(height-H)/2+277,414,18,Component.translatable("screen.village-quest.stats.feedback.text.label"));
        message.setMaxLength(500);message.setBordered(false);message.setTextColor(INK);message.setTextShadow(false);message.setValue(old);addRenderableWidget(message);
    }
    @Override protected boolean suppressInventoryKeyClose(){return message!=null&&message.isFocused();}
    public StatsFeedbackScreen(Screen back) {
        super(Component.translatable("screen.village-quest.stats.feedback.title")); this.back=back;
        for(int i=0;i<TARGETS.length;i++) if(TARGETS[i].equals(GameplayStats.feedbackTarget())) target=i;
    }
    @Override public boolean isPauseScreen(){return false;}
    private static String text(String key){return Component.translatable("screen.village-quest.stats.feedback."+key).getString();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        float scale=beginResponsivePanel(g,W,H);
        try {
            int x=(width-W)/2,y=(height-H)/2,mx=responsiveMouseX(mouseX,W,H),my=responsiveMouseY(mouseY,W,H);
            VillageUiTheme.drawPaper(g,x,y,W,H);
            VillageUiTheme.drawStringScaled(g,font,text("title"),x+14,y+12,INK,1);
            VillageUiTheme.drawWrappedScaled(g,font,text("notice"),x+14,y+34,W-28,INK,.85f,3);
            VillageUiTheme.drawButton(g,font,x+14,y+74,W-28,23,text("target."+TARGETS[target]),true,mx>=x+14&&mx<x+W-14&&my>=y+74&&my<y+97,false);
            for(int i=0;i<ANSWERS.length;i++) {
                int bx=x+14+(i%2)*214,by=y+106+(i/2)*29;
                VillageUiTheme.drawButton(g,font,bx,by,208,23,text("answer."+ANSWERS[i]),GameplayStats.enabled(),mx>=bx&&mx<bx+208&&my>=by&&my<by+23,false);
            }
            VillageUiTheme.drawWrappedScaled(g,font,text(!GameplayStats.enabled()?"disabled":status.isEmpty()?"limit":status),x+14,y+230,W-28,INK,.85f,2);
            VillageUiTheme.drawStringScaled(g,font,text("text.label"),x+14,y+260,INK,.85f);
            g.fill(x+14,y+274,x+436,y+298,0xFFE2CFAB);
            VillageUiTheme.drawWrappedScaled(g,font,text(GameplayStats.localMode()?"text.notice.local":"text.notice"),x+14,y+304,W-28,INK,.8f,4);
            VillageUiTheme.drawButton(g,font,x+14,y+350,W-28,23,text(sending?"text.sending":"text.send"),!sending&&GameplayStats.enabled(),mx>=x+14&&mx<x+436&&my>=y+350&&my<y+373,false);
            if(message!=null)super.render(g,mx,my,delta);
            VillageUiTheme.drawButton(g,font,x+14,y+390,W-28,22,text("back"),true,mx>=x+14&&mx<x+W-14&&my>=y+390&&my<y+412,false);
        } finally {endResponsivePanel(g,scale);}
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click,boolean doubled){
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT&&click.button()!=InputConstants.MOUSE_BUTTON_RIGHT)return true;
        int mx=responsiveMouseX(click.x(),W,H)-(width-W)/2,my=responsiveMouseY(click.y(),W,H)-(height-H)/2;
        if(mx>=14&&mx<W-14&&my>=74&&my<97){target=Math.floorMod(target+(click.button()==InputConstants.MOUSE_BUTTON_RIGHT?-1:1),TARGETS.length);status="";return true;}
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT)return true;
        if(mx>=14&&mx<436&&my>=350&&my<373&&!sending&&message!=null){
            if(!GameplayStats.canSendText()){status="text.limit";return true;}
            if(!GameplayStats.validFeedbackText(message.getValue())){status="text.invalid";return true;}
            sending=true;status="text.sending";
            GameplayStats.sendText(TARGETS[target],message.getValue(),ok->{sending=false;status=ok?"text.sent":"text.failed";if(ok&&message!=null)message.setValue("");});return true;
        }
        if(message!=null){
            boolean inside=mx>=14&&mx<436&&my>=274&&my<298;
            message.setFocused(inside);setFocused(inside?message:null);
            if(inside)return message.mouseClicked(new MouseButtonEvent(mx+(width-W)/2,my+(height-H)/2,click.buttonInfo()),doubled);
        }
        for(int i=0;i<ANSWERS.length;i++){
            int bx=14+(i%2)*214,by=106+(i/2)*29;
            if(mx>=bx&&mx<bx+208&&my>=by&&my<by+23){status=GameplayStats.feedback(TARGETS[target],ANSWERS[i])?"sent":"limit";return true;}
        }
        if(mx>=14&&mx<W-14&&my>=390&&my<412)onClose();
        return true;
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(back);}
}
