package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.client.ui.VillageUiTheme;
import de.quest.config.VillageQuestServerConfig.AdventureProfile;
import de.quest.config.VillageQuestServerConfig.CaravanVisualMode;
import de.quest.config.WorldQuestSettings;
import de.quest.network.Payloads;
import de.quest.network.QuestSetupPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Small journal setup board. World controls always reflect a server-issued session. */
public final class QuestSetupScreen extends CompatScreen {
    private static final int W = 560, H = 344, INK = 0xFF493721;
    private QuestSetupPayloads.View view;
    private WorldQuestSettings settings;
    private final boolean[] personal = new boolean[6];
    private final List<Control> controls = new ArrayList<>();
    private boolean pending;
    private String localError = "";
    private record Control(int x, int y, int width, int height, Runnable action) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    }
    public QuestSetupScreen(QuestSetupPayloads.View view) {
        super(Component.translatable("screen.village-quest.setup.title")); update(view);
    }
    public void update(QuestSetupPayloads.View next) {
        view = next; settings = next.settings(); pending = false; localError = "";
        var p = VillageQuestClientConfig.get().presentation();
        personal[0] = next.tracker(); personal[1] = next.minimap(); personal[2] = p.questNotifications();
        personal[3] = p.caravanNotifications(); personal[4] = p.sounds(); personal[5] = p.speechBubbles();
    }
    private static String text(String suffix, Object... args) {
        return Component.translatable("screen.village-quest.setup." + suffix, args).getString();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Minecraft 26.3 extracts the screen background before this content pass.
        // Requesting the vanilla blur here again crashes with 'Can only blur once per frame'.
        float scale = beginResponsivePanel(g, W, H);
        try {
            controls.clear();
            int x = (width - W) / 2, y = (height - H) / 2;
            int mx = responsiveMouseX(mouseX, W, H), my = responsiveMouseY(mouseY, W, H);
            // One thin wood edge, the established VQ parchment, no ornamental frame assets.
            g.fill(x, y, x + W, y + H, 0xFF604125);
            VillageUiTheme.drawPaper(g, x + 3, y + 3, W - 6, H - 6);
            VillageUiTheme.drawStringScaled(g, font, text(view.firstRun() ? "title" : "settings"), x + 18, y + 14, INK, 1.2f);
            button(g, x + W - 33, y + 9, 22, 20, "×", !pending, mx, my, this::onClose);
            VillageUiTheme.drawWrappedScaled(g, font, text(view.firstRun() ? "intro" : "edit_intro"), x + 18, y + 37, W - 40, INK, .8f, 3);
            VillageUiTheme.drawStringScaled(g, font, text("world"), x + 18, y + 76, INK, .95f);
            VillageUiTheme.drawStringScaled(g, font, text("personal"), x + 294, y + 76, INK, .95f);
            g.fill(x + 278, y + 75, x + 279, y + 264, 0x554F321B);
            String[] labels = {"calendar", "profile", "cooldown", "caravans"};
            String[] values = {text(settings.minecraftCalendar() ? "minecraft" : "realtime"),
                    text("profile." + settings.profile().name().toLowerCase(java.util.Locale.ROOT)),
                    text(settings.storyCooldown() ? "on" : "off"),
                    text("caravans." + settings.caravans().name().toLowerCase(java.util.Locale.ROOT))};
            for (int i = 0; i < labels.length; i++) {
                int row = y + 98 + i * 40, index = i;
                VillageUiTheme.drawStringScaled(g, font, text(labels[i]), x + 18, row, INK, .8f);
                button(g, x + 18, row + 11, 242, 23, values[i], view.editable() && !pending, mx, my, () -> cycle(index));
                if (mx >= x + 18 && mx < x + 260 && my >= row && my < row + 34) {
                    var lines = new ArrayList<Component>();
                    lines.add(Component.translatable("screen.village-quest.setup." + labels[i] + ".help"));
                    if (!view.editable()) lines.add(Component.translatable("screen.village-quest.setup.locked_help"));
                    g.setTooltipForNextFrame(font, lines, mx, my);
                }
            }
            String[] toggles = {"tracker", "minimap", "quest_notifications", "caravan_notifications", "sounds", "speech"};
            for (int i = 0; i < toggles.length; i++) {
                int index = i;
                button(g, x + 294, y + 98 + i * 27, 248, 23,
                        text(toggles[i]) + ": " + text(personal[i] ? "on" : "off"), !pending, mx, my,
                        () -> personal[index] = !personal[index]);
            }
            String status = !localError.isEmpty() ? localError : view.status() > 0 ? text("error." + view.status())
                    : pending ? text("saving") : text(view.editable() ? "world_scope" : "locked");
            VillageUiTheme.drawWrappedScaled(g, font, status, x + 18, y + 269, W - 36,
                    !localError.isEmpty() || view.status() > 0 ? 0xFF9A372C : INK, .78f, 3);
            button(g, x + 18, y + 310, 258, 23, text("defaults"), !pending, mx, my, this::recommended);
            button(g, x + 289, y + 310, 253, 23, text("save"), !pending, mx, my, this::save);
        } finally { endResponsivePanel(g, scale); }
    }
    private void cycle(int index) {
        settings = switch (index) {
            case 0 -> new WorldQuestSettings(!settings.minecraftCalendar(), settings.profile(), settings.storyCooldown(), settings.caravans());
            case 1 -> new WorldQuestSettings(settings.minecraftCalendar(), AdventureProfile.values()[(settings.profile().ordinal() + 1) % 3], settings.storyCooldown(), settings.caravans());
            case 2 -> new WorldQuestSettings(settings.minecraftCalendar(), settings.profile(), !settings.storyCooldown(), settings.caravans());
            default -> new WorldQuestSettings(settings.minecraftCalendar(), settings.profile(), settings.storyCooldown(), CaravanVisualMode.values()[(settings.caravans().ordinal() + 1) % 3]);
        };
    }
    private void recommended() {
        if (view.editable()) settings = WorldQuestSettings.recommended();
        var p = VillageQuestClientConfig.Presentation.recommended();
        personal[0] = p.tracker(); personal[1] = p.minimap(); personal[2] = p.questNotifications();
        personal[3] = p.caravanNotifications(); personal[4] = p.sounds(); personal[5] = p.speechBubbles();
        save();
    }
    private void save() {
        if (pending || !ClientPlayNetworking.canSend(QuestSetupPayloads.Save.ID)) return;
        var p = new VillageQuestClientConfig.Presentation(personal[0], personal[1], personal[2], personal[3], personal[4], personal[5]);
        if (!VillageQuestClientConfig.get().savePresentation(p)) { localError = text("local_error"); return; }
        var config = VillageQuestClientConfig.get();
        ClientPlayNetworking.send(new Payloads.ClientPreferencesPayload(personal[0], personal[2], personal[3], personal[4], config.questProgressSoundVolume()));
        pending = true;
        ClientPlayNetworking.send(new QuestSetupPayloads.Save(view.session(), true,
                view.editable() && !settings.equals(view.settings()), settings, personal[0], personal[1]));
    }
    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled,
                        int mx, int my, Runnable action) {
        var control = new Control(x, y, w, h, action);
        VillageUiTheme.drawButton(g, font, x, y, w, h, label, enabled, control.contains(mx, my), false);
        if (enabled) controls.add(control);
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click, doubled);
        int mx = responsiveMouseX(click.x(), W, H), my = responsiveMouseY(click.y(), W, H);
        for (var control : List.copyOf(controls)) if (control.contains(mx, my)) { control.action.run(); return true; }
        return super.handleMouseClicked(click, doubled);
    }
    @Override public void onClose() {
        if (pending) return;
        if (ClientPlayNetworking.canSend(QuestSetupPayloads.Save.ID))
            ClientPlayNetworking.send(new QuestSetupPayloads.Save(view.session(), false, false, view.settings(), personal[0], personal[1]));
        super.onClose();
    }
}
