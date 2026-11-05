package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.VillageTooltip;
import de.quest.network.VillageNetworkPayloads;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Read-only snapshot of the interacted master and their authoritative route. */
public final class CaravanMasterScreen extends CompatScreen {
    private static final int WIDTH = 560, HEIGHT = 339;
    private static final int FOOTER_Y = 278;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, CREAM = 0xFFF1D8AA;

    private VillageNetworkPayloads.CaravanMasterPayload data;
    private int refreshTicks;
    private final VillageTooltip tooltip = new VillageTooltip();
    private de.quest.reputation.ReputationViewService.View reputation;
    public void updateReputation(de.quest.reputation.ReputationViewService.View view) { reputation = view; }

    public CaravanMasterScreen(VillageNetworkPayloads.CaravanMasterPayload data) {
        super(Component.translatable("screen.village-quest.caravan_master.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.CaravanMasterPayload data) {
        if (this.data.entityId().equals(data.entityId())) this.data = data;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isPreview() && ++refreshTicks >= 100) {
            refreshTicks = 0;
            UiTestTour.send(new VillageNetworkPayloads.CaravanMasterActionPayload(
                    data.entityId(), VillageNetworkPayloads.CaravanMasterActionPayload.REFRESH));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        tooltip.clear();VillageUiTheme.drawScreenShade(g,width,height);
        int mx=responsiveMouseX(mouseX,WIDTH,HEIGHT),my=responsiveMouseY(mouseY,WIDTH,HEIGHT);
        float scale=beginResponsivePanel(g,WIDTH,HEIGHT);
        try {
            int l=(width-WIDTH)/2,t=(height-HEIGHT)/2;
            de.quest.client.ui.JournalV3Visuals.insetBoard(g,l,t,WIDTH,HEIGHT);

            var close=de.quest.client.ui.KingdomUiAssets.closeBounds(l,t,WIDTH,HEIGHT);
            de.quest.client.ui.KingdomUiAssets.closeButton(g,l,t,WIDTH,HEIGHT,close.contains(mx,my));
            inset(g,l+28,t + 48,294,222);
            de.quest.client.ui.JournalV3Visuals.heading(g,font,data.masterName().getString(),l+38,t + 58,274,1.2f,INK);
            compact(g,data.routeName().getString(),l+38,t + 78,INK,274,mx,my,java.util.List.of(data.routeName(),data.quote()));
            de.quest.client.ui.JournalV3Visuals.logistics(g,l+38,t + 96,274,103);
            compact(g,data.journey().getString(),l+38,t + 213,INK,274,mx,my,java.util.List.of(data.journey(),data.availability()));
            int progress=Math.clamp(data.progressPercent(),0,100);
            g.fill(l+38,t + 240,l+312,t + 250,0xFFBB934A);
            g.fill(l+40,t + 242,l+310,t + 248,0xFFE6C791);
            g.fill(l+40,t + 242,l+40+270*progress/100,t + 248,VillageUiTheme.TEAL);
            VillageUiTheme.drawStringScaled(g,font,progress+"%",l+283,t + 256,INK,.8f);
            if(within(mx,my,l+38,t + 236,274,20))tooltip.set(java.util.List.of(Component.translatable("screen.village-quest.caravan_master.journey_progress").append(": "+progress+"%"),data.availability()));
            String arrival=data.etaSeconds()<0?data.availability().getString():String.format(java.util.Locale.ROOT,"%d:%02d",data.etaSeconds()/60,data.etaSeconds()%60);
            infoCard(g,l+330,t + 48,"caravan",labelled("arrival",arrival),data.availability().getString(),mx,my);
            infoCard(g,l+330,t + 105,"compass",labelled("status",data.roadStatus().getString()),labelled("quality",data.roadQuality()+" / 100"),mx,my);
            infoCard(g,l+330,t + 162,"quests",Component.translatable("screen.village-quest.caravan_master.cargo").getString(),data.cargo().getString(),mx,my);
            if(!data.incident().getString().isBlank())infoCard(g,l+330,t + 219,"danger",Component.translatable("screen.village-quest.caravan_master.incident").getString(),data.incident().getString(),mx,my);
            VillageUiTheme.drawPaper(g,l+88,t + 272,444,26);
            if(reputation!=null) {
                Component standing=ReputationPanel.text("master_standing",ReputationPanel.rank(reputation.guildTrust()),reputation.guildTrust(),reputation.buyLimit());
                VillageUiTheme.drawWrappedScaled(g,font,standing.getString(),l+96,t + 279,221,INK,.8f,2);
                if(within(mx,my,l+96,t + 277,221,20))tooltip.set(java.util.List.of(standing));
            }
            if(data.canViewRoute())VillageUiTheme.drawButton(g,font,l+330,t+FOOTER_Y,126,16,Component.translatable("screen.village-quest.caravan_master.view_route").getString(),true,within(mx,my,l+330,t+FOOTER_Y,126,16),true);
            de.quest.client.ui.JournalV3Visuals.secondary(g,font,Component.translatable("screen.village-quest.caravan_master.close").getString(),l+464,t+FOOTER_Y,60,16,within(mx,my,l+464,t+FOOTER_Y,60,16));
            de.quest.client.ui.JournalV3Visuals.title(g,font,title.getString(),l+150,t+3,260,"caravan");
            super.render(g,mx,my,delta);
        }finally{endResponsivePanel(g,scale);}
        tooltip.draw(g,font,mouseX,mouseY);
    }
    private void infoCard(GuiGraphics g,int x,int y,String icon,String label,String value,int mx,int my) {
        inset(g,x,y,202,51);de.quest.client.ui.JournalV3Visuals.sprite(g,icon,x+8,y+10,28,28);
        if(!data.incident().getString().isBlank() && y == (height-HEIGHT)/2+226)
            g.fill(x+2,y+2,x+5,y+49,0xFFAA6633);
        VillageUiTheme.drawWrappedScaled(g,font,label,x+43,y+8,150,INK,.95f,2);
        VillageUiTheme.drawWrappedScaled(g,font,value,x+43,y+29,150,MUTED,.82f,2);
        if(within(mx,my,x,y,202,51))tooltip.set(java.util.List.of(Component.literal(label),Component.literal(value)));
    }
    private void compact(GuiGraphics g,String value,int x,int y,int span,int mx,int my,java.util.List<Component> lines) {
        compact(g,value,x,y,INK,span,mx,my,lines);
    }
    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click,boolean doubled) {
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT)return super.handleMouseClicked(click,doubled);
        int l=(width-WIDTH)/2,t=(height-HEIGHT)/2,x=responsiveMouseX(click.x(),WIDTH,HEIGHT),y=responsiveMouseY(click.y(),WIDTH,HEIGHT);
        if(de.quest.client.ui.KingdomUiAssets.closeBounds(l,t,WIDTH,HEIGHT).contains(x,y)||within(x,y,l+464,t+FOOTER_Y,60,16)){onClose();return true;}
        if(data.canViewRoute()&&within(x,y,l+330,t+FOOTER_Y,126,16)){
            if(!isPreview())UiTestTour.send(new VillageNetworkPayloads.CaravanMasterActionPayload(data.entityId(),VillageNetworkPayloads.CaravanMasterActionPayload.VIEW_ROUTE));
            return true;
        }
        return super.handleMouseClicked(click,doubled);
    }
    private boolean isPreview() {
        return de.quest.network.ReputationPayloads.NONE.equals(data.entityId());
    }

    private static String labelled(String key, String value) {
        return Component.translatable("screen.village-quest.caravan_master." + key).getString() + ": " + value;
    }

    private void compact(GuiGraphics graphics, String value, int x, int y, int color, int width,
                         int mouseX, int mouseY, java.util.List<Component> tooltip) {
        float scale = 0.85f;
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(width / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible, x, y, color, scale);
        if (within(mouseX, mouseY, x, y - 1, width, 12)) {
            this.tooltip.set(tooltip);
        }
    }

    private void center(GuiGraphics graphics, String value, float center, float y,
                        int color, float scale, int maxWidth) {
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(maxWidth / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                center - font.width(visible) * scale / 2f, y, color, scale);
    }

    private static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        VillageUiTheme.drawCard(graphics,x,y,width,height,false,false);
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
