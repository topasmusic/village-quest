package de.quest.client.ui;

import de.quest.client.compat.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Short, wrapped hints drawn after the responsive board transform has ended. */
public final class VillageTooltip {
    private List<Component> content = List.of();

    public void clear() { content = List.of(); }
    public void set(Component value) { set(List.of(value)); }
    public void set(List<Component> values) { content = List.copyOf(values); }

    public void draw(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (content.isEmpty()) return;
        int screenWidth = graphics.guiWidth(), screenHeight = graphics.guiHeight();
        float scale = .8f;
        int step = Math.max(8, (int) Math.ceil(font.lineHeight * scale) + 2);
        int maxWidth = Math.max(1, Math.min(200, screenWidth - 20));
        int maxLines = Math.max(1, Math.min(7, (screenHeight - 20) / step));
        List<String> lines = new ArrayList<>();
        for (Component value : content) for (var wrapped : font.split(value, Math.max(1, (int) (maxWidth / scale)))) {
            StringBuilder line = new StringBuilder();
            wrapped.accept((index, style, codepoint) -> { line.appendCodePoint(codepoint); return true; });
            lines.add(line.toString());
        }
        if (lines.isEmpty()) return;
        boolean shortened = lines.size() > maxLines;
        if (shortened) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            String last = lines.getLast();
            lines.set(lines.size() - 1, VillageUiTheme.ellipsize(font, last + " …", (int) (maxWidth / scale)));
        }
        int textWidth = lines.stream().mapToInt(font::width).max().orElse(1);
        int boxWidth = Math.min(screenWidth - 8, (int) Math.ceil(textWidth * scale) + 12);
        int boxHeight = Math.min(screenHeight - 8, lines.size() * step + 10);
        int x = mouseX + 12, y = mouseY + 10;
        if (x + boxWidth > screenWidth - 4) x = mouseX - boxWidth - 12;
        if (y + boxHeight > screenHeight - 4) y = mouseY - boxHeight - 10;
        x = Math.clamp(x, 4, Math.max(4, screenWidth - boxWidth - 4));
        y = Math.clamp(y, 4, Math.max(4, screenHeight - boxHeight - 4));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0181510);
        graphics.fill(x, y, x + boxWidth, y + 1, VillageUiTheme.GOLD);
        graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, VillageUiTheme.GOLD);
        graphics.fill(x, y, x + 1, y + boxHeight, VillageUiTheme.GOLD);
        graphics.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, VillageUiTheme.GOLD);
        for (int i = 0; i < lines.size(); i++)
            VillageUiTheme.drawStringScaled(graphics, font, lines.get(i), x + 6, y + 5 + i * step, VillageUiTheme.LIGHT_TEXT, scale);
    }
}
