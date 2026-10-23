package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.QuestArtwork;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.VillageTooltip;
import de.quest.network.ReputationPayloads;
import de.quest.network.ReputationPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/** Fixed-card aid desk; server snapshots and session revisions retain all reward authority. */
public final class ReparationScreen extends CompatScreen {
    private static final int WIDTH = 560, HEIGHT = 315, INK = 0xFF2D1B12, TEAL = 0xFF236B68, GOLD = 0xFF9A6620;
    private static final String[] TAB_KEYS = {"title", "reparation", "support"};
    private static final String[] TAB_ICONS = {"trust_handshake", "amends", "resettlement"};
    private record Hit(String id, int x, int y, int width, int height, Runnable action, boolean enabled) {}
    private InteractionPayload data;
    private final Screen parent;
    private int tab, villagePage, supportPage;
    private String focus = "", feedback = "";
    private boolean closeNotified;
    private final VillageTooltip tooltip = new VillageTooltip();
    private final List<Hit> hits = new ArrayList<>();
    public ReparationScreen(InteractionPayload data, Screen parent) {
        super(ReputationPanel.text("title")); this.data = data; this.parent = parent;
        tab = data.view().reparation() == null ? 0 : 1;
    }
    public UUID session() { return data.session(); }
    void selectTourTab(int value) { tab = Math.clamp(value, 0, 2); }
    public void updateData(InteractionPayload next) {
        if (!next.session().equals(data.session()) || next.view().revision() < data.view().revision()) return;
        if (next.view().revision() > data.view().revision()) feedback = "";
        if (!next.feedback().isBlank()) feedback = next.feedback();
        data = next;
    }
    private static Component text(String key, Object... args) { return ReputationPanel.text(key, args); }
    private void send(int action, UUID subject, int material, int page) {
        if (data.preview()) return;
        feedback = "";
        UiTestTour.send(new ActionPayload(data.session(), subject, data.view().revision(), action, material, page));
    }
    @Override public void onClose() {
        if (!closeNotified && !data.preview()) { closeNotified = true; send(ActionPayload.CLOSE, ReputationPayloads.NONE, 0, 0); }
        if (minecraft != null) UiTestTour.navigate(parent);
    }
    public void closeFromServer() { closeNotified = true; onClose(); }
    @Override public void removed() {
        if (!closeNotified && !data.preview()) { closeNotified = true; send(ActionPayload.CLOSE, ReputationPayloads.NONE, 0, 0); }
        super.removed();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        tooltip.clear();
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int mx = responsiveMouseX(mouseX, WIDTH, HEIGHT), my = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2; hits.clear();
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            de.quest.client.ui.JournalV3Visuals.insetBoard(graphics, left, top, WIDTH, HEIGHT);
            graphics.fill(left+20,top + 39,left+124,top+HEIGHT-39,VillageUiTheme.WOOD);
            for (int i = 0; i < TAB_KEYS.length; i++) {
                final int selected = i;
                int x = left + 22, y = top + 59 + i * 62;
                boolean hover = within(mx, my, x, y, 100, 54);
                VillageUiTheme.drawTab(graphics, x, y, 100, 54, tab == i, hover || focus.equals("tab_" + i));
                VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon(TAB_ICONS[i]), x + 35, y + 5, 28);
                var labels=VillageUiTheme.wrapScaled(font,text(TAB_KEYS[i]).getString(),92,.85f);
                for(int row=0;row<Math.min(2,labels.size());row++) {
                    String label=labels.get(row);
                    VillageUiTheme.drawStringScaled(graphics,font,label,x+(100-font.width(label)*.85f)/2,y+37+row*8,VillageUiTheme.LIGHT_TEXT,.85f);
                }
                if (hover) tooltip.set(text(TAB_KEYS[i]));
                hits.add(new Hit("tab_" + i, x, y, 100, 54, () -> {
                    tab = selected; villagePage = supportPage = 0; focus = "tab_" + selected;
                }, true));
            }
            String heading = text(TAB_KEYS[tab]).getString();
            VillageUiTheme.drawTitle(graphics, font, heading, TAB_ICONS[tab], left + 158, top + 5, 244);
            if (within(mx, my, left + 158, top + 5, 244, 26))
                tooltip.set(text(TAB_KEYS[tab]));
            VillageUiTheme.drawPaper(graphics,left+140,top + 253,392,22);
            if (tab == 0) trust(graphics, left, top, mx, my);
            else if (tab == 1) amends(graphics, left, top, mx, my);
            else support(graphics, left, top, mx, my);
            boolean paging = hits.stream().anyMatch(hit -> hit.id().equals("previous"));
            int hintX = left + (paging ? 278 : 150), hintWidth = paging ? 130 : 255;
            float hintScale = paging ? .58f : .68f;
            Component hint = feedback.isBlank() ? text(data.preview() ? "preview" : "personal") : Component.translatable(feedback);
            line(graphics, hint, hintX, top + 261, hintWidth,
                    feedback.isBlank() ? VillageUiTheme.MUTED : GOLD, hintScale, mx, my, List.of(hint));
            button(graphics, "back", left + 417, top + 257, 88, 16, text("back"), true, false, mx, my, this::onClose);
            KingdomUiAssets.closeButton(graphics,left,top,WIDTH,HEIGHT,KingdomUiAssets.closeBounds(left,top,WIDTH,HEIGHT).contains(mx,my));
            var close=KingdomUiAssets.closeBounds(left,top,WIDTH,HEIGHT);
            hits.add(new Hit("close",close.x(),close.y(),close.width(),close.height(),this::onClose,true));
            super.render(graphics, mx, my, delta);
        } finally { endResponsivePanel(graphics, scale); }
        tooltip.draw(graphics, font, mouseX, mouseY);
    }
    private static boolean within(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private void line(GuiGraphics g, Component value, int x, int y, int w, int color, float scale,
                      int mx, int my, List<Component> tooltip) {
        String visible = VillageUiTheme.ellipsize(font, value.getString(), Math.round(w / scale));
        VillageUiTheme.drawStringScaled(g, font, visible, x, y, color, scale);
        if (within(mx, my, x, y - 1, w, 11))
            this.tooltip.set(tooltip.isEmpty() ? List.of(value) : tooltip);
    }
    private void card(GuiGraphics g, int x, int y, int w, int h) {
        card(g, x, y, w, h, false);
    }
    private void card(GuiGraphics g, int x, int y, int w, int h, boolean active) {
        VillageUiTheme.drawCard(g,x,y,w,h,false,active);
    }
    private void paragraph(GuiGraphics g, Component value, int x, int y, int w, int maxLines, int mx, int my) {
        VillageUiTheme.drawWrappedScaled(g, font, value.getString(), x, y, w, INK, .75f, maxLines);
        if (within(mx, my, x, y, w, maxLines * 9)) tooltip.set(value);
    }
    private void pages(GuiGraphics g, int l, int t, int mx, int my, int current, int total, int perPage, boolean offers) {
        int localPages = Math.max(1, (total + perPage - 1) / perPage);
        boolean serverPrev = !data.preview() && data.view().villagePage() > 0;
        boolean serverNext = !data.preview() && (data.view().villagePage() + 1) * 8 < data.view().villageTotal();
        if (localPages == 1 && !serverPrev && !serverNext) return;
        button(g, "previous", l + 140, t + 257, 18, 16, Component.literal("<"), current > 0 || serverPrev,
                false, mx, my, () -> {
                    if (current > 0) { if (offers) supportPage--; else villagePage--; }
                    else { villagePage = 3; supportPage = 0; send(ActionPayload.PAGE, ReputationPayloads.NONE, 0, data.view().villagePage() - 1); }
                });
        button(g, "next", l + 170, t + 257, 18, 16, Component.literal(">"), current + 1 < localPages || serverNext,
                false, mx, my, () -> {
                    if (current + 1 < localPages) { if (offers) supportPage++; else villagePage++; }
                    else { villagePage = supportPage = 0; send(ActionPayload.PAGE, ReputationPayloads.NONE, 0, data.view().villagePage() + 1); }
                });
        // Villages use a global index; support offers exist only on the authoritative village page.
        Component count = offers ? text("desk.page", current + 1, localPages)
                : text("desk.page", data.view().villagePage() * 4 + current + 1, Math.max(1, (data.view().villageTotal() + 1) / 2));
        line(g, count, l + 201, t + 261, 55, GOLD, .6f, mx, my, List.of(count));
    }

    private void button(GuiGraphics graphics, String id, int x, int y, int width, int height, Component label,
                        boolean enabled, boolean selected, int mx, int my, Runnable action) {
        boolean hover = mx >= x && mx < x + width && my >= y && my < y + height;
        String value = VillageUiTheme.ellipsize(font, label.getString(), Math.round((width - 12) / (height <= 18 ? 0.75f : 0.85f)));
        VillageUiTheme.drawButton(graphics, font, x, y, width, height, value, enabled, hover, selected || focus.equals(id));
        if (hover && !value.equals(label.getString())) tooltip.set(label);
        hits.add(new Hit(id, x, y, width, height, action, enabled));
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(event, doubled);
        int x = responsiveMouseX(event.x(), WIDTH, HEIGHT), y = responsiveMouseY(event.y(), WIDTH, HEIGHT);
        for (var hit : hits) if (hit.enabled() && within(x, y, hit.x(), hit.y(), hit.width(), hit.height())) {
            focus = hit.id(); hit.action().run(); return true;
        }
        return super.handleMouseClicked(event, doubled);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (minecraft != null && minecraft.options.keyInventory.matches(event)) { onClose(); return true; }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            String id = event.key() == InputConstants.KEY_PAGEDOWN ? "next" : "previous";
            hits.stream().filter(hit -> hit.enabled() && hit.id().equals(id)).findFirst().ifPresent(hit -> hit.action().run()); return true;
        }
        if (event.key() == InputConstants.KEY_TAB || event.key() == InputConstants.KEY_DOWN || event.key() == InputConstants.KEY_UP) {
            var enabled = hits.stream().filter(Hit::enabled).toList();
            if (!enabled.isEmpty()) {
                int index = -1; for (int i = 0; i < enabled.size(); i++) if (enabled.get(i).id().equals(focus)) index = i;
                int step = event.key() == InputConstants.KEY_UP || event.hasShiftDown() ? -1 : 1;
                focus = enabled.get(Math.floorMod(index + step, enabled.size())).id();
            } return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) {
            hits.stream().filter(hit -> hit.enabled() && hit.id().equals(focus)).findFirst().ifPresent(hit -> hit.action().run()); return true;
        }
        return super.keyPressed(event);
    }
    private void trust(GuiGraphics g, int l, int t, int mx, int my) {
        var v = data.view();
        List<Component> details = List.of(text("desk.guild_hint"), text("next_rank", ReputationPanel.progressTarget(v.guildTrust())));
        card(g, l + 140, t + 45, 392, 62);
        VillageUiTheme.drawIcon(g, VillageUiTheme.icon("trust_handshake"), l + 153, t + 57, 32);
        line(g, text("guild"), l + 200, t + 55, 310, INK, 1.05f, mx, my, details);
        line(g, text("standing", ReputationPanel.rank(v.guildTrust()), v.guildTrust()), l + 200, t + 72,
                310, INK, 1.05f, mx, my, details);
        g.fill(l + 153, t + 92, l + 365, t + 96, 0xFFB79A6A);
        g.fill(l + 153, t + 92, l + 153 + ReputationPanel.progressPixels(v.guildTrust(), 212), t + 96, TEAL);
        line(g, text("desk.next_rank", ReputationPanel.rank(ReputationPanel.progressTarget(v.guildTrust())), ReputationPanel.progressTarget(v.guildTrust())), l + 375, t + 91, 148, INK, .68f, mx, my,
                List.of(text("next_rank", ReputationPanel.progressTarget(v.guildTrust()))));
        service(g, l + 140, t + 117, text("desk.trade"), text("desk.limit", v.buyLimit()), text("trade_limit", v.buyLimit()), mx, my);
        service(g, l + 274, t + 117, text("desk.dispatch"), ReputationPanel.yes(v.dispatchAllowed()),
                text("dispatch", ReputationPanel.yes(v.dispatchAllowed())), mx, my);
        service(g, l + 408, t + 117, text("desk.convoy"), ReputationPanel.yes(v.convoyAllowed()),
                text("convoy", ReputationPanel.yes(v.convoyAllowed())), mx, my);
        card(g, l + 140, t + 191, 392, 21);
        Component status = !v.enabled() ? text("disabled") : !v.writable() ? text("readonly")
                : v.reparation() != null ? text("desk.amends_due") : v.probation() > 0 ? text("desk.probation", v.probation()) : text("desk.earn");
        List<Component> statusHint = !v.enabled() || !v.writable() || v.reparation() != null || v.probation() > 0
                ? List.of(status, text(v.reparation() != null ? "case_locations" : v.probation() > 0 ? "probation" : !v.enabled() ? "disabled" : "readonly", v.probation()))
                : List.of(text("desk.earn_hint"), text("desk.cap_hint"));
        line(g, status, l + 153, t + 200, 372, INK, .72f, mx, my, statusHint);
        int count = v.villages().size();
        villagePage = Math.clamp(villagePage, 0, Math.max(0, (count - 1) / 2));
        if (count == 0) {
            card(g, l + 140, t + 221, 392, 28);
            line(g, text("no_village"), l + 153, t + 230, 372, INK, .75f, mx, my, List.of(text("no_village_body")));
        }
        for (int i = villagePage * 2; i < Math.min(count, villagePage * 2 + 2); i++) {
            var village = v.villages().get(i); int x = l + 140 + (i % 2) * 202;
            int localWidth=count==1?392:190;
            card(g, x, t + 221, localWidth, 28);
            List<Component> info = List.of(ReputationPanel.village(village.key()), text("local_separate"), text(village.affected() ? "local_case" : "local_open"));
            line(g, ReputationPanel.village(village.key()), x + 8, t + 227, localWidth-16, INK, .78f, mx, my, info);
            line(g, text("desk.local_standing", ReputationPanel.rank(village.trust()), village.trust()), x + 8, t + 238, localWidth-16, INK, .82f, mx, my, info);
        }
        pages(g, l, t, mx, my, villagePage, count, 2, false);
    }
    private void service(GuiGraphics g, int x, int y, Component heading, Component value, Component detail, int mx, int my) {
        card(g, x, y, 124, 72);
        if(heading.equals(text("desk.dispatch")))de.quest.client.ui.JournalV3Visuals.logistics(g,x+14,y+4,96,31);
        else de.quest.client.ui.JournalV3Visuals.illustration(g,(heading.equals(text("desk.trade"))?QuestArtwork.Motif.MARKET:QuestArtwork.Motif.CARAVAN).largeTexture(),x+14,y+4,96,31);
        line(g, heading, x + 8, y + 39, 108, INK, .85f, mx, my, List.of(detail));
        line(g, value, x + 8, y + 51, 108, INK, .78f, mx, my, List.of(detail));
        if(heading.equals(text("desk.trade"))) line(g,text("desk.trade_reset"),x+8,y+62,108,VillageUiTheme.MUTED,.62f,mx,my,List.of(detail));
    }
    private void support(GuiGraphics g, int l, int t, int mx, int my) {
        card(g, l + 140, t + 45, 392, 62);
        VillageUiTheme.drawIcon(g, VillageUiTheme.icon("resettlement"), l + 153, t + 68, 25);
        paragraph(g, text("desk.support_steps"), l + 194, t + 64, 326, 4, mx, my);
        if (within(mx, my, l + 140, t + 59, 392, 44)) tooltip.set(text("desk.support_hint"));
        for (int i = 0; i < 2; i++) {
            int x = l + 140 + i * 202; card(g, x, t + 117, 190, 33);
            g.renderItem(new ItemStack(i == 0 ? Items.WHEAT : Items.OAK_PLANKS), x + 9, t + 126);
            line(g, text("desk.supply", i == 0 ? 32 : 16, text("material." + i)), x + 32, t + 130,
                    145, INK, .95f, mx, my, List.of(text("support_bundle")));
        }
        card(g, l + 140, t + 161, 392, 90);
        supportPage = Math.clamp(supportPage, 0, Math.max(0, data.support().size() - 1));
        if (data.support().isEmpty()) paragraph(g, text("support_empty"), l + 153, t + 176, 372, 4, mx, my);
        else {
            var offer = data.support().get(supportPage);
            line(g, ReputationPanel.village(offer.village()), l + 153, t + 172, 372, GOLD, 1f, mx, my, List.of());
            Component status = offer.barred() ? text("desk.support_barred") : offer.supplied() ? text("desk.support_wait") : text(offer.accepted() ? "desk.support_deliver" : "desk.support_promise");
            line(g, status, l + 153, t + 191, 372, INK, .85f, mx, my,
                    List.of(text(offer.barred() ? "support_barred" : offer.supplied() ? "support_wait" : "support_bundle")));
            if (!offer.supplied() && !offer.barred())
                button(g, "supply", l + 232, t + 233, 161, 14, text(offer.accepted() ? "support_submit" : "support_accept"),
                        !data.preview(), true, mx, my, () -> send(offer.accepted() ? ActionPayload.SUPPORT_SUBMIT : ActionPayload.SUPPORT_ACCEPT,
                                offer.id(), 0, data.view().villagePage()));
        }
        pages(g, l, t, mx, my, supportPage, data.support().size(), 1, true);
    }

    private void amends(GuiGraphics g, int l, int t, int mx, int my) {
        var active = data.view().reparation();
        if (active == null) {
            card(g, l + 140, t + 79, 392, 76);
            VillageUiTheme.drawIcon(g, VillageUiTheme.icon("amends"), l + 153, t + 101, 32);
            paragraph(g, text("no_case"), l + 208, t + 104, 310, 3, mx, my); return;
        }
        List<Component> info = List.of(text(active.major() ? "case_major" : "case_minor"), text(active.major() ? "major_materials" : "minor_materials"));
        card(g, l + 140, t + 45, 392, 62);
        line(g, text(active.major() ? "desk.case_major" : "desk.case_minor"), l + 153, t + 56, 372, GOLD, 1f, mx, my, info);
        Component cause = text("cause", text("event.offence." + de.quest.reputation.SocialReputationRules.Offence.values()[active.offence()].name().toLowerCase(Locale.ROOT)));
        line(g, cause, l + 153, t + 73, 372, INK, .8f, mx, my, List.of(cause));
        int seconds = (active.ticksRemaining() + 19) / 20;
        line(g, text("desk.time", seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60)),
                l + 153, t + 92, 372, GOLD, .95f, mx, my, List.of(text("case_seconds", seconds), text("case_reset")));
        boolean supplied = active.aid().stream().anyMatch(aid -> aid.supplied() > 0);
        for (int i = 0; i < 3; i++) {
            final int material = i; int x = l + 140 + i * 132;
            var aid = active.aid().stream().filter(a -> a.material() == material).findFirst().orElse(null);
            card(g, x, t + 120, 124, 96, aid != null);
            g.renderItem(new ItemStack(i == 0 ? Items.WHEAT : i == 1 ? Items.OAK_PLANKS : Items.IRON_INGOT), x + 9, t + 129);
            line(g, text("material." + i), x + 29, t + 135, 87, INK, .85f, mx, my, List.of());
            if (aid != null) {
                Component full = text("aid_carried", text("material." + i), aid.supplied(), aid.required(), aid.carried());
                line(g, text("ui_supplied",aid.supplied(),aid.required()), x + 9, t + 152, 106, INK, .78f, mx, my, List.of(full));
                line(g, text("desk.carried", aid.carried()), x + 9, t + 171, 106, INK, .85f, mx, my, List.of(full));
            } else if (!active.major()) {
                line(g,text("desk.bundle",i==0?32:i==1?16:4),x+9,t + 155,106,INK,.85f,mx,my,info);
                button(g, "choose_" + i, x + 21, t + 192, 80, 14, text("desk.choose"),
                        data.canReparate() && !data.preview() && !supplied && active.selected() != i, false, mx, my,
                        () -> send(ActionPayload.CHOOSE, active.id(), material, data.view().villagePage()));
            }
        }
        if(!active.major()) VillageUiTheme.drawWrappedScaled(g,font,text("desk.one_bundle").getString(),l+153,t + 218,366,INK,.68f,2);
        boolean missing = active.aid().stream().anyMatch(aid -> aid.supplied() < aid.required() && aid.carried() > 0);
        Component label = missing ? text("submit") : text(active.aid().stream().anyMatch(a -> a.supplied() < a.required())
                ? "missing_materials" : "desk.aid_complete");
        button(g, "submit", l + 232, t + 234, 161, 14, label, data.canReparate() && !data.preview() && missing,
                true, mx, my, () -> send(ActionPayload.SUBMIT, active.id(), 0, data.view().villagePage()));
        if (within(mx, my, l + 232, t + 234, 161, 14)) tooltip.set(List.of(label, text(data.canReparate() ? "case_finish" : "case_locations")));
    }

}
