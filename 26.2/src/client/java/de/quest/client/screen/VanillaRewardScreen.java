package de.quest.client.screen;

import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.RewardPayloads;
import de.quest.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** One visible extra-package choice, earned at quest turn-in with options frozen at acceptance. */
public final class VanillaRewardScreen extends CompatScreen {
    private static final int PANEL_WIDTH = 320, PANEL_HEIGHT = 180;
    private final RewardPayloads.View data;
    private Screen parent;
    public Screen parent() { return parent; }
    public void parent(Screen screen) { parent = screen; }
    private int selected, page;
    private boolean submitted;
    public VanillaRewardScreen(RewardPayloads.View data, Screen parent) {
        super(Component.translatable("reward.village-quest.choose")); this.data = data; this.parent = parent;
        for (int i = 0; i < data.choices().size(); i++) if (data.choices().get(i).id().equals(data.preferred())) selected = i;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Screen.extractRenderStateWithTooltipAndSubtitles already extracts the background once.
        float scale = beginResponsivePanel(g, PANEL_WIDTH, PANEL_HEIGHT);
        int x = (width - PANEL_WIDTH) / 2, y = (height - PANEL_HEIGHT) / 2;
        int mx = responsiveMouseX(mouseX, PANEL_WIDTH, PANEL_HEIGHT), my = responsiveMouseY(mouseY, PANEL_WIDTH, PANEL_HEIGHT);
        VillageUiTheme.blitScaled(g, Identifier.fromNamespaceAndPath("village-quest", "textures/gui/reward_panel.png"), x, y, PANEL_WIDTH, PANEL_HEIGHT, 1672, 941);
        g.unwrap().text(font, title, x + (PANEL_WIDTH - font.width(title)) / 2, y + 14, 0xFF594127, false);
        var hint = font.split(Component.translatable("reward.village-quest.base_kept"), PANEL_WIDTH - 30);
        int lineY = y + 32;
        for (var line : hint) { g.unwrap().text(font, line, x + 15, lineY, 0xFF594127, false); lineY += 10; }
        int tabWidth = (PANEL_WIDTH - 30) / Math.max(1, data.choices().size());
        for (int i = 0; i < data.choices().size(); i++) {
            int left = x + 15 + i * tabWidth;
            var choice = data.choices().get(i);
            var preview = RewardPreview.rows(choice);
            String label = Component.translatable("reward.village-quest.option." + choice.id()).getString();
            VillageUiTheme.drawButton(g, font, left, y + 50, tabWidth - 4, 20, preview.isEmpty() ? label : "", !submitted,
                    mx >= left && mx < left + tabWidth - 4 && my >= y + 50 && my < y + 70, selected == i);
            if (!preview.isEmpty()) {
                float textScale = Math.min(.8f, (tabWidth - 22f) / Math.max(1, font.width(label)));
                int total = 12 + Math.round(font.width(label) * textScale), startX = left + (tabWidth - 4 - total) / 2;
                drawItem(g, previewStack(preview.getFirst()), startX, y + 55, .6f);
                VillageUiTheme.drawStringScaled(g, font, label, startX + 12, y + 57, 0xFFFFE9BC, textScale);
            }
        }
        var choice = data.choices().get(selected); var rows = RewardPreview.rows(choice);
        int pages;
        if (!rows.isEmpty()) {
            pages = (rows.size() + 2) / 3;
            int start = page * 3, end = Math.min(rows.size(), start + 3);
            int top = y + 77 + (66 - (end - start) * 22) / 2;
            for (int i = start; i < end; i++) {
                var row = rows.get(i); var stack = previewStack(row);
                Component name = row.currency().isEmpty() ? stack.getHoverName() : Component.translatable(
                        "text.village-quest.currency.unit." + row.currency() + (row.count() == 1 ? ".one" : ".other"));
                String label = row.count() + " × " + name.getString();
                float textScale = Math.min(1f, 244f / Math.max(1, font.width(label)));
                int rowWidth = 26 + Math.round(font.width(label) * textScale), left = x + (PANEL_WIDTH - rowWidth) / 2;
                int rowY = top + (i - start) * 22;
                drawItem(g, stack, left, rowY, 1.2f);
                VillageUiTheme.drawStringScaled(g, font, label, left + 26, rowY + 6, 0xFF382E22, textScale);
                if (mx >= left && mx < left + rowWidth && my >= rowY && my < rowY + 21)
                    g.setTooltipForNextFrame(font, choice.lines(), mouseX, mouseY);
            }
        } else {
            // Text-only older test/view data stays readable; saved .15 offers also supply native previews.
            var lines = choice.lines().stream().flatMap(c -> font.split(c, PANEL_WIDTH - 40).stream()).toList();
            pages = (lines.size() + 5) / 6; int start = page * 6;
            for (int i = start; i < Math.min(lines.size(), start + 6); i++)
                g.unwrap().text(font, lines.get(i), x + (PANEL_WIDTH - font.width(lines.get(i))) / 2, y + 80 + (i - start) * 11, 0xFF382E22, false);
        }
        if (pages > 1) VillageUiTheme.drawButton(g, font, x + 268, y + 130, 35, 16, (page + 1) + "/" + pages, true, false, false);
        VillageUiTheme.drawButton(g, font, x + 16, y + 150, 80, 20, Component.translatable("reward.village-quest.later").getString(), !submitted, mx >= x + 16 && mx < x + 96 && my >= y + 150 && my < y + 170, false);
        VillageUiTheme.drawButton(g, font, x + 104, y + 150, 200, 20, Component.translatable("reward.village-quest.confirm").getString(), !submitted, mx >= x + 104 && mx < x + 304 && my >= y + 150 && my < y + 170, true);
        endResponsivePanel(g, scale);
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (submitted || click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        int mx = responsiveMouseX(click.x(), PANEL_WIDTH, PANEL_HEIGHT), my = responsiveMouseY(click.y(), PANEL_WIDTH, PANEL_HEIGHT);
        int x = (width - PANEL_WIDTH) / 2, y = (height - PANEL_HEIGHT) / 2;
        if (my >= y + 50 && my < y + 70 && mx >= x + 15 && mx < x + PANEL_WIDTH - 15) {
            selected = Math.min(data.choices().size() - 1, (mx - x - 15) / ((PANEL_WIDTH - 30) / data.choices().size())); page = 0; return true;
        }
        if (my >= y + 130 && my < y + 146 && mx >= x + 268 && mx < x + 303) {
            var choice = data.choices().get(selected); var rows = RewardPreview.rows(choice);
            int pages = rows.isEmpty() ? (choice.lines().stream().mapToInt(c -> font.split(c, PANEL_WIDTH - 40).size()).sum() + 5) / 6 : (rows.size() + 2) / 3;
            page = (page + 1) % Math.max(1, pages); return true;
        }
        if (my >= y + 150 && my < y + 170) {
            if (mx >= x + 16 && mx < x + 96) {
                if (minecraft.player != null) minecraft.gui.chatListener().handleSystemMessage(
                        Component.translatable("reward.village-quest.later_hint").withStyle(net.minecraft.ChatFormatting.GOLD), false);
                onClose(); return true;
            }
            if (mx >= x + 104 && mx < x + 304) {
                submitted = true; ClientPlayNetworking.send(new RewardPayloads.Action(data.session(), data.choices().get(selected).id()));
                minecraft.gui.setScreen(parent); return true;
            }
        }
        return false;
    }
    private static ItemStack previewStack(RewardPreview.Entry row) {
        return row.currency().isEmpty() ? row.stack() : new ItemStack(row.currency().equals("crown") ? ModItems.CROWN : ModItems.SILVERMARK);
    }
    private static void drawItem(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        var pose = g.pose(); pose.pushMatrix(); pose.translate(x, y); pose.scale(scale, scale);
        g.renderItem(stack, 0, 0); pose.popMatrix();
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
