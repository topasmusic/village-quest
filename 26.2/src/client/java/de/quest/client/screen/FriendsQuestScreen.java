package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.*;
import de.quest.network.FriendsQuestPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

/** Shared live guest conversation. Long localized content scrolls inside fixed rails. */
public final class FriendsQuestScreen extends CompatScreen {
    static final int W=580,H=430,TOP=84,FOOTER=94,MARGIN=36;
    private View view;private int scroll,maxScroll,focus;private boolean pending;
    private final List<Control> controls=new ArrayList<>();
    private final List<ItemHit> itemHits=new ArrayList<>();
    record ItemHit(ItemStack stack,int x,int y,int width,int height) {
        boolean contains(int mx,int my){return mx>=x && mx<x+width && my>=y && my<y+height;}
    }
    private record Control(int x,int y,int w,int h,boolean enabled,Runnable action) {
        boolean contains(int mx,int my){return mx>=x && mx<x+w && my>=y && my<y+h;}
    }
    public FriendsQuestScreen(View view){super(view.name());this.view=view;}
    public UUID session(){return view.session();}
    public void update(View next){if(next.revision()<view.revision())return;boolean chapter=next.chapter()!=view.chapter() || !next.title().equals(view.title());view=next;pending=false;if(chapter)scroll=0;}
    @Override public boolean isPauseScreen(){return false;}
    private static String ui(String key,Object... args){return Component.translatable("screen.village-quest.friends."+key,args).getString();}
    int panelHeight(){return H;}
    static int bodyBottom(int height){return height-FOOTER;}
    record FooterBounds(int x,int y,int width,int height) {}
    static List<FooterBounds> footerBounds(int x,int y,int height,int options) {
        int count=options+1,gap=8,available=W-132,bw=Math.min(152,(available-(count-1)*gap)/count);
        int start=x+84+(available-count*bw-(count-1)*gap)/2;
        var result=new ArrayList<FooterBounds>();for(int i=0;i<count;i++)result.add(new FooterBounds(start+i*(bw+gap),y+height-61,bw,24));return result;
    }
    static String actionLabel(Component label){String s=label.getString().strip();return s.startsWith("[") && s.endsWith("]")?s.substring(1,s.length()-1):s;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        int h=panelHeight();float scale=beginResponsivePanel(g,W,h);
        try {
            int x=(width-W)/2,y=(height-h)/2,mx=responsiveMouseX(mouseX,W,h),my=responsiveMouseY(mouseY,W,h);
            controls.clear();itemHits.clear();
            KingdomUiAssets.panel(g,x,y,W,h);JournalV3Visuals.ornaments(g,x,y,W,h);
            int plaque=Math.clamp(font.width(view.name().getString())+55,230,340);
            JournalV3Visuals.titlePlate(g,x+(W-plaque)/2,y+4,plaque);
            JournalV3Visuals.centeredHeading(g,font,view.name().getString(),x+(W-plaque)/2+12,y+11,plaque-24,1.3f,VillageUiTheme.INK);
            JournalV3Visuals.centeredHeading(g,font,view.title().getString(),x+30,y+51,W-60,1.15f,VillageUiTheme.INK);
            if(view.chapter()>0)JournalV3Visuals.centeredHeading(g,font,ui("chapter",view.chapter(),3),x+30,y+69,W-60,.75f,0xFF806747);
            var close=KingdomUiAssets.closeBounds(x,y,W,h);KingdomUiAssets.closeButton(g,x,y,W,h,close.contains(mx,my));
            controls.add(new Control(close.x(),close.y(),close.width(),close.height(),true,this::onClose));
            int bodyX=x+MARGIN,bodyY=y+TOP,bodyW=W-2*MARGIN,bodyH=bodyBottom(h)-TOP;
            scroll=Math.clamp(scroll,0,maxScroll);
            g.enableScissor(bodyX,bodyY,bodyX+bodyW,bodyY+bodyH);
            int end;
            try { end=content(g,bodyX,bodyY-scroll,bodyW); }
            finally {g.disableScissor();}
            maxScroll=Math.max(0,end-(bodyY-scroll)-bodyH+4);scroll=Math.clamp(scroll,0,maxScroll);
            if(maxScroll>0) {
                g.fill(x+W-25,bodyY,x+W-23,bodyY+bodyH,0x557C613D);
                int thumb=Math.max(18,bodyH*bodyH/(bodyH+maxScroll));
                int at=bodyY+(bodyH-thumb)*scroll/maxScroll;g.fill(x+W-25,at,x+W-23,at+thumb,0xFF426D63);
            }
            var footer=footerBounds(x,y,h,view.options().size());var later=footer.getFirst();
            button(g,later.x,later.y,later.width,later.height,ui("later"),true,mx,my,this::onClose);
            int n=view.options().size();
            for(int i=0;i<n;i++) {
                var option=view.options().get(i);var b=footer.get(i+1);
                button(g,b.x,b.y,b.width,b.height,actionLabel(option.label()),option.enabled() && !pending,mx,my,()->act(option.action()));
            }
            String hint=pending?ui("waiting"):maxScroll>0?ui("scroll"):"";
            VillageUiTheme.drawStringScaled(g,font,hint,x+84,y+h-79,0xFF806747,.72f);
        } finally {endResponsivePanel(g,scale);}
        // Native tooltip after both scissor and responsive transform have ended.
        int mx=responsiveMouseX(mouseX,W,H),my=responsiveMouseY(mouseY,W,H),top=(height-H)/2;
        var hovered=hoveredItem(itemHits,mx,my,top+TOP,top+bodyBottom(H));
        if(!hovered.isEmpty())g.unwrap().setTooltipForNextFrame(font,hovered,mouseX,mouseY);
    }
    static ItemStack hoveredItem(List<ItemHit> hits,int mx,int my,int top,int bottom) {
        if(my<top || my>=bottom)return ItemStack.EMPTY;
        for(var hit:hits)if(hit.contains(mx,my))return hit.stack();
        return ItemStack.EMPTY;
    }
    private int content(GuiGraphics g,int x,int y,int w) {
        int start=y;Identifier art=view.title().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("quest.village-quest.friends.simon.herbs.title")?artwork("simon",1):artwork(view.who(),view.chapter());
        if(g!=null)JournalV3Visuals.illustration(g,art,x,y,224,84);
        int story=paragraph(g,view.dialogue(),x+238,y+2,w-238,.98f,VillageUiTheme.INK);
        y+=Math.max(91,story+9);
        if(!view.objectives().isEmpty()) {
            section(g,ui("objectives"),x,y,w);y+=21;
            for(var line:view.objectives()){y+=paragraph(g,line,x+4,y,w-8,.92f,VillageUiTheme.INK)+4;}
            if(!view.progress().isEmpty()) {
                int cell=(w-12)/view.progress().size();
                int rowH=40;
                for(var p:view.progress())rowH=Math.max(rowH,19+VillageUiTheme.wrapScaledLines(font,p.stack().getHoverName().getString(),cell-40,.9f).size()*10);
                for(int i=0;i<view.progress().size();i++) {
                    var p=view.progress().get(i);int left=x+i*(cell+6);
                    if(g!=null){JournalV3Visuals.paper(g,left,y,cell,rowH);drawItem(g,p.stack(),left+6,y+8,23);itemHits.add(new ItemHit(p.stack(),left,y,cell,rowH));}
                    int h=paragraph(g,p.stack().getHoverName(),left+34,y+6,cell-40,.9f,VillageUiTheme.INK);
                    if(g!=null)VillageUiTheme.drawStringScaled(g,font,p.carried()+" / "+p.required(),left+34,y+7+h,p.carried()>=p.required()?0xFF306C42:VillageUiTheme.INK,.9f);
                }
                y+=rowH+7;
            }
            section(g,ui("reward"),x,y,w);y+=21;
            var rows=rewardRows(view);int cell=(w-6*(rows.size()-1))/Math.max(1,rows.size()),rowH=38;
            for(var row:rows)rowH=Math.max(rowH,12+paragraph(null,Component.literal(row.text),0,0,cell-39,.9f,VillageUiTheme.INK));
            for(int i=0;i<rows.size();i++) {
                var row=rows.get(i);int left=x+i*(cell+6);
                if(g!=null){
                    JournalV3Visuals.paper(g,left,y,cell,rowH);
                    if(row.icon.isEmpty() && row.icons.isEmpty()) {g.fill(left+6,y+rowH/2-3,left+27,y+rowH/2+3,0xFF305530);g.fill(left+7,y+rowH/2-2,left+24,y+rowH/2+2,0xFF8BD047);}
                    else if(!row.icon.isEmpty())drawItem(g,row.icon,left+5,y+(rowH-23)/2,23);
                    if(row.icons.size()<=1 && !row.icon.isEmpty())itemHits.add(new ItemHit(row.icon,left,y,cell,rowH));
                }
                paragraph(g,Component.literal(row.text),left+34,y+6,cell-39,.9f,VillageUiTheme.INK);
            }
            if(view.rewards().choice() && !rows.isEmpty()) {
                var choices=rows.getFirst().icons;int choiceY=y+rowH+4;
                for(int i=0;i<choices.size();i++) {
                    int left=x+i*(w/choices.size());var stack=choices.get(i);
                    if(g!=null){drawItem(g,stack,left+5,choiceY,23);itemHits.add(new ItemHit(stack,left,choiceY,w/choices.size()-6,30));}
                    paragraph(g,stack.getHoverName(),left+34,choiceY+4,w/choices.size()-40,.9f,VillageUiTheme.INK);
                }
                rowH+=38;
            }
            y+=rowH+7;
        }
        for(var line:view.notices()){y+=paragraph(g,line,x+4,y,w-8,.9f,0xFF586751)+5;}
        return Math.max(y,start+91);
    }
    private void section(GuiGraphics g,String title,int x,int y,int w){if(g==null)return;JournalV3Visuals.heading(g,font,title,x+4,y+2,w,.98f,VillageUiTheme.INK);g.fill(x,y+15,x+w,y+16,0xFFBA9354);}
    private static void drawItem(GuiGraphics g,ItemStack item,int x,int y,int size){var pose=g.pose();pose.pushMatrix();pose.translate(x,y);pose.scale(size/16f,size/16f);g.renderItem(item,0,0);pose.popMatrix();}
    record RewardRow(ItemStack icon,String text,List<ItemStack> icons) {}
    static List<RewardRow> rewardRows(View view) {
        var reward=view.rewards();var rows=new ArrayList<RewardRow>();
        for(var entry:RewardPreview.rows(new de.quest.network.RewardPayloads.Choice("friends",List.of(),reward.currency(),List.of()))) {
            var icon=new ItemStack(entry.currency().equals("crown")?de.quest.registry.ModItems.CROWN:de.quest.registry.ModItems.SILVERMARK,(int)entry.count());
            String name=Component.translatable("text.village-quest.currency.unit."+entry.currency()+(entry.count()==1?".one":".other")).getString();
            rows.add(new RewardRow(icon,entry.count()+" × "+name,List.of(icon)));
        }
        var client=net.minecraft.client.Minecraft.getInstance();
        String language=client==null || client.getLanguageManager()==null?"en_us":client.getLanguageManager().getSelected();
        if(reward.experience()>0)rows.add(new RewardRow(ItemStack.EMPTY,ui("experience",experienceNumber(reward.experience(),language)),List.of()));
        if(reward.choice())rows.add(new RewardRow(ItemStack.EMPTY,view.reward().getString(),reward.items()));
        else for(var entry:RewardPreview.rows(new de.quest.network.RewardPayloads.Choice("friends",List.of(),0,reward.items()))) {
            var stack=entry.stack();rows.add(new RewardRow(stack,entry.count()+" × "+stack.getHoverName().getString(),List.of(stack)));
        }
        return rows;
    }
    static String experienceNumber(double bars,String language) {
        return java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag((language==null?"en_us":language).replace('_','-'))).format(bars);
    }
    private int paragraph(GuiGraphics g,Component text,int x,int y,int width,float scale,int color) {
        var lines=VillageUiTheme.wrapScaledLines(font,text.getString(),width,scale);
        int step=Math.max(9,Math.round((font.lineHeight+3)*scale));
        if(g!=null)for(int i=0;i<lines.size();i++)VillageUiTheme.drawStringScaled(g,font,lines.get(i),x,y+i*step,color,scale);
        return lines.size()*step;
    }
    static Identifier artwork(String who,int chapter) {
        String motif=switch(who) {
            case "simon"->chapter==1?"friends_herbs":chapter==2?"caravan_road":"village_home";
            case "lutte"->chapter==3?"forge":chapter==2?"friends_coastal_signals":"caravan_road";
            case "marius"->chapter==2?"friends_golem_repair":"night_watch";
            case "ravel"->"raphael_harp";
            default->"village_home";
        };
        return Identifier.fromNamespaceAndPath("village-quest","textures/gui/artwork/"+motif+(motif.startsWith("friends_") || motif.equals("raphael_harp")?"":"_192x72")+".png");
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String label,boolean enabled,int mx,int my,Runnable action) {
        var c=new Control(x,y,w,h,enabled,action);controls.add(c);
        VillageUiTheme.drawButton(g,font,x,y,w,h,"",enabled,c.contains(mx,my) || focus==controls.size()-1,false);
        float textScale=Math.min(.92f,(w-12f)/Math.max(1,font.width(label)));
        VillageUiTheme.drawStringScaled(g,font,label,x+(w-font.width(label)*textScale)/2,y+(h-font.lineHeight*textScale)/2,enabled?VillageUiTheme.LIGHT_TEXT:0xFFC0AD8A,textScale);
        if(c.contains(mx,my) && font.width(label)*.8f>w-12)g.setTooltipForNextFrame(font,List.of(Component.literal(label)),mx,my);
    }
    private void act(String action) {
        if(pending || UiTestTour.active())return;
        if(ClientPlayNetworking.canSend(Action.ID)){pending=true;ClientPlayNetworking.send(new Action(view.session(),view.revision(),action));}
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click,boolean doubled) {
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT)return super.handleMouseClicked(click,doubled);
        int h=panelHeight(),mx=responsiveMouseX(click.x(),W,h),my=responsiveMouseY(click.y(),W,h);
        for(var c:List.copyOf(controls))if(c.enabled && c.contains(mx,my)){c.action.run();return true;}
        return super.handleMouseClicked(click,doubled);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        int h=panelHeight(),mx=responsiveMouseX(x,W,h),my=responsiveMouseY(y,W,h),left=(width-W)/2,top=(height-h)/2;
        if(mx>=left+MARGIN && mx<left+W-MARGIN && my>=top+TOP && my<top+bodyBottom(h)){scroll=Math.clamp(scroll-(int)(vertical*24),0,maxScroll);return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent key) {
        if(key.key()==InputConstants.KEY_TAB && !controls.isEmpty()){focus=(focus+1)%controls.size();return true;}
        if((key.key()==InputConstants.KEY_RETURN || key.key()==InputConstants.KEY_NUMPADENTER) && focus<controls.size()){var c=controls.get(focus);if(c.enabled)c.action.run();return true;}
        if(key.key()==InputConstants.KEY_DOWN || key.key()==InputConstants.KEY_PAGEDOWN){scroll=Math.min(maxScroll,scroll+(key.key()==InputConstants.KEY_DOWN?20:160));return true;}
        if(key.key()==InputConstants.KEY_UP || key.key()==InputConstants.KEY_PAGEUP){scroll=Math.max(0,scroll-(key.key()==InputConstants.KEY_UP?20:160));return true;}
        if(key.key()==InputConstants.KEY_HOME){scroll=0;return true;}if(key.key()==InputConstants.KEY_END){scroll=maxScroll;return true;}
        return super.keyPressed(key);
    }
    @Override public void onClose(){
        if(!UiTestTour.active() && ClientPlayNetworking.canSend(Action.ID))ClientPlayNetworking.send(new Action(view.session(),view.revision(),"close"));
        super.onClose();
    }
}
