package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.KingdomUiHints;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageCheckPayload;
import java.util.Locale;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Read-only local diagnosis. The server remains the sole source of evidence. */
public final class VillageCheckScreen extends CompatScreen {
    private final KingdomUiHints hints=new KingdomUiHints();
    private static final int W=416,H=330,INK=0xFF3E2918,MUTED=0xFF80694F;
    private static final String TEXT="screen.village-quest.building.";
    private VillageCheckPayload data;
    private int selected, listScroll;
    private static final int VISIBLE_MARKERS=8;
    public VillageCheckScreen(VillageCheckPayload data) {
        super(Component.translatable(TEXT+"diagnosis")); this.data=data;
    }
    public void updateData(VillageCheckPayload view) {
        if(!view.initial() && (!view.session().equals(data.session()) || !view.dimension().equals(data.dimension()))) return;
        var at=data.markers().isEmpty()?null:data.markers().get(selected).position();
        data=view; selected=0;
        for(int i=0;i<data.markers().size();i++) if(data.markers().get(i).position().equals(at)) selected=i;
        listScroll=Math.clamp(listScroll,0,Math.max(0,data.markers().size()-VISIBLE_MARKERS));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        VillageUiTheme.drawScreenShade(g,width,height); hints.clear();
        int mx=responsiveMouseX(mouseX,W,H),my=responsiveMouseY(mouseY,W,H);
        float scale=beginResponsivePanel(g,W,H);
        try {
            int l=(width-W)/2,t=(height-H)/2;
            de.quest.client.ui.JournalV3Visuals.insetBoard(g,l,t,W,H);
            KingdomUiAssets.managementHeader(g,font,title.getString(),KingdomUiAssets.DetailIcon.FOUNDING,l,t,W);
            KingdomUiAssets.closeButton(g,l,t,W,H,KingdomUiAssets.closeBounds(l,t,W,H).contains(mx,my));
            long valid=data.markers().stream().filter(m -> m.status()==de.quest.kingdom.BuildingScanner.Status.VALID).count();
            long missing=data.markers().stream().filter(m -> m.status()==de.quest.kingdom.BuildingScanner.Status.INVALID).count();
            line(g,Component.translatable(TEXT+"diagnosis_summary",data.markers().size(),valid,missing,data.markers().size()-valid-missing).getString(),l+34,t+61,348,INK);
            KingdomUiAssets.card(g,l+28,t+82,116,180,false); KingdomUiAssets.card(g,l+152,t+82,236,180,false);
            for(int i=listScroll;i<Math.min(data.markers().size(),listScroll+VISIBLE_MARKERS);i++) {
                var m=data.markers().get(i); int y=t+88+(i-listScroll)*22;
                KingdomUiAssets.icon(g,KingdomUiAssets.role(m.role()),l+30,y,18);
                String label=Component.translatable(TEXT+"role."+m.role().name().toLowerCase(Locale.ROOT)).getString();
                VillageUiTheme.drawButton(g,font,l+52,y,80,20,label,true,within(mx,my,l+28,y,112,20),selected==i);
                if(within(mx,my,l+28,y,112,20)) hints.setTooltipForNextFrame(font,java.util.List.of(
                        Component.literal(m.village()),Component.literal(label),Component.literal(m.position().toShortString())),mx,my);
            }
            VillageUiTheme.drawScrollBar(g,l+135,t+88,174,174,data.markers().size()*22,listScroll*22,
                    Math.max(0,(data.markers().size()-VISIBLE_MARKERS)*22));
            if(!data.markers().isEmpty()) {
                var m=data.markers().get(selected);
                line(g,m.village(),l+158,t+91,224,INK);
                line(g,m.position().getX()+", "+m.position().getY()+", "+m.position().getZ(),l+158,t+104,224,MUTED);
                line(g,Component.translatable(TEXT+"status."+m.status().name().toLowerCase(Locale.ROOT)).getString(),l+158,t+117,224,INK);
                for(int i=0;i<m.rows().size();i++) {
                    var row=m.rows().get(i); int x=l+157+(i%2)*115,y=t+131+(i/2)*22;
                    String key=row.check().name().toLowerCase(Locale.ROOT);
                    KingdomUiAssets.card(g,x,y,110,20,row.passed());
                    KingdomUiAssets.detailIcon(g,KingdomUiAssets.checkIcon(row.check()),x+2,y+2,16);
                    VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,Component.translatable(TEXT+"check_short."+key).getString(),146),x+21,y+3,INK,.6f);
                    VillageUiTheme.drawStringScaled(g,font,(row.passed()?"+ ":"! ")+row.found()+" / "+row.required(),x+21,y+12,row.passed()?0xFF306C42:0xFF9A3D32,.6f);
                    if(within(mx,my,x,y,110,20)) hints.setTooltipForNextFrame(font,java.util.List.of(
                            Component.literal(BuildingScreen.checkHint(m.role(),row.check())),Component.translatable(TEXT+"check_count",row.found(),row.required(),Math.max(0,row.required()-row.found()))),mx,my);
                }
                if(m.rows().isEmpty()) VillageUiTheme.drawWrappedScaled(g,font,Component.translatable(TEXT+"checking").getString(),l+158,t+140,224,MUTED,.8f,4);
                if(m.conflict() && within(mx,my,l+154,t+114,232,17)) hints.setTooltipForNextFrame(font,Component.translatable(TEXT+"station_conflict"),mx,my);
            }
            VillageUiTheme.drawPaper(g,l+28,t+270,360,18);
            VillageUiTheme.drawWrappedScaled(g,font,Component.translatable(TEXT+"diagnosis_hint").getString(),l+32,t+274,352,MUTED,.65f,2);
            hints.draw(g,font,l,t,W,H);
        } finally { endResponsivePanel(g,scale); }
    }
    private void line(GuiGraphics g,String value,int x,int y,int w,int color) {
        VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,value,(int)(w/.75f)),x,y,color,.75f);
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click,boolean doubled) {
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT) return false;
        int l=(width-W)/2,t=(height-H)/2,mx=responsiveMouseX(click.x(),W,H),my=responsiveMouseY(click.y(),W,H);
        if(KingdomUiAssets.closeBounds(l,t,W,H).contains(mx,my)) { onClose(); return true; }
        for(int i=listScroll;i<Math.min(data.markers().size(),listScroll+VISIBLE_MARKERS);i++)
            if(within(mx,my,l+28,t+88+(i-listScroll)*22,112,20)) { selected=i; return true; }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        int l=(width-W)/2,t=(height-H)/2,mx=responsiveMouseX(x,W,H),my=responsiveMouseY(y,W,H);
        if(within(mx,my,l+28,t+82,116,180)) {
            listScroll=Math.clamp(listScroll-(int)Math.signum(vertical),0,Math.max(0,data.markers().size()-VISIBLE_MARKERS));
            return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private static boolean within(int x,int y,int l,int t,int w,int h) { return x>=l && x<l+w && y>=t && y<t+h; }
}
