package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.*;
import de.quest.network.EncounterQuestPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A small VQ conversation, using the existing panel, paper, plaque and turquoise controls. */
public final class EncounterQuestScreen extends CompatScreen {
    static final int W=430,H=340,TOP=76,BOTTOM=225;
    private View view;private int scroll,maxScroll,focus;private boolean pending;
    private final List<Control> controls=new ArrayList<>();
    private record Control(int x,int y,int w,int h,Runnable action){boolean contains(int mx,int my){return mx>=x && mx<x+w && my>=y && my<y+h;}}
    public EncounterQuestScreen(View view){super(view.name());this.view=view;}
    public UUID session(){return view.session();}
    public void update(View next){view=next;pending=false;}
    @Override public boolean isPauseScreen(){return false;}
    private static String tr(String key){return Component.translatable("quest.village-quest.encounter."+key).getString();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        float scale=beginResponsivePanel(g,W,H);
        try{
            int x=(width-W)/2,y=(height-H)/2,mx=responsiveMouseX(mouseX,W,H),my=responsiveMouseY(mouseY,W,H);controls.clear();
            KingdomUiAssets.panel(g,x,y,W,H);
            JournalV3Visuals.titlePlate(g,x+85,y+4,W-170);
            JournalV3Visuals.centeredHeading(g,font,view.name().getString(),x+94,y+12,W-188,1.05f,VillageUiTheme.INK);
            JournalV3Visuals.centeredHeading(g,font,view.title().getString(),x+30,y+47,W-60,1f,VillageUiTheme.INK);
            JournalV3Visuals.paper(g,x+24,y+TOP,W-48,BOTTOM-TOP);
            g.enableScissor(x+30,y+TOP+6,x+W-30,y+BOTTOM-6);
            int end;
            try{
                int at=y+TOP+10-scroll;
                at=paragraph(g,view.dialogue(),x+34,at,W-68,.9f,VillageUiTheme.INK)+11;
                at=paragraph(g,view.objective(),x+34,at,W-68,.95f,0xFF245A56)+8;
                at=paragraph(g,view.rules(),x+34,at,W-68,.85f,VillageUiTheme.INK);end=at;
            }finally{g.disableScissor();}
            maxScroll=Math.max(0,end+scroll-(y+BOTTOM-10));scroll=Math.clamp(scroll,0,maxScroll);
            if(maxScroll>0){int track=BOTTOM-TOP-12,thumb=Math.max(16,track*track/(track+maxScroll));int at=y+TOP+6+(track-thumb)*scroll/maxScroll;g.fill(x+W-29,at,x+W-27,at+thumb,0xFF326D66);}
            JournalV3Visuals.paper(g,x+24,y+BOTTOM+7,W-48,57);
            paragraph(g,view.reward(),x+32,y+BOTTOM+12,W-64,.75f,VillageUiTheme.INK);
            g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD),x+34,y+BOTTOM+37);
            g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE),x+200,y+BOTTOM+37);
            VillageUiTheme.drawStringScaled(g,font,Component.translatable("quest.village-quest.encounter.reward_items",view.emeralds()).getString(),x+54,y+BOTTOM+41,VillageUiTheme.INK,.8f);
            VillageUiTheme.drawStringScaled(g,font,Component.translatable("quest.village-quest.encounter.reward_levels",view.levels()).getString(),x+220,y+BOTTOM+41,VillageUiTheme.INK,.8f);
            var actions=List.of(3,4);
            int gap=7,bw=(W-52-gap*(actions.size()-1))/actions.size();
            for(int i=0;i<actions.size();i++){
                int action=actions.get(i);String label=tr(action==4?"decline":"accept");
                int bx=x+26+i*(bw+gap),by=y+H-43;var c=new Control(bx,by,bw,25,()->act(action));controls.add(c);
                VillageUiTheme.drawButton(g,font,bx,by,bw,25,"",!pending,c.contains(mx,my)||focus==i,false);
                float ts=Math.min(.85f,(bw-10f)/Math.max(1,font.width(label)));
                VillageUiTheme.drawStringScaled(g,font,label,bx+(bw-font.width(label)*ts)/2,by+(25-font.lineHeight*ts)/2,VillageUiTheme.LIGHT_TEXT,ts);
            }
        }finally{endResponsivePanel(g,scale);}
    }
    private int paragraph(GuiGraphics g,Component text,int x,int y,int width,float scale,int color){
        var lines=VillageUiTheme.wrapScaledLines(font,text.getString(),width,scale);int step=Math.max(10,Math.round((font.lineHeight+3)*scale));
        for(int i=0;i<lines.size();i++)VillageUiTheme.drawStringScaled(g,font,lines.get(i),x,y+i*step,color,scale);return y+lines.size()*step;
    }
    private void act(int action){if(pending || UiTestTour.active() || !ClientPlayNetworking.canSend(Action.ID))return;pending=true;ClientPlayNetworking.send(new Action(view.session(),action));}
    @Override protected boolean handleMouseClicked(MouseButtonEvent click,boolean doubled){
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT)return super.handleMouseClicked(click,doubled);
        int mx=responsiveMouseX(click.x(),W,H),my=responsiveMouseY(click.y(),W,H);
        if(!pending)for(var c:List.copyOf(controls))if(c.contains(mx,my)){c.action.run();return true;}
        return super.handleMouseClicked(click,doubled);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.clamp(scroll-(int)(vertical*22),0,maxScroll);return true;}
    @Override public boolean keyPressed(KeyEvent key){
        if(key.key()==InputConstants.KEY_TAB && !controls.isEmpty()){focus=(focus+1)%controls.size();return true;}
        if((key.key()==InputConstants.KEY_RETURN || key.key()==InputConstants.KEY_NUMPADENTER) && !pending && focus<controls.size()){controls.get(focus).action.run();return true;}
        if(key.key()==InputConstants.KEY_DOWN || key.key()==InputConstants.KEY_PAGEDOWN){scroll=Math.min(maxScroll,scroll+40);return true;}
        if(key.key()==InputConstants.KEY_UP || key.key()==InputConstants.KEY_PAGEUP){scroll=Math.max(0,scroll-40);return true;}
        if(key.key()==InputConstants.KEY_HOME){scroll=0;return true;}if(key.key()==InputConstants.KEY_END){scroll=maxScroll;return true;}
        return super.keyPressed(key);
    }
    @Override public void onClose(){if(ClientPlayNetworking.canSend(Action.ID))ClientPlayNetworking.send(new Action(view.session(),0));super.onClose();}
}
