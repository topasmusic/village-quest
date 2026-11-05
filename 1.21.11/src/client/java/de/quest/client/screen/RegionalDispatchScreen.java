package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.network.VillageNetworkPayloads;
import de.quest.client.compat.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A compact board page for actual two-route regional freight. */
public final class RegionalDispatchScreen extends CompatScreen {
    private static final int WIDTH = NoticeJourneyLayout.WIDTH, HEIGHT = NoticeJourneyLayout.HEIGHT;
    private static final int FOOTER_Y = 255;
    private static final int OFFER_Y = 93, OFFER_STEP = 50, OFFER_BOTTOM = 247;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, CREAM = 0xFFF1D8AA;
    private VillageNetworkPayloads.RegionalDispatchPayload data;
    private int offerScroll;

    public RegionalDispatchScreen(VillageNetworkPayloads.RegionalDispatchPayload data) {
        super(Component.translatable("screen.village-quest.dispatch.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.RegionalDispatchPayload data) {
        this.data = data;
        offerScroll=Math.clamp(offerScroll,0,Math.max(0,data.offers().size()-3));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiX = responsiveMouseX(mouseX, WIDTH, HEIGHT);
        int uiY = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            de.quest.client.ui.JournalV3Visuals.insetBoard(graphics, left, top, WIDTH, HEIGHT);
            VillageUiTheme.drawTitle(graphics, font, title.getString(), "dispatch", left + 150, top + 4, 260);
            boolean freight = !data.offers().isEmpty() || !data.activeCargo().isEmpty()
                    || !data.path().getString().isBlank() || data.canClaim();
            VillageUiTheme.drawCard(graphics,left+28,top + 55,200,data.activeCargo().isEmpty()?164:186,false,!data.activeCargo().isEmpty());
            JournalV3Visuals.logistics(graphics,left+36,top + 77,184,69);
            center(graphics,data.source().getString(),left+128,top + 60,INK,1.0f,180);
            JournalV3Visuals.section(graphics,font,Component.translatable(data.activeCargo().isEmpty()?"screen.village-quest.dispatch.current":"screen.village-quest.ui_review.active_cargo").getString(),left+36,top + 153,184,.9f,"dispatch");
            VillageUiTheme.drawWrappedScaled(graphics,font,data.status().getString(),left+40,top + 178,176,INK,.85f,data.activeCargo().isEmpty()?3:2);
            if(within(uiX,uiY,left+28,top + 55,200,data.activeCargo().isEmpty()?164:186))
                graphics.setTooltipForNextFrame(font,List.of(data.status(),data.path()),uiX,uiY);
            JournalV3Visuals.section(graphics,font,Component.translatable(data.activeCargo().isEmpty()?"screen.village-quest.dispatch.offers":"screen.village-quest.ui_review.other_offers").getString(),left+240,top + 55,292,.95f);
            if(!data.path().getString().isBlank())VillageUiTheme.drawWrappedScaled(graphics,font,data.path().getString(),left+248,top + 77,276,INK,.75f,2);
            if(!freight)VillageUiTheme.drawWrappedScaled(graphics,font,data.status().getString(),left+250,top + 105,270,INK,.9f,10);
            if (!data.activeCargo().isEmpty()) {
                graphics.renderItem(data.activeCargo(), left + 40, top + 211);
                VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font,
                        Component.translatable("screen.village-quest.dispatch.cargo", data.activeAmount(),
                                data.activeCargo().getHoverName()).getString(), 200), left + 62, top + 215, INK, .75f);
            }
            offerScroll=Math.clamp(offerScroll,0,Math.max(0,data.offers().size()-3));
            for (int i = offerScroll; i < data.offers().size(); i++) {
                var offer = data.offers().get(i);
                int rowY = top + OFFER_Y + (i-offerScroll) * OFFER_STEP;
                if (rowY + OFFER_STEP > top + OFFER_BOTTOM) break;
                VillageUiTheme.drawCard(graphics,left+240,rowY,292,46,false,false);
                graphics.renderItem(offer.cargo(), left + 248, rowY + 8);
                String label = Component.translatable("screen.village-quest.dispatch.offer",
                        offer.destination(), offer.amount(), offer.cargo().getHoverName(),
                        offer.reward()).getString();
                JournalV3Visuals.heading(graphics,font,offer.destination().getString(),left+272,rowY+5,110,.85f,INK);
                VillageUiTheme.drawWrappedScaled(graphics,font,Component.translatable("screen.village-quest.dispatch.cargo",offer.amount(),offer.cargo().getHoverName()).getString(),left+272,rowY+17,110,INK,.75f,1);
                VillageUiTheme.drawStringScaled(graphics,font,VillageUiTheme.ellipsize(font,Component.translatable("screen.village-quest.dispatch.carried",offer.inventory(),offer.amount()).getString(),150),left+272,rowY+28,offer.inventory()>=offer.amount()?VillageUiTheme.TEAL:MUTED,.72f);
                VillageUiTheme.drawStringScaled(graphics,font,Component.translatable("screen.village-quest.questmaster.rewards").getString(),left+390,rowY+8,INK,.65f);
                KingdomUiAssets.moneyCentered(graphics,font,offer.reward(),left+390,rowY+22,70,false);
                if (within(uiX, uiY, left + 240, rowY, 292, 46)) {
                    graphics.setTooltipForNextFrame(font, Component.literal(label), uiX, uiY);
                }
                boolean enabled = data.canStart() && offer.inventory() >= offer.amount();
                VillageUiTheme.drawButton(graphics, font, left + 462, rowY+13, 62, 20,
                        Component.translatable("screen.village-quest.dispatch.send").getString(),
                        enabled, within(uiX, uiY, left + 462, rowY+13, 62, 20), true, VillageUiTheme.ButtonTone.OAK);
                if(!enabled && data.canStart()) VillageUiTheme.drawStringScaled(graphics,font,VillageUiTheme.ellipsize(font,Component.translatable("screen.village-quest.dispatch.missing",Math.max(0,offer.amount()-offer.inventory()),offer.cargo().getHoverName()).getString(),270),left+272,rowY+37,MUTED,.65f);
                if(!enabled && within(uiX,uiY,left+462,rowY+13,62,20))graphics.setTooltipForNextFrame(font,
                        !data.canStart()?data.status():Component.translatable("screen.village-quest.dispatch.missing",Math.max(0,offer.amount()-offer.inventory()),offer.cargo().getHoverName()),uiX,uiY);
            }
            VillageUiTheme.drawScrollBar(graphics,left+536,top+OFFER_Y,150,150,data.offers().size()*OFFER_STEP,offerScroll*OFFER_STEP,Math.max(0,data.offers().size()-3)*OFFER_STEP);
            VillageUiTheme.drawPaper(graphics,left+28,top+FOOTER_Y-4,504,24);
            if (data.canClaim()) {
                VillageUiTheme.drawButton(graphics, font, left + 208, top + FOOTER_Y, 190, 18,
                        Component.translatable("screen.village-quest.dispatch.claim").getString(),
                        true, within(uiX, uiY, left + 208, top + FOOTER_Y, 190, 18), true, VillageUiTheme.ButtonTone.PAPER);
            }
            JournalV3Visuals.secondary(graphics,font,Component.translatable("screen.village-quest.dispatch.back").getString(),left+28,top+FOOTER_Y,170,18,within(uiX,uiY,left+28,top+FOOTER_Y,170,18));
            JournalV3Visuals.secondary(graphics,font,Component.translatable("screen.village-quest.notice_board.close").getString(),left+418,top+FOOTER_Y,114,18,within(uiX,uiY,left+418,top+FOOTER_Y,114,18));
            KingdomUiAssets.closeButton(graphics,left,top,WIDTH,HEIGHT,KingdomUiAssets.closeBounds(left,top,WIDTH,HEIGHT).contains(uiX,uiY));
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT);
        int y = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        if (KingdomUiAssets.closeBounds(left,top,WIDTH,HEIGHT).contains(x,y)) { onClose(); return true; }
        if (data.canStart()) {
            for (int i = offerScroll; i < data.offers().size(); i++) {
                var offer = data.offers().get(i);
                int rowY = top + OFFER_Y + (i-offerScroll) * OFFER_STEP;
                if (rowY + OFFER_STEP > top + OFFER_BOTTOM) break;
                if (offer.inventory() >= offer.amount() && within(x, y, left + 462, rowY+13, 62, 20)) {
                    send(VillageNetworkPayloads.RegionalDispatchActionPayload.START, offer.routeIndex());
                    return true;
                }
            }
        }
        if (data.canClaim() && within(x, y, left + 208, top + FOOTER_Y, 190, 18)) {
            send(VillageNetworkPayloads.RegionalDispatchActionPayload.CLAIM, -1);
            return true;
        }
        if (within(x, y, left + 28, top + FOOTER_Y, 170, 18)) {
            UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(
                    data.worldX(), data.worldY(), data.worldZ(),
                    VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH));
            return true;
        }
        if (within(x, y, left + 418, top + FOOTER_Y, 114, 18)) {
            onClose();
            return true;
        }
        return super.handleMouseClicked(click, doubled);
    }

    private void send(int action, int route) {
        UiTestTour.send(new VillageNetworkPayloads.RegionalDispatchActionPayload(
                data.worldX(), data.worldY(), data.worldZ(), action, route));
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        int left=(width-WIDTH)/2,top=(height-HEIGHT)/2;
        if(within(responsiveMouseX(x,WIDTH,HEIGHT),responsiveMouseY(y,WIDTH,HEIGHT),left+240,top+OFFER_Y,300,150)) {
            offerScroll=Math.clamp(offerScroll-(int)Math.signum(vertical),0,Math.max(0,data.offers().size()-3));
            return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }

    private void center(GuiGraphics graphics, String value, float center, float y,
                        int color, float scale, int maxWidth) {
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(maxWidth / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                center - font.width(visible) * scale / 2f, y, color, scale);
    }

    private List<String> wrap(String value, int maxWidth) {
        return VillageUiTheme.wrapScaledLines(font,value,Math.round(maxWidth*.85f),.85f);
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
