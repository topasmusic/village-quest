package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.QuestArtwork;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.network.RewardPayloads;
import de.quest.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Frozen quest packages: inspect a choice, then confirm it exactly once. */
public final class VanillaRewardScreen extends CompatScreen {
    @Override protected float panelSizeFactor() { return super.panelSizeFactor()*0.85f; }
    private static final int PANEL_WIDTH = 416, PANEL_HEIGHT = 264;
    private static final int CHOICES_X = 146, CHOICES_Y = 78, CHOICES_WIDTH = 250;
    private static final int BUTTON_Y = 229, BUTTON_HEIGHT = 18;
    private final RewardPayloads.View data;
    private Screen parent;
    public Screen parent() { return parent; }
    public void parent(Screen screen) { parent = screen; }
    private int selected, page;
    private boolean submitted;

    public VanillaRewardScreen(RewardPayloads.View data, Screen parent) {
        super(Component.translatable("reward.village-quest.choose"));
        this.data = data; this.parent = parent;
        for (int i = 0; i < data.choices().size(); i++)
            if (data.choices().get(i).id().equals(data.preferred())) selected = i;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(g, width, height);
        float scale = beginResponsivePanel(g, PANEL_WIDTH, PANEL_HEIGHT);
        try {
            int x = (width - PANEL_WIDTH) / 2, y = (height - PANEL_HEIGHT) / 2;
            int mx = responsiveMouseX(mouseX, PANEL_WIDTH, PANEL_HEIGHT), my = responsiveMouseY(mouseY, PANEL_WIDTH, PANEL_HEIGHT);
            VillageUiTheme.drawBoard(g, x, y, PANEL_WIDTH, PANEL_HEIGHT);
            VillageUiTheme.drawTitle(g, font, title.getString(), "quests", x + 64, y + 5, 288);
            VillageUiTheme.drawPaper(g, x + 20, y + 78, 118, 143);
            var artwork = QuestArtwork.forQuest(data.key(), null).filter(QuestArtwork.Motif::hasIllustration);
            if (artwork.isPresent()) de.quest.client.ui.JournalV3Visuals.illustration(g, artwork.get().largeTexture(), x + 28, y + 88, 102, 38);
            else VillageUiTheme.drawIcon(g, VillageUiTheme.icon("quests"), x + 63, y + 91, 32);
            var hint = font.split(Component.translatable("reward.village-quest.base_kept"), 125);
            int hintY = y + 135;
            for (var line : hint) {
                var pose = g.pose(); pose.pushMatrix(); pose.scale(.8f, .8f);
                g.unwrap().text(font, line, Math.round((x + 29) / .8f), Math.round(hintY / .8f), VillageUiTheme.BODY, false);
                pose.popMatrix(); hintY += 8;
            }
            if (within(mx, my, x + 20, y + 78, 118, 143))
                g.setTooltipForNextFrame(font, Component.translatable("reward.village-quest.base_kept"), mx, my);
            VillageUiTheme.drawPaper(g, x + CHOICES_X, y + 106, CHOICES_WIDTH, 115);
            for (int i = 0; i < data.choices().size(); i++) {
                int left = x + CHOICES_X + i * tabWidth();
                var choice = data.choices().get(i);
                String label = Component.translatable("reward.village-quest.option." + choice.id()).getString();
                boolean hover = within(mx, my, left, y + CHOICES_Y, tabWidth() - 4, 22);
                VillageUiTheme.drawButton(g, font, left, y + CHOICES_Y, tabWidth() - 4, 22,
                        label, !submitted, hover, selected == i);
                if (hover) g.setTooltipForNextFrame(font, Component.literal(label), mx, my);
            }
            if (!data.choices().isEmpty()) drawChoice(g, x, y, mx, my);
            VillageUiTheme.drawPaper(g,x+20,y+BUTTON_Y-4,376,26);
            KingdomUiAssets.closeButton(g,x,y,PANEL_WIDTH,PANEL_HEIGHT,KingdomUiAssets.closeBounds(x,y,PANEL_WIDTH,PANEL_HEIGHT).contains(mx,my));
            VillageUiTheme.drawButton(g, font, x + 20, y + BUTTON_Y, 118, BUTTON_HEIGHT,
                    Component.translatable("reward.village-quest.later").getString(), !submitted,
                    within(mx, my, x + 20, y + BUTTON_Y, 118, BUTTON_HEIGHT), false);
            VillageUiTheme.drawButton(g, font, x + CHOICES_X, y + BUTTON_Y, CHOICES_WIDTH, BUTTON_HEIGHT,
                    Component.translatable("reward.village-quest.confirm").getString(), !submitted && !data.choices().isEmpty(),
                    within(mx, my, x + CHOICES_X, y + BUTTON_Y, CHOICES_WIDTH, BUTTON_HEIGHT), true);
        } finally { endResponsivePanel(g, scale); }
    }

    private void drawChoice(GuiGraphics g, int x, int y, int mx, int my) {
        var choice = data.choices().get(selected);
        var rows = RewardPreview.rows(choice);
        int pages = pageCount(); page = Math.max(0, Math.min(page, pages - 1));
        if (!rows.isEmpty()) {
            int start = page * 3, end = Math.min(rows.size(), start + 3);
            for (int i = start; i < end; i++) {
                var row = rows.get(i); var stack = previewStack(row);
                Component name = row.currency().isEmpty() ? stack.getHoverName() : Component.translatable(
                        "text.village-quest.currency.unit." + row.currency() + (row.count() == 1 ? ".one" : ".other"));
                String label = row.count() + " × " + name.getString();
                int rowY = y + 118 + (i - start) * 26;
                drawItem(g, stack, x + 158, rowY, 1f);
                VillageUiTheme.drawStringScaled(g, font, VillageUiTheme.ellipsize(font, label, 258),
                        x + 182, rowY + 5, VillageUiTheme.INK, .8f);
                if (within(mx, my, x + 154, rowY, 230, 24))
                    g.setTooltipForNextFrame(font, choice.lines().isEmpty() ? java.util.List.of(Component.literal(label)) : choice.lines(), mx, my);
            }
        } else {
            var lines = choice.lines().stream().flatMap(c -> font.split(c, 224).stream()).toList();
            int start = page * 6;
            for (int i = start; i < Math.min(lines.size(), start + 6); i++)
                g.unwrap().text(font, lines.get(i), x + 158, y + 117 + (i - start) * 12, VillageUiTheme.BODY, false);
        }
        if (pages > 1) VillageUiTheme.drawButton(g, font, x + 346, y + 202, 42, 15,
                (page + 1) + "/" + pages, !submitted, within(mx, my, x + 346, y + 202, 42, 15), false);
    }
    private int tabWidth() { return CHOICES_WIDTH / Math.max(1, data.choices().size()); }
    private int pageCount() {
        if (data.choices().isEmpty()) return 1;
        var choice = data.choices().get(selected); var rows = RewardPreview.rows(choice);
        return Math.max(1, rows.isEmpty() ? (choice.lines().stream().mapToInt(c -> font.split(c, 224).size()).sum() + 5) / 6
                : (rows.size() + 2) / 3);
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (submitted || click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        int mx = responsiveMouseX(click.x(), PANEL_WIDTH, PANEL_HEIGHT), my = responsiveMouseY(click.y(), PANEL_WIDTH, PANEL_HEIGHT);
        int x = (width - PANEL_WIDTH) / 2, y = (height - PANEL_HEIGHT) / 2;
        if(KingdomUiAssets.closeBounds(x,y,PANEL_WIDTH,PANEL_HEIGHT).contains(mx,my)) {onClose();return true;}
        for (int i = 0; i < data.choices().size(); i++) {
            if (within(mx, my, x + CHOICES_X + i * tabWidth(), y + CHOICES_Y, tabWidth() - 4, 22)) {
                selected = i; page = 0; return true;
            }
        }
        if (pageCount() > 1 && within(mx, my, x + 346, y + 202, 42, 15)) {
            page = (page + 1) % pageCount(); return true;
        }
        if (UiTestTour.active()) return within(mx,my,x+20,y+BUTTON_Y,118,BUTTON_HEIGHT)
                || within(mx,my,x+CHOICES_X,y+BUTTON_Y,CHOICES_WIDTH,BUTTON_HEIGHT);
        if (within(mx, my, x + 20, y + BUTTON_Y, 118, BUTTON_HEIGHT)) {
            if (minecraft.player != null) minecraft.gui.chatListener().handleSystemMessage(
                    Component.translatable("reward.village-quest.later_hint").withStyle(net.minecraft.ChatFormatting.GOLD), false);
            onClose(); return true;
        }
        if (!data.choices().isEmpty() && within(mx, my, x + CHOICES_X, y + BUTTON_Y, CHOICES_WIDTH, BUTTON_HEIGHT)) {
            submitted = true;
            UiTestTour.send(new RewardPayloads.Action(data.session(), data.choices().get(selected).id()));
            minecraft.gui.setScreen(parent); return true;
        }
        return false;
    }
    private static boolean within(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static ItemStack previewStack(RewardPreview.Entry row) {
        return row.currency().isEmpty() ? row.stack() : new ItemStack(row.currency().equals("crown") ? ModItems.CROWN : ModItems.SILVERMARK);
    }
    private static void drawItem(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        var pose = g.pose(); pose.pushMatrix(); pose.translate(x, y); pose.scale(scale, scale);
        g.renderItem(stack, 0, 0); pose.popMatrix();
    }
    @Override public void onClose() {
        if (UiTestTour.active()) { UiTestTour.closed(); return; }
        minecraft.gui.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
