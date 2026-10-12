package de.quest.client.screen;

import de.quest.commands.UiTestTourSteps;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Local tour controller. No commands, gameplay packets or persistent data leave previews. */
public final class UiTestTour {
    private static boolean active, changing;
    private static int index;
    private UiTestTour() {}
    public static boolean active() { return active; }
    public static void start() { active = true; index = 0; open(); }
    private static void open() {
        changing = true;
        try { Minecraft.getInstance().gui.setScreen(UiTestTourScreens.create(UiTestTourSteps.ALL.get(index))); }
        finally { changing = false; }
    }
    public static void end() {
        // Keep the guard active while removed()/onClose() can notify sessions.
        changing = true;
        try { Minecraft.getInstance().gui.setScreen(null); }
        finally { active = false; changing = false; de.quest.client.ui.SurfaceMapRenderer.clearTour(); }
    }
    public static void navigate(Screen screen) {
        if (active) { if (screen == null) end(); return; }
        Minecraft.getInstance().gui.setScreen(screen);
    }
    public static void send(CustomPacketPayload payload) { if (!active) ClientPlayNetworking.send(payload); }
    public static void command(String command) {
        var player = Minecraft.getInstance().player;
        if (!active && player != null) player.connection.sendCommand(command);
    }
    public static void closed() { if (active && !changing) end(); }
    public static void disconnected() { active = false; changing = false; index = 0; de.quest.client.ui.SurfaceMapRenderer.clearTour(); }
    public static void render(GuiGraphics g, Font font, int width, int height, int mx, int my) {
        if (!active) return;
        int y = height - 21;
        g.fill(0, y - 14, width, height, 0xE82D1B12);
        String label = Component.translatable("screen.village-quest.ui_tour.step", index + 1, UiTestTourSteps.ALL.size(),
                Component.translatable("screen.village-quest.ui_tour.family." + UiTestTourSteps.ALL.get(index).family()),
                Component.translatable("screen.village-quest.ui_tour.state." + UiTestTourSteps.ALL.get(index).state())).getString();
        VillageUiTheme.drawStringScaled(g, font, VillageUiTheme.ellipsize(font, label, width - 12), 6, y - 11, VillageUiTheme.LIGHT_TEXT, .68f);
        button(g, font, width / 2 - 110, y, "back", index > 0, mx, my);
        button(g, font, width / 2 - 34, y, index == UiTestTourSteps.ALL.size() - 1 ? "finish" : "next", true, mx, my);
        button(g, font, width / 2 + 42, y, "exit", true, mx, my);
    }
    private static void button(GuiGraphics g, Font f, int x, int y, String key, boolean enabled, int mx, int my) {
        VillageUiTheme.drawButton(g, f, x, y, 68, 17, Component.translatable("screen.village-quest.ui_tour." + key).getString(), enabled,
                mx >= x && mx < x + 68 && my >= y && my < y + 17, false);
    }
    public static boolean click(double x, double y, int width, int height) {
        if (!active || y < height - 35) return false;
        int center = width / 2;
        if (y >= height - 21 && y < height - 4) {
            if (x >= center - 110 && x < center - 42 && index > 0) { index = UiTestTourSteps.move(index, -1); open(); }
            else if (x >= center - 34 && x < center + 34) {
                if (index == UiTestTourSteps.ALL.size() - 1) end(); else { index = UiTestTourSteps.move(index, 1); open(); }
            } else if (x >= center + 42 && x < center + 110) end();
        }
        return true;
    }
}
