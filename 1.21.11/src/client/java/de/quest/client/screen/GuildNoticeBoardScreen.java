package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.client.ui.QuestArtwork;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.economy.CurrencyService;
import de.quest.network.VillageNetworkPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Illustrated village commission board with server-authoritative delivery actions. */
public final class GuildNoticeBoardScreen extends CompatScreen {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_frame.png");
    private static final Identifier INNER = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_inner.png");
    private static final Identifier BOND_COMPLETE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/notice_board/bond_complete.png");
    private static final Identifier BOND_CURRENT = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/notice_board/bond_current.png");
    private static final Identifier BOND_LOCKED = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/notice_board/bond_locked.png");
    private static final int FRAME_TEXTURE_WIDTH = 416;
    private static final int FRAME_TEXTURE_HEIGHT = 234;
    private static final int WIDTH = 560;
    private static final int HEIGHT = 315;
    private static final int INNER_X = 8;
    private static final int INNER_Y = 14;
    private static final int INNER_WIDTH = 400;
    private static final int INNER_HEIGHT = 207;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int META = 0xFF5A3B24;
    private static final int TEAL = VillageUiTheme.TEAL;
    private static final int GOLD = 0xFF704411;
    private static final int LIGHT = 0xFFF1D29A;

    private static final int WALLET_RIGHT_INSET = 26;
    private static final int WALLET_TOP = 7;

    private static final int DELIVER_X = 224;
    private static final int CLOSE_X = 438;
    private static final int BUTTON_Y = 257;
    private static final int DELIVER_WIDTH = 204;
    private static final int CLOSE_WIDTH = 94;
    private static final int BUTTON_HEIGHT = 18;

    private static final int[] BOND_CENTERS = {300, 390, 480};
    private static final int BOND_CENTER_Y = 231;
    private static final int BOND_SEAL_SIZE = 20;
    private static final int[] OFFER_X = {28, 28, 28};
    private static final int[] OFFER_WIDTH = {204, 204, 204};
    private static final int OFFER_Y = 59;
    private static final int OFFER_HEIGHT = 56;
    private static final int OFFER_STEP = 62;

    private VillageNetworkPayloads.NoticeBoardPayload data;
    private final VillageNetworkPayloads.NoticeJourneyPayload journey;
    private int selectedOfferId;
    private int refreshTicks;
    private final de.quest.client.ui.InstructionScroll instructionScroll = new de.quest.client.ui.InstructionScroll();

    public GuildNoticeBoardScreen(VillageNetworkPayloads.NoticeBoardPayload data) {
        this(data, null);
    }

    public GuildNoticeBoardScreen(VillageNetworkPayloads.NoticeBoardPayload data,
                                  VillageNetworkPayloads.NoticeJourneyPayload journey) {
        super(Component.translatable("screen.village-quest.notice_board.title"));
        this.data = data;
        this.journey = journey;
        this.selectedOfferId = data.intro()!=null && data.intro().activeId()>=0 ? data.intro().activeId() : firstOffer().id();
    }

    public void updateData(VillageNetworkPayloads.NoticeBoardPayload data) {
        this.data = data;
        if (data.intro()!=null && data.intro().activeId()>=0) selectedOfferId=data.intro().activeId();
        if (data.offers() == null || data.offers().stream().noneMatch(offer -> offer.id() == selectedOfferId)) {
            selectedOfferId = firstOffer().id();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks < 20 || journey != null && journey.preview()) return;
        refreshTicks = 0;
        UiTestTour.send(new VillageNetworkPayloads.NoticeBoardActionPayload(
                data.worldX(), data.worldY(), data.worldZ(),
                VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_REFRESH, -1));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiX = responsiveMouseX(mouseX, WIDTH, HEIGHT);
        int uiY = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            de.quest.client.ui.JournalV3Visuals.insetBoard(graphics, left, top, WIDTH, HEIGHT);
            VillageUiTheme.drawPaper(graphics, left + 242, top + 59, 290, 138);
            VillageUiTheme.drawPaper(graphics, left + 242, top + 201, 290, 52);
            drawRequest(graphics, left, top, uiX, uiY);
            drawBondPath(graphics, left, top, uiX, uiY);
            drawHeader(graphics, left, top);
            drawFooter(graphics, left, top, uiX, uiY);
            KingdomUiAssets.closeButton(graphics,left,top,WIDTH,HEIGHT,KingdomUiAssets.closeBounds(left,top,WIDTH,HEIGHT).contains(uiX,uiY));
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        VillageUiTheme.drawTitle(graphics, font, title.getString(), "story", left + 150, top + 5, 260);
        VillageUiTheme.drawWalletStrip(graphics,font,left,top,WIDTH,data.balance(),40,10);

        String village = data.villageType().getString() + " · " + ReputationPanel.relationshipText("bond",data.bondLevel()).getString();
        String condition = data.villageCondition().getString() + " · " + data.adventureProfile().getString();
        float villageScale = .72f;
        VillageUiTheme.drawStringScaled(graphics,font,VillageUiTheme.ellipsize(font,village,(int)(204/villageScale)),
                left+28,top+48,BODY,villageScale);
        VillageUiTheme.drawStringScaled(graphics,font,VillageUiTheme.ellipsize(font,condition,(int)(204/villageScale)),
                left+328,top+48,BODY,villageScale);
    }

    private void drawRequest(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<VillageNetworkPayloads.NoticeBoardOfferData> offers = offers();
        for (int i = 0; i < offers.size() && i < 3; i++) {
            VillageNetworkPayloads.NoticeBoardOfferData offer = offers.get(i);
            int x = left + OFFER_X[i];
            int offerY = top + OFFER_Y + i * OFFER_STEP;
            int offerWidth = OFFER_WIDTH[i];
            boolean selected = offer.id() == selectedOfferId;
            boolean hover = within(mouseX, mouseY, x, offerY, offerWidth, OFFER_HEIGHT);
            VillageUiTheme.drawCard(graphics, x, offerY, offerWidth, OFFER_HEIGHT, hover, selected);
            drawScaledItem(graphics, offer.stack(), x + 10, offerY + 11, 1.0f);
            VillageUiTheme.drawWrappedScaled(graphics,font,offer.title().getString(),x+38,offerY+8,offerWidth-46,INK,.85f,2);
            drawCenteredScaled(graphics, Component.translatable(data.intro()!=null && offer.id()==2
                            ? "screen.village-quest.intro.harvest_progress" : "screen.village-quest.ui_review.inventory_need",offer.inventoryAmount(),offer.requiredAmount()).getString(),
                    x + offerWidth/2, offerY + 36, INK, 0.8f, offerWidth - 16);
            if (within(mouseX, mouseY, x, offerY, offerWidth, OFFER_HEIGHT)) {
                int remaining = Math.max(0, offer.requiredAmount() - offer.inventoryAmount());
                graphics.setTooltipForNextFrame(font, List.of(
                        offer.title(),
                        data.intro()!=null ? Component.translatable(introDetailKey(offer.id())) : data.networkConnected()
                                ? Component.translatable("screen.village-quest.notice_board.selection",
                                    offer.title(), remaining, offer.support())
                                : Component.translatable("screen.village-quest.notice_board.local_selection", remaining),
                        data.intro()!=null ? Component.translatable(offer.reward()==0 ? "screen.village-quest.intro.unpaid"
                                : offer.id()==2 ? "screen.village-quest.intro.harvest_reward" : "screen.village-quest.intro.material_reward")
                                : CurrencyService.formatBalance(offer.reward())), mouseX, mouseY);
            }
        }

        VillageNetworkPayloads.NoticeBoardOfferData selected = selectedOffer();
        int required = Math.max(0, selected.requiredAmount());
        int shown = required <= 0 ? 0 : Math.min(Math.max(0, selected.inventoryAmount()), required);
        var artwork = QuestArtwork.forComponent(selected.title()).filter(QuestArtwork.Motif::hasIllustration);
        if (artwork.isPresent()) de.quest.client.ui.JournalV3Visuals.illustration(graphics, artwork.get().largeTexture(),
                left + 254, top + 69, 132, 50);
        else drawScaledItem(graphics, selected.stack(), left + 295, top + 74, 1.5f);
        VillageUiTheme.drawWrappedScaled(graphics,font,selected.title().getString(),left+393,top + 71,130,INK,1f,3);

        String context = data.requestAvailable()
                ? data.networkConnected() ? Component.translatable("screen.village-quest.notice_board.network_need",
                        data.villageNeed(), Math.max(0, Math.min(100, data.villageSupport())), 100).getString()
                        : Component.translatable("screen.village-quest.notice_board.local_context").getString()
                : data.nextRequest().getString().isBlank()
                ? Component.translatable("screen.village-quest.notice_board.available_after_reset").getString()
                : data.nextRequest().getString();
        if (data.intro()!=null) {
            instructionScroll.draw(graphics,font,introDetail().getString(),left+254,top+120,266,4,.65f,META);
        } else if(data.requestAvailable() && data.networkConnected()) {
            VillageUiTheme.drawStringScaled(graphics,font,Component.translatable("screen.village-quest.ui_review.village_need",data.villageNeed()).getString(),left+254,top + 124,META,.78f);
            VillageUiTheme.drawStringScaled(graphics,font,Component.translatable("screen.village-quest.ui_review.village_supply",Math.max(0,Math.min(100,data.villageSupport())),100).getString(),left+254,top + 136,META,.78f);
        } else drawCenteredScaled(graphics, data.requestAvailable()
                ? Component.translatable("screen.village-quest.notice_board.local_context").getString() : context,
                left + 387, top + 130, META, 0.85f, 266);
        if (data.intro()!=null && data.intro().replacement()) return;
        VillageUiTheme.drawStringScaled(graphics,font,Component.translatable("screen.village-quest.questmaster.rewards").getString(),left+254,top + 153,INK,.8f);
        int rewardLabelWidth=Math.round(font.width(Component.translatable("screen.village-quest.questmaster.rewards").getString())*.8f);
        if (data.intro()!=null && selected.reward()<0)
            VillageUiTheme.drawStringScaled(graphics,font,Component.translatable("screen.village-quest.intro.reward_pending").getString(),
                    left+254+rewardLabelWidth+6,top+153,INK,.62f);
        else KingdomUiAssets.moneyCentered(graphics,font,selected.reward(),left+254+rewardLabelWidth+6,top+150,62,false);
        if (data.intro()==null && data.networkConnected()) drawCenteredScaled(graphics,
                Component.translatable("screen.village-quest.notice_board.support_label", selected.support()).getString(),
                left + 464, top + 154, TEAL, 0.8f, 116);

        int progressX = left + 254;
        int progressY = top + 172;
        int progressWidth = 208;
        graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 7, 0xFF5A351E);
        int innerWidth = progressWidth - 4;
        int filled = required <= 0 ? innerWidth : Math.round(innerWidth * (shown / (float) required));
        graphics.fill(progressX + 2, progressY + 2, progressX + 2 + filled, progressY + 5, TEAL);
        VillageUiTheme.drawStringScaled(graphics, font, Component.translatable(data.intro()!=null && selected.id()==2
                        ? "screen.village-quest.intro.harvest_progress" : "screen.village-quest.ui_review.delivery_inventory",selected.inventoryAmount(),required).getString(),
                left + 254, top + 184, INK, 0.72f);
        if (within(mouseX, mouseY, left + 242, top + 59, 290, 138))
            graphics.setTooltipForNextFrame(font, List.of(selected.title(), Component.literal(context),
                    data.intro()!=null ? introRewardDetail() : CurrencyService.formatBalance(selected.reward())), mouseX, mouseY);
    }

    private void drawBondPath(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String bondSummary=ReputationPanel.relationshipSummary(data.bondLevel(),data.socialTrust(),data.relationship(),data.socialCase()).getString();
        String requests=data.nextThreshold()>0 ? Component.translatable("screen.village-quest.notice_board.bond_requests",data.completions(),data.nextThreshold()).getString() : "";
        drawCenteredScaled(graphics,ReputationPanel.relationshipText("title").getString(),left+387,top + 205,INK,.7f,266);
        drawCenteredScaled(graphics,bondSummary,left+387,top + 214,INK,.7f,266);
        drawCenteredScaled(graphics,(data.nextThreshold()>0 ? data.relationship().nextBond() : data.nextPerk()).getString(),left+387,top + 243,BODY,.62f,266);

        int bondTier = Math.max(0, Math.min(data.bondTier(), BOND_CENTERS.length - 1));
        int connectorY = top + BOND_CENTER_Y;
        graphics.fill(left + BOND_CENTERS[0], connectorY - 1,
                left + BOND_CENTERS[BOND_CENTERS.length - 1], connectorY + 1, 0xFF5A351E);
        graphics.fill(left + BOND_CENTERS[0], connectorY - 1,
                left + BOND_CENTERS[BOND_CENTERS.length - 1], connectorY, TEAL);
        for (int i = 0; i < BOND_CENTERS.length; i++) {
            boolean completed = bondTier > i;
            boolean current = bondTier == i;
            int centerX = left + BOND_CENTERS[i];
            int centerY = top + BOND_CENTER_Y;
            Identifier seal = completed ? BOND_COMPLETE : current ? BOND_CURRENT : BOND_LOCKED;
            VillageUiTheme.blitScaled(graphics, seal,
                    centerX - BOND_SEAL_SIZE / 2, centerY - BOND_SEAL_SIZE / 2,
                    BOND_SEAL_SIZE, BOND_SEAL_SIZE, BOND_SEAL_SIZE, BOND_SEAL_SIZE);
        }

        String hint = Component.translatable("screen.village-quest.notice_board.bond_hint").getString();
        int finalThreshold = data.nextThreshold() > 0 ? data.nextThreshold() : 8;
        if (within(mouseX, mouseY, left + 242, top + 201, 290, 52)) {
            graphics.setTooltipForNextFrame(font, ReputationPanel.relationshipDetails(data.bondLevel(),data.socialTrust(),data.socialCase(),data.relationship()), mouseX, mouseY);
        }
    }

    private void drawFooter(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        VillageUiTheme.drawPaper(graphics,left+28,top+BUTTON_Y-4,WIDTH-56,BUTTON_HEIGHT+4);
        if (journey == null) VillageUiTheme.drawButton(graphics, font, left + 28, top + BUTTON_Y, 186, BUTTON_HEIGHT,
                Component.translatable("screen.village-quest.reputation.title").getString(), true,
                within(mouseX, mouseY, left + 28, top + BUTTON_Y, 186, BUTTON_HEIGHT), false, VillageUiTheme.ButtonTone.PAPER);
        if (journey != null) {
            VillageUiTheme.drawButton(graphics, font, left + 28, top + BUTTON_Y, 186, BUTTON_HEIGHT,
                    Component.translatable("screen.village-quest.notice_journey.story_tab").getString(),
                    true, within(mouseX, mouseY, left + 28, top + BUTTON_Y, 186, BUTTON_HEIGHT), false, VillageUiTheme.ButtonTone.PAPER);
        }
        boolean deliverHover = within(mouseX, mouseY,
                left + DELIVER_X, top + BUTTON_Y, DELIVER_WIDTH, BUTTON_HEIGHT);
        boolean closeHover = within(mouseX, mouseY,
                left + CLOSE_X, top + BUTTON_Y, CLOSE_WIDTH, BUTTON_HEIGHT);
        VillageUiTheme.drawButton(graphics, font, left + DELIVER_X, top + BUTTON_Y, DELIVER_WIDTH, BUTTON_HEIGHT,
                Component.translatable(data.intro()!=null ? introActionLabel() : data.requestAvailable()
                        ? "screen.village-quest.notice_board.deliver"
                        : "screen.village-quest.notice_board.return_tomorrow").getString(),
                actionEnabled(), deliverHover, true, VillageUiTheme.ButtonTone.OAK);
        if (data.intro()!=null && data.intro().activeId()>=0 && !data.intro().replacement())
            VillageUiTheme.drawButton(graphics,font,left+224,top+281,204,18,
                    Component.translatable("screen.village-quest.intro.cancel").getString(),true,
                    within(mouseX,mouseY,left+224,top+281,204,18),false,VillageUiTheme.ButtonTone.PAPER);
        VillageUiTheme.drawButton(graphics, font,
                left + CLOSE_X, top + BUTTON_Y, CLOSE_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.village-quest.notice_board.close").getString(),
                true, closeHover, false, VillageUiTheme.ButtonTone.PAPER);
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.handleMouseClicked(click, doubled);
        }
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WIDTH, HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        if(KingdomUiAssets.closeBounds(left,top,WIDTH,HEIGHT).contains(mouseX,mouseY)) {onClose();return true;}
        if (data.intro()!=null && data.intro().activeId()>=0 && !data.intro().replacement()
                && within(mouseX,mouseY,left+224,top+281,204,18)) {
            sendIntro(VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_CANCEL,data.intro().activeId()); return true;
        }
        if (journey == null && within(mouseX, mouseY, left + 28, top + BUTTON_Y, 186, BUTTON_HEIGHT)) {
            UiTestTour.send(new de.quest.network.ReputationPayloads.OpenPayload(0,
                    new net.minecraft.core.BlockPos(data.worldX(), data.worldY(), data.worldZ()), 0)); return true;
        }
        if (journey != null && within(mouseX, mouseY, left + 28, top + BUTTON_Y, 186, BUTTON_HEIGHT)) {
            UiTestTour.navigate(new NoticeJourneyScreen(journey));
            if (!journey.preview()) UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(
                    journey.worldX(), journey.worldY(), journey.worldZ(),
                    VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH));
            return true;
        }
        if (within(mouseX, mouseY,
                left + DELIVER_X, top + BUTTON_Y, DELIVER_WIDTH, BUTTON_HEIGHT)
                && actionEnabled()) {
            if (data.intro()!=null) {
                sendIntro(data.intro().replacement() ? VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_REPLACE
                        : data.intro().activeId()<0 ? VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_ACCEPT
                        : VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_DELIVER,selectedOffer().id());
                return true;
            }
            UiTestTour.send(new VillageNetworkPayloads.NoticeBoardActionPayload(
                    data.worldX(), data.worldY(), data.worldZ(),
                    VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_DELIVER, selectedOffer().id()));
            return true;
        }
        List<VillageNetworkPayloads.NoticeBoardOfferData> offers = offers();
        for (int i = 0; i < offers.size() && i < 3; i++) {
            if (within(mouseX, mouseY, left + OFFER_X[i], top + OFFER_Y + i * OFFER_STEP,
                    OFFER_WIDTH[i], OFFER_HEIGHT)) {
                selectedOfferId = offers.get(i).id();
                return true;
            }
        }
        if (within(mouseX, mouseY,
                left + CLOSE_X, top + BUTTON_Y, CLOSE_WIDTH, BUTTON_HEIGHT)) {
            onClose();
            return true;
        }
        return super.handleMouseClicked(click, doubled);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent key) {
        if (data.intro()!=null && instructionScroll.key(key)) return true;
        return super.keyPressed(key);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        int left=(width-WIDTH)/2,top=(height-HEIGHT)/2;
        if (data.intro()!=null && within(responsiveMouseX(x,WIDTH,HEIGHT),responsiveMouseY(y,WIDTH,HEIGHT),left+254,top+120,266,30)
                && instructionScroll.scroll(vertical)) return true;
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private boolean actionEnabled() {
        return data.intro()==null ? data.requestAvailable() && selectedOffer().canDeliver()
                : data.intro().replacement() || data.intro().activeId()<0
                || data.intro().activeId()==selectedOffer().id() && selectedOffer().canDeliver();
    }
    private String introActionLabel() {
        return data.intro().replacement() ? "screen.village-quest.intro.replace"
                : data.intro().activeId()<0 ? "screen.village-quest.intro.accept" : "screen.village-quest.notice_board.deliver";
    }
    private Component introDetail() {
        if (data.intro().replacement()) return data.intro().detail();
        return Component.translatable(introDetailKey(selectedOffer().id()));
    }
    private static String introDetailKey(int id) {
        return switch (id) {
            case 0 -> "screen.village-quest.intro.detail_0";
            case 1 -> "screen.village-quest.intro.detail_1";
            default -> "screen.village-quest.intro.detail_2";
        };
    }
    private Component introRewardDetail() {
        return Component.translatable(selectedOffer().reward()==0 ? "screen.village-quest.intro.unpaid"
                : selectedOffer().id()==2 ? "screen.village-quest.intro.harvest_reward" : "screen.village-quest.intro.material_reward");
    }
    private void sendIntro(int action,int id) {
        if (journey==null || !journey.preview()) UiTestTour.send(new VillageNetworkPayloads.NoticeBoardActionPayload(
                data.worldX(),data.worldY(),data.worldZ(),action,id,data.intro().token()));
    }

    private void drawCenteredScaled(GuiGraphics graphics, String text,
                                    float centerX, float y, int color, float scale, int width) {
        String visible = VillageUiTheme.ellipsize(font, text, Math.round(width / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                centerX - font.width(visible) * scale / 2.0f, y, color, scale);
    }

    private static void drawScaledItem(GuiGraphics graphics, ItemStack stack,
                                       int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        graphics.renderItem(stack, 0, 0);
        matrices.popMatrix();
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private List<VillageNetworkPayloads.NoticeBoardOfferData> offers() {
        if (data.offers() != null && !data.offers().isEmpty()) return data.offers();
        return List.of(new VillageNetworkPayloads.NoticeBoardOfferData(0, data.requestTitle(), data.requestStack(),
                data.requiredAmount(), data.inventoryAmount(), data.reward(), 0, false, data.canDeliver()));
    }

    private VillageNetworkPayloads.NoticeBoardOfferData firstOffer() {
        return offers().getFirst();
    }

    private VillageNetworkPayloads.NoticeBoardOfferData selectedOffer() {
        return offers().stream().filter(offer -> offer.id() == selectedOfferId).findFirst().orElse(firstOffer());
    }
}
