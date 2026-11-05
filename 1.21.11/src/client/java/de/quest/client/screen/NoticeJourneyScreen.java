package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.QuestArtwork;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.network.VillageNetworkPayloads;
import java.util.ArrayList;
import java.util.List;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** The first-village and local-story page of the physical Notice Post. */
public final class NoticeJourneyScreen extends CompatScreen {
    private static final int WIDTH = NoticeJourneyLayout.WIDTH, HEIGHT = NoticeJourneyLayout.HEIGHT;
    private static final int INK = VillageUiTheme.INK, MUTED = VillageUiTheme.BODY, TEAL = VillageUiTheme.TEAL;
    private static final int CREAM = 0xFFF1D8AA;
    private VillageNetworkPayloads.NoticeJourneyPayload data;
    private String trustFeedback = "";
    private int descriptionScroll, descriptionScrollMax;

    public NoticeJourneyScreen(VillageNetworkPayloads.NoticeJourneyPayload data) {
        super(Component.translatable("screen.village-quest.notice_journey.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.NoticeJourneyPayload data) {
        this.data = data;
        trustFeedback = "";
        descriptionScroll = 0;
    }

    public void showTrustFeedback(String reasonKey) {
        trustFeedback = reasonKey;
        descriptionScroll = 0;
    }

    // This page exchanges server packets; singleplayer cooldowns must keep advancing.
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
            VillageUiTheme.drawPaper(graphics, left + 28, top + 45, 504, 136);
            graphics.fill(left+31,top+48,left+529,top+178,0x193E7772);
            VillageUiTheme.drawTitle(graphics, font, title.getString(), "story", left + 150, top + 4, 260);
            var artwork = QuestArtwork.forComponent(data.title()).filter(QuestArtwork.Motif::hasIllustration);
            if (trustFeedback.isBlank() && artwork.isPresent()) JournalV3Visuals.illustration(graphics,
                    artwork.get().largeTexture(), left + 36, top + 77, 220, 83);
            else VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon("story"), left + 119, top + 72, 48);
            JournalV3Visuals.centeredHeading(graphics,font,data.village().getString(),left+36,top + 57,220,1.2f,INK);
            VillageUiTheme.drawWrappedScaled(graphics,font,trustFeedback.isBlank() ? data.title().getString()
                    : Component.translatable("screen.village-quest.reputation.title").getString(),
                    left+272,top + 56,244,INK,1.2f,2);
            List<String> description = wrap(trustFeedback.isBlank() ? (data.stage()==VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE
                    ? Component.translatable("screen.village-quest.notice_journey.choice_prompt").getString() + "\n\n" + activeInstruction():activeInstruction())
                    : Component.translatable(trustFeedback).getString(), 255);
            int visibleLines = 7;
            descriptionScrollMax = Math.max(0, description.size() - visibleLines);
            descriptionScroll = Math.max(0, Math.min(descriptionScroll, descriptionScrollMax));
            int descriptionY = top + 88;
            for (int i = 0; i < Math.min(visibleLines, description.size() - descriptionScroll); i++) {
                VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font, description.get(i + descriptionScroll), 259),
                        left + 272, descriptionY + i * 11, INK, 0.94f);
            }
            VillageUiTheme.drawScrollBar(graphics, left + 522, descriptionY, visibleLines * 11,
                    visibleLines * 11, description.size() * 11, descriptionScroll * 11, descriptionScrollMax * 11);
            if (within(uiX, uiY, left + 28, top + 45, 504, 136)) graphics.setTooltipForNextFrame(font,
                    List.of(data.title(), trustFeedback.isBlank() ? data.detail() : Component.translatable(trustFeedback)), uiX, uiY);
            if (trustFeedback.isBlank()) {
                if(data.stage()==VillageNetworkPayloads.NoticeJourneyPayload.REMEMBERED)
                    JournalV3Visuals.heading(graphics,font,Component.translatable("screen.village-quest.ui_review.completed").getString(),left+40,top + 196,480,1.05f,TEAL);
                else if(data.stage()==VillageNetworkPayloads.NoticeJourneyPayload.AWAY)
                    VillageUiTheme.drawWrappedScaled(graphics,font,Component.translatable("screen.village-quest.ui_review.return",data.village()).getString(),left+40,top + 196,480,INK,.95f,2);
                else drawProgress(graphics, left, top);
            }
            drawButtons(graphics, left, top, uiX, uiY);
            KingdomUiAssets.closeButton(graphics,left,top,WIDTH,HEIGHT,KingdomUiAssets.closeBounds(left,top,WIDTH,HEIGHT).contains(uiX,uiY));
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        if (descriptionScrollMax > 0 && within(responsiveMouseX(x, WIDTH, HEIGHT), responsiveMouseY(y, WIDTH, HEIGHT),
                left + 28, top + 45, 504, 136)) {
            descriptionScroll = Math.max(0, Math.min(descriptionScrollMax, descriptionScroll - (int) Math.signum(vertical)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent key) {
        if (descriptionScrollMax > 0) {
            int change = switch (key.key()) {
                case InputConstants.KEY_DOWN -> 1;
                case InputConstants.KEY_UP -> -1;
                case InputConstants.KEY_PAGEDOWN -> 7;
                case InputConstants.KEY_PAGEUP -> -7;
                case InputConstants.KEY_HOME -> -descriptionScrollMax;
                case InputConstants.KEY_END -> descriptionScrollMax;
                default -> 0;
            };
            if (change != 0) {
                descriptionScroll = Math.max(0, Math.min(descriptionScrollMax, descriptionScroll + change));
                return true;
            }
        }
        return super.keyPressed(key);
    }

    private void drawProgress(GuiGraphics graphics, int left, int top) {
        if (data.firstTarget() > 0 && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.READY
                && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_READY) {
            String label = data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.INTRO
                    ? Component.translatable("screen.village-quest.notice_journey.greetings").getString()
                    : data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RESETTLEMENT
                    || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RECOVERING
                    ? Component.translatable("screen.village-quest.village_life.settlers").getString()
                    : data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_ACTIVE
                    ? Component.translatable("screen.village-quest.afterstory.supplies").getString()
                    : objectiveLabel(true);
            progress(graphics, left + 40, top + 193, label, data.first(), data.firstTarget());
            if (data.secondTarget() > 0) {
                progress(graphics, left + 290, top + 193,
                        Component.translatable(data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RESETTLEMENT
                                || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RECOVERING
                                ? "screen.village-quest.village_life.beds"
                                : objectiveKey(false)).getString(),
                        data.second(), data.secondTarget());
            }
        }
        if ((data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.READY
                || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_READY)
                && !data.delivery().isEmpty()) {
            VillageUiTheme.drawCard(graphics,left+28,top + 189,504,34,false,true);
            graphics.renderItem(data.delivery(), left + 42, top + 197);
            VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font, Component.translatable("screen.village-quest.notice_journey.bundle",
                    data.deliveryCount(), data.delivery().getHoverName()).getString(), 530), left + 68, top + 200, INK, 1f);
        }
    }

    private String activeInstruction() {
        if ((data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.ACTIVE
                || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE
                || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.READY
                || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE)
                && data.title().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title) {
            for (var story : de.quest.guildtown.GuildTownStory.values())
                if (title.getKey().equals("quest.village-quest.guild_town.story." + story.key() + ".title"))
                    return String.join("\n\n", story.instructionLines().stream().map(Component::getString).toList())
                            + "\n\n" + data.detail().getString();
        }
        return data.detail().getString();
    }

    private String objectiveLabel(boolean first) { return Component.translatable(objectiveKey(first)).getString(); }
    private String objectiveKey(boolean first) {
        if(data.title().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title) {
            for(var story:de.quest.guildtown.GuildTownStory.values()) {
                if(title.getKey().equals("quest.village-quest.guild_town.story."+story.key()+".title"))
                    return "screen.village-quest.notice_journey.objective."+story.key()+"."+(first?"first":"second");
            }
        }
        return "screen.village-quest.notice_journey.objective_"+(first?"one":"two");
    }

    private void progress(GuiGraphics graphics, int x, int y, String label, int current, int target) {
        boolean available=data.stage()==VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE;
        boolean complete=!available && current>=target;
        VillageUiTheme.drawCard(graphics,x-12,y-8,242,38,false,complete);
        String count=available?Component.translatable("screen.village-quest.ui_review.goal",target).getString()
                :(complete?"✓ ":"")+current+" / "+target;
        int countWidth=(int)Math.ceil(font.width(count)*.95f);
        VillageUiTheme.drawWrappedScaled(graphics,font,label,x,y-2,208-countWidth,INK,.84f,2);
        VillageUiTheme.drawStringScaled(graphics,font,count,x+216-countWidth,y,complete?TEAL:INK,.95f);
        if(available)return;
        graphics.fill(x, y+20, x+216, y+26, 0xFF6A4B30);
        graphics.fill(x+2, y+22, x+214, y+24, 0xFFD7BC8A);
        graphics.fill(x+2, y+22, x+2+Math.round(212f*Math.min(target,current)/target), y+24, TEAL);
    }

    private void drawButtons(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        if(data.stage()==VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER) {
            VillageUiTheme.drawPaper(graphics,left+28,top + 189,504,34);
            VillageUiTheme.drawPaper(graphics,left+28,top + 253,504,22);
        } else VillageUiTheme.drawPaper(graphics,left+28,top + 228,504,46);
        for (var button : NoticeJourneyLayout.buttons(data.stage(), data.requests() != null)) {
            if(button.action()==VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH
                    && within(mouseX,mouseY,left+button.x(),top+button.y(),button.width(),button.height()))
                graphics.setTooltipForNextFrame(font,Component.translatable("screen.village-quest.notice_journey.refresh_hint"),mouseX,mouseY);
            VillageUiTheme.drawButton(graphics, font, left + button.x(), top + button.y(),
                    button.width(), button.height(), Component.translatable(button.action()==NoticeJourneyLayout.REQUESTS
                            && data.requests()!=null && data.requests().intro()!=null ? "screen.village-quest.intro.tab" : button.key()).getString(),
                    true, button.contains(mouseX - left, mouseY - top), button.primary(),
                    button.primary()?VillageUiTheme.ButtonTone.OAK:VillageUiTheme.ButtonTone.PAPER);
            if (button.contains(mouseX - left, mouseY - top)) {
                graphics.setTooltipForNextFrame(font, Component.translatable(button.key()), mouseX, mouseY);
            }
        }
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT) - left;
        int y = responsiveMouseY(click.y(), WIDTH, HEIGHT) - top;
        if (KingdomUiAssets.closeBounds(0,0,WIDTH,HEIGHT).contains(x,y)) { onClose(); return true; }
        for (var button : NoticeJourneyLayout.buttons(data.stage(), data.requests() != null)) {
            if (!button.contains(x, y)) continue;
            if (button.action() != NoticeJourneyLayout.TRUST) trustFeedback = "";
            switch (button.action()) {
                case NoticeJourneyLayout.CLOSE -> onClose();
                case NoticeJourneyLayout.TRUST -> {
                    if (!data.preview()) UiTestTour.send(new de.quest.network.ReputationPayloads.OpenPayload(0,
                            new net.minecraft.core.BlockPos(data.worldX(), data.worldY(), data.worldZ()), 0));
                }
                case NoticeJourneyLayout.REQUESTS -> UiTestTour.navigate(new GuildNoticeBoardScreen(data.requests(), data));
                case NoticeJourneyLayout.QUESTMASTER -> {
                    if (!data.preview() && minecraft != null && minecraft.player != null) {
                        UiTestTour.command("vq questmaster");
                        onClose();
                    }
                }
                default -> {
                    if (!data.preview()) UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(
                            data.worldX(), data.worldY(), data.worldZ(), button.action()));
                }
            }
            return true;
        }
        return super.handleMouseClicked(click, doubled);
    }

    private void drawCentered(GuiGraphics graphics, String value, float center, float y,
                              int color, float scale, int maxWidth) {
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(maxWidth / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                center - font.width(visible) * scale / 2f, y, color, scale);
    }

    private List<String> wrap(String value, int maxWidth) {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                result.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
