package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.ReputationPayloads;
import de.quest.network.ReputationPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Physical aid desk, using the existing 416×234 journal frame. No client-side reward authority. */
public final class ReparationScreen extends CompatScreen {
    private static final int WIDTH = 416, HEIGHT = 234, INK = 0xFF2D1B12, TEAL = 0xFF236B68, GOLD = 0xFF9A6620;
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("village-quest", "textures/gui/journal_board.png");
    private record Row(String id, Component text, int action, UUID subject, int material, boolean enabled) {}
    private record Hit(String id, int x, int y, int width, int height, Runnable action, boolean enabled) {}
    private InteractionPayload data;
    private final Screen parent;
    private int tab, scroll, scrollMax;
    private String focus = "", feedback = "";
    private boolean closeNotified;
    private final List<Hit> hits = new ArrayList<>();
    public ReparationScreen(InteractionPayload data, Screen parent) {
        super(ReputationPanel.text("title")); this.data = data; this.parent = parent;
        tab = data.view().reparation() == null ? 0 : 1;
    }
    public UUID session() { return data.session(); }
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
        ClientPlayNetworking.send(new ActionPayload(data.session(), subject, data.view().revision(), action, material, page));
    }
    @Override public void onClose() {
        if (!closeNotified && !data.preview()) { closeNotified = true; send(ActionPayload.CLOSE, ReputationPayloads.NONE, 0, 0); }
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
    public void closeFromServer() { closeNotified = true; onClose(); }
    @Override public void removed() {
        if (!closeNotified && !data.preview()) { closeNotified = true; send(ActionPayload.CLOSE, ReputationPayloads.NONE, 0, 0); }
        super.removed();
    }
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (tab == 0) {
            for (var card : ReputationPanel.trustCards(data.view())) {
                rows.add(new Row(card.id(), card.title().copy().append(" — ").append(card.subtitle()), -1, ReputationPayloads.NONE, 0, false));
                int i = 0;
                for (var detail : card.details()) rows.add(new Row(card.id() + "_" + i++, detail, -1, ReputationPayloads.NONE, 0, false));
            }
        } else if (tab == 1) {
            var active = data.view().reparation();
            if (active == null) rows.add(new Row("no_case", text("no_case"), -1, ReputationPayloads.NONE, 0, false));
            else {
                rows.add(new Row("cause", text(active.major() ? "case_major" : "case_minor"), -1, active.id(), 0, false));
                rows.add(new Row("offence", text("cause", text("event.offence." + de.quest.reputation.SocialReputationRules.Offence.values()[active.offence()].name().toLowerCase(Locale.ROOT))), -1, active.id(), 0, false));
                rows.add(new Row("lore", text("case_lore"), -1, active.id(), 0, false));
                rows.add(new Row("time", text("case_seconds", (active.ticksRemaining() + 19) / 20), -1, active.id(), 0, false));
                rows.add(new Row("rule", text(active.major() ? "major_materials" : "minor_materials"), -1, active.id(), 0, false));
                boolean supplied = active.aid().stream().anyMatch(aid -> aid.supplied() > 0);
                if (!active.major()) for (int i = 0; i < 3; i++) rows.add(new Row("choose_" + i,
                        text("choose_material", text("material." + i)), ActionPayload.CHOOSE, active.id(), i,
                        data.canReparate() && !supplied && active.selected() != i));
                for (var aid : active.aid()) rows.add(new Row("aid_" + aid.material(), text("aid_carried", text("material." + aid.material()), aid.supplied(), aid.required(), aid.carried()), -1, active.id(), aid.material(), false));
                boolean missing = active.aid().stream().anyMatch(aid -> aid.supplied() < aid.required() && aid.carried() > 0);
                if (!missing && active.aid().stream().anyMatch(aid -> aid.supplied() < aid.required()))
                    rows.add(new Row("missing", text("missing_materials"), -1, active.id(), 0, false));
                rows.add(new Row("submit", text("submit"), ActionPayload.SUBMIT, active.id(), 0, data.canReparate() && missing));
                rows.add(new Row("place", text(data.canReparate() ? "case_finish" : "case_locations"), -1, active.id(), 0, false));
                rows.add(new Row("reset", text("case_reset"), -1, active.id(), 0, false));
            }
        } else {
            rows.add(new Row("support_rule", text("support_rule"), -1, ReputationPayloads.NONE, 0, false));
            if (data.support().isEmpty()) rows.add(new Row("support_empty", text("support_empty"), -1, ReputationPayloads.NONE, 0, false));
            for (var offer : data.support()) {
                rows.add(new Row("support_" + offer.id(), ReputationPanel.village(offer.village()), -1, offer.id(), 0, false));
                rows.add(new Row("status_" + offer.id(), text(offer.barred() ? "support_barred" : offer.supplied() ? "support_wait" : "support_bundle"), -1, offer.id(), 0, false));
                if (!offer.supplied() && !offer.barred()) rows.add(new Row("action_" + offer.id(), text(offer.accepted() ? "support_submit" : "support_accept"),
                        offer.accepted() ? ActionPayload.SUPPORT_SUBMIT : ActionPayload.SUPPORT_ACCEPT, offer.id(), 0, !data.preview()));
            }
        }
        return List.copyOf(rows);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int mx = responsiveMouseX(mouseX, WIDTH, HEIGHT), my = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2; hits.clear();
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            VillageUiTheme.blitScaled(graphics, FRAME, left, top, WIDTH, HEIGHT, WIDTH, HEIGHT);
            graphics.drawString(font, title, left + 86, top + 16, INK, false);
            String[] tabs = {"title", "reparation", "support"};
            for (int i = 0; i < 3; i++) {
                final int selected = i;
                button(graphics, "tab_" + i, left + 81 + i * 102, top + 39, 98, 17, text(tabs[i]), true, tab == i, mx, my,
                        () -> { tab = selected; scroll = 0; focus = "tab_" + selected; });
            }
            if (data.view().villageTotal() > 8 && tab != 1) {
                int page = data.view().villagePage();
                button(graphics, "previous", left + 309, top + 14, 28, 18, Component.literal("<"), !data.preview() && page > 0, false, mx, my,
                        () -> send(ActionPayload.PAGE, ReputationPayloads.NONE, 0, page - 1));
                button(graphics, "next", left + 342, top + 14, 28, 18, Component.literal(">"), !data.preview() && (page + 1) * 8 < data.view().villageTotal(), false, mx, my,
                        () -> send(ActionPayload.PAGE, ReputationPayloads.NONE, 0, page + 1));
            }
            int y = top + 64 - scroll, total = 0;
            graphics.enableScissor(left + 81, top + 61, left + 389, top + 192);
            for (var row : rows()) {
                int height;
                if (row.action() >= 0) {
                    height = 25;
                    if (y + height > top + 61 && y < top + 192) button(graphics, row.id(), left + 89, y, 282, 20, row.text(), row.enabled() && !data.preview(), false, mx, my,
                            () -> send(row.action(), row.subject(), row.material(), data.view().villagePage()));
                } else {
                    var lines = font.split(row.text(), 365); height = lines.size() * 9 + 7;
                    graphics.pose().pushMatrix(); graphics.pose().translate(left + 91, y + 2); graphics.pose().scale(0.78f, 0.78f);
                    for (int i = 0; i < lines.size(); i++) {
                        StringBuilder line = new StringBuilder();
                        lines.get(i).accept((index, style, codepoint) -> { line.appendCodePoint(codepoint); return true; });
                        graphics.drawString(font, line.toString(), 0, i * 11, INK, false);
                    }
                    graphics.pose().popMatrix();
                    if (row.id().equals("social_guild")) {
                        height += 18;
                        int barY = y + height - 17;
                        graphics.fill(left + 92, barY, left + 370, barY + 4, 0xFF9B8060);
                        int trust = data.view().guildTrust();
                        graphics.fill(left + 92, barY, left + 92 + ReputationPanel.progressPixels(trust, 278), barY + 4, TEAL);
                        VillageUiTheme.drawStringScaled(graphics, font, Integer.toString(ReputationPanel.progressFloor(trust)), left + 92, barY + 6, INK, 0.6f);
                        String target = Integer.toString(ReputationPanel.progressTarget(trust));
                        VillageUiTheme.drawStringScaled(graphics, font, target, left + 370 - Math.round(font.width(target) * 0.6f), barY + 6, INK, 0.6f);
                    }
                }
                y += height; total += height;
            }
            graphics.disableScissor(); scrollMax = Math.max(0, total - 131); scroll = Math.clamp(scroll, 0, scrollMax);
            VillageUiTheme.drawScrollBar(graphics, left + 380, top + 62, 129, 131, total, scroll, scrollMax);
            if (!feedback.isBlank()) {
                String value = Component.translatable(feedback).getString();
                VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font, value, 440), left + 87, top + 195, GOLD, 0.68f);
                if (mx >= left + 85 && my >= top + 192 && my < top + 207) graphics.setTooltipForNextFrame(font, Component.translatable(feedback), mx, my);
            }
            button(graphics, "back", left + 300, top + 210, 88, 17, text("back"), true, false, mx, my, this::onClose);
            VillageUiTheme.drawStringScaled(graphics, font, text(data.preview() ? "preview" : "personal").getString(), left + 85, top + 214, TEAL, 0.68f);
            super.render(graphics, mx, my, delta);
        } finally { endResponsivePanel(graphics, scale); }
    }
    private void button(GuiGraphics graphics, String id, int x, int y, int width, int height, Component label,
                        boolean enabled, boolean selected, int mx, int my, Runnable action) {
        boolean hover = mx >= x && mx < x + width && my >= y && my < y + height;
        String value = VillageUiTheme.ellipsize(font, label.getString(), Math.round((width - 12) / (height <= 18 ? 0.75f : 0.85f)));
        VillageUiTheme.drawButton(graphics, font, x, y, width, height, value, enabled, hover, selected || focus.equals(id));
        if (hover && !value.equals(label.getString())) graphics.setTooltipForNextFrame(font, label, mx, my);
        hits.add(new Hit(id, x, y, width, height, action, enabled));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return super.mouseClicked(event, doubled);
        int x = responsiveMouseX(event.x(), WIDTH, HEIGHT), y = responsiveMouseY(event.y(), WIDTH, HEIGHT);
        int top = (height - HEIGHT) / 2;
        for (var hit : hits) if (hit.enabled() && x >= hit.x() && x < hit.x() + hit.width() && y >= hit.y() && y < hit.y() + hit.height()
                && (hit.id().startsWith("tab_") || hit.id().equals("back") || hit.id().equals("previous") || hit.id().equals("next") || y >= top + 61 && y < top + 192)) {
            focus = hit.id(); hit.action().run(); return true;
        }
        return super.mouseClicked(event, doubled);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 22, 0, scrollMax); return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (minecraft != null && minecraft.options.keyInventory.matches(event)) { onClose(); return true; }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            scroll = Math.clamp(scroll + (event.key() == InputConstants.KEY_PAGEDOWN ? 90 : -90), 0, scrollMax); return true;
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
}
