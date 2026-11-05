package de.quest.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;

/** A fixed text viewport; scrolling never moves the surrounding actions. */
public final class InstructionScroll {
    private String content = "";
    private int offset, maximum;
    public int offset() { return offset; }
    public int maximum() { return maximum; }
    public void draw(GuiGraphics g, Font font, String text, int x, int y, int width,
                     int visible, float scale, int color) {
        if (!content.equals(text)) { content = text; offset = 0; }
        var lines = VillageUiTheme.wrapScaledLines(font, text, width - 8, scale);
        maximum = Math.max(0, lines.size() - visible);
        offset = Math.clamp(offset, 0, maximum);
        int step = Math.max(7, Math.round((font.lineHeight + 2) * scale));
        for (int n = 0; n < Math.min(visible, lines.size() - offset); n++)
            VillageUiTheme.drawStringScaled(g, font, lines.get(offset + n), x, y + n * step, color, scale);
        VillageUiTheme.drawScrollBar(g, x + width - 5, y, visible * step, visible * step,
                lines.size() * step, offset * step, maximum * step);
    }
    public boolean scroll(double amount) {
        if (maximum == 0 || amount == 0) return false;
        offset = Math.clamp(offset - (int)Math.signum(amount), 0, maximum); return true;
    }
    public boolean key(KeyEvent key) {
        if (maximum == 0) return false;
        int change = switch (key.key()) {
            case InputConstants.KEY_DOWN -> 1;
            case InputConstants.KEY_UP -> -1;
            case InputConstants.KEY_PAGEDOWN -> 4;
            case InputConstants.KEY_PAGEUP -> -4;
            case InputConstants.KEY_HOME -> -maximum;
            case InputConstants.KEY_END -> maximum;
            default -> 0;
        };
        if (change == 0) return false;
        offset = Math.clamp(offset + change, 0, maximum); return true;
    }
}
