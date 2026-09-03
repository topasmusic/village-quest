package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.SurfaceMapRenderer;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import de.quest.network.VillageNetworkPayloads;
import de.quest.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Village Quest-styled map and destination board for a bound Guild Wayshrine. */
public final class WayshrineScreen extends CompatScreen {
    private static final Identifier BOARD_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/wayshrine_board.png");
    private static final int WINDOW_WIDTH = 432;
    private static final int WINDOW_HEIGHT = 248;
    private static final int MAP_X = 35;
    private static final int MAP_Y = 55;
    private static final int MAP_WIDTH = 229;
    private static final int MAP_HEIGHT = 145;
    private static final int ZOOM_X = MAP_X + MAP_WIDTH - 19;
    private static final int ZOOM_Y = MAP_Y + 6;
    private static final int CONTROL_SIZE = 16;
    private static final int CONTROL_GAP = 2;
    private static final int LIST_X = 281;
    private static final int LIST_Y = 56;
    private static final int LIST_WIDTH = 116;
    private static final int ROW_HEIGHT = 28;
    private static final int VISIBLE_ROWS = 5;
    private static final int STATUS_X = 151;
    private static final int STATUS_Y = 35;
    private static final int STATUS_WIDTH = 130;
    private static final int TRAVEL_X = 38;
    private static final int TRAVEL_WIDTH = 131;
    private static final int PAYMENT_X = 185;
    private static final int PAYMENT_WIDTH = 64;
    private static final int RENAME_X = 266;
    private static final int RENAME_WIDTH = 130;
    private static final int FOOTER_Y = 214;
    private static final int FOOTER_HEIGHT = 17;
    private static final int INK = 0xFF3E2918;
    private static final int MUTED = 0xFF80694F;
    private static final float STATUS_SCALE = 0.62f;
    /* Keep the shrine map on the exact same stepped scale as the main living map. */
    private static final double[] ZOOM_FACTORS = {
            24.0, 16.0, 10.0, 6.0, 3.5, 2.0, 1.0, 0.70, 0.48, 0.32
    };
    private static final String[] ZOOM_LABELS = {
            "4%", "6%", "10%", "17%", "29%", "50%", "100%", "140%", "210%", "310%"
    };
    private static final int DEFAULT_ZOOM_LEVEL = 6;

    private VillageNetworkPayloads.WayshrinePayload data;
    private int selectedIndex;
    private boolean mapDragging;
    private double centerX;
    private double centerZ;
    private int zoomLevel = DEFAULT_ZOOM_LEVEL;
    private long cooldownObservedAtMillis = System.currentTimeMillis();
    private int listScroll;
    private boolean renaming;
    private boolean useCharge;
    private EditBox nameField;

    public WayshrineScreen(VillageNetworkPayloads.WayshrinePayload data) {
        super(Component.translatable("screen.village-quest.wayshrine.title"));
        this.data = data;
        this.useCharge = data.charges() > 0;
        this.selectedIndex = choices().isEmpty() ? -1 : choices().getFirst().index();
        ensureWayshrineRenderers();
        resetMapCenter();
    }

    public void updateData(VillageNetworkPayloads.WayshrinePayload updated) {
        this.data = updated;
        if (updated.charges() <= 0) useCharge = false;
        this.cooldownObservedAtMillis = System.currentTimeMillis();
        if (updated.destinations().stream().noneMatch(value -> value.index() == selectedIndex && !value.current())) {
            selectedIndex = choices().isEmpty() ? -1 : choices().getFirst().index();
        }
        listScroll = Math.min(listScroll, maxListScroll());
        ensureWayshrineRenderers();
        resetMapCenter();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        nameField = new EditBox(font, left + 112, top + 113, 208, 20,
                Component.translatable("screen.village-quest.wayshrine.rename_hint"));
        nameField.setMaxLength(32);
        nameField.setVisible(false);
        nameField.setBordered(false);
        nameField.setTextColor(INK);
        nameField.setTextColorUneditable(MUTED);
        nameField.setInvertHighlightedTextColor(false);
        nameField.setHint(Component.translatable("screen.village-quest.wayshrine.rename_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameField);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        float scale = beginResponsivePanel(graphics, WINDOW_WIDTH, WINDOW_HEIGHT);
        try {
            int left = (width - WINDOW_WIDTH) / 2;
            int top = (height - WINDOW_HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD_TEXTURE, left, top, 0.0f, 0.0f,
                    WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
            drawHeader(graphics, left, top, uiMouseX, uiMouseY);
            drawMap(graphics, left, top, uiMouseX, uiMouseY);
            drawDestinations(graphics, left, top, uiMouseX, uiMouseY);
            drawFooter(graphics, left, top, uiMouseX, uiMouseY);
            if (renaming) drawRenameOverlay(graphics, left, top, uiMouseX, uiMouseY);
            super.render(graphics, uiMouseX, uiMouseY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String heading = VillageUiTheme.ellipsize(font, title.getString(), 138);
        graphics.drawString(font, heading, left + (WINDOW_WIDTH - font.width(heading)) / 2,
                top + 13, INK, false);
        VillageUiTheme.drawWalletStrip(graphics, font, left, top, WINDOW_WIDTH, data.balance(), 49, 11);

        Payloads.TradeRouteShrineData current = current();
        String charges = data.charges() + "/" + data.maxCharges();
        float chargesWidth = font.width(charges) * STATUS_SCALE;
        float chargesX = left + STATUS_X + STATUS_WIDTH - 20 - chargesWidth;
        float currentX = left + STATUS_X + 12;
        if (current != null) {
            int currentWidth = Math.max(1, (int) Math.floor(
                    (chargesX - 5 - currentX) / STATUS_SCALE));
            VillageUiTheme.drawStringScaled(graphics, font,
                    VillageUiTheme.ellipsize(font, current.name().getString(), currentWidth),
                    currentX, top + STATUS_Y + 3, INK, STATUS_SCALE);
        }
        VillageUiTheme.drawStringScaled(graphics, font, charges,
                chargesX, top + STATUS_Y + 3,
                data.charges() > 0 ? VillageUiTheme.TEAL : MUTED, STATUS_SCALE);

        if (current != null && within(mouseX, mouseY,
                left + STATUS_X, top + STATUS_Y, STATUS_WIDTH, 13)) {
            String subtitle = Component.translatable(data.owner()
                            ? "screen.village-quest.wayshrine.from_owner"
                            : "screen.village-quest.wayshrine.from_guest",
                    current.name(), data.ownerName()).getString();
            graphics.setTooltipForNextFrame(font, List.of(
                    Component.literal(subtitle),
                    Component.translatable("screen.village-quest.wayshrine.charges",
                            data.charges(), data.maxCharges())), mouseX, mouseY);
        }
    }

    private void drawMap(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + MAP_X;
        int y = top + MAP_Y;
        MapBounds bounds = bounds();
        graphics.enableScissor(x, y, x + MAP_WIDTH, y + MAP_HEIGHT);
        SurfaceMapRenderer.drawScreen(graphics, x, y, MAP_WIDTH, MAP_HEIGHT,
                bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ, !mapDragging);
        drawMapConnections(graphics, bounds, left, top);
        for (Payloads.TradeRouteShrineData shrine : data.destinations()) {
            Point point = pointFor(shrine, bounds, left, top);
            if (shrine.index() == selectedIndex) {
                graphics.fill(point.x - 7, point.y - 7, point.x + 8, point.y + 8, 0x6637B5AC);
            }
            VillageUiTheme.drawMarker(graphics, "shrine", point.x, point.y, 19);
            boolean hovered = Math.abs(mouseX - point.x) <= 11 && Math.abs(mouseY - point.y) <= 11;
            if (hovered) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(shrine.name());
                tooltip.add(Component.translatable("screen.village-quest.wayshrine.map_coordinates",
                        shrine.worldX(), shrine.worldZ()));
                tooltip.add(shrine.current()
                        ? Component.translatable("screen.village-quest.wayshrine.current")
                        : Component.translatable("screen.village-quest.wayshrine.cost", shrine.cost()));
                graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        drawMapControls(graphics, left, top, mouseX, mouseY);
    }

    private void drawMapControls(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String[] icons = {"plus", "minus", "focus"};
        String[] labels = {
                "screen.village-quest.trade_route.zoom_in",
                "screen.village-quest.trade_route.zoom_out",
                "screen.village-quest.wayshrine.recenter"
        };
        for (int i = 0; i < icons.length; i++) {
            int x = left + ZOOM_X;
            int y = top + ZOOM_Y + i * (CONTROL_SIZE + CONTROL_GAP);
            boolean enabled = i == 0 ? zoomLevel < ZOOM_FACTORS.length - 1
                    : i == 1 ? zoomLevel > 0 : true;
            boolean hovered = enabled && within(mouseX, mouseY, x, y, CONTROL_SIZE, CONTROL_SIZE);
            VillageUiTheme.drawButton(graphics, font, x, y, CONTROL_SIZE, CONTROL_SIZE,
                    "", enabled, hovered, false);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon(icons[i]), x + 3, y + 3, 10);
            if (within(mouseX, mouseY, x, y, CONTROL_SIZE, CONTROL_SIZE)) {
                graphics.setTooltipForNextFrame(font, Component.translatable(labels[i]), mouseX, mouseY);
            }
        }
        String zoom = ZOOM_LABELS[zoomLevel];
        int zoomWidth = font.width(zoom) + 6;
        int zoomY = top + ZOOM_Y + 3 * (CONTROL_SIZE + CONTROL_GAP) + 1;
        graphics.fill(left + ZOOM_X + CONTROL_SIZE - zoomWidth, zoomY,
                left + ZOOM_X + CONTROL_SIZE, zoomY + 11, 0xD9F4E3BE);
        graphics.drawString(font, zoom, left + ZOOM_X + CONTROL_SIZE - zoomWidth + 3,
                zoomY + 2, MUTED, false);
    }

    private void drawMapConnections(GuiGraphics graphics, MapBounds bounds, int left, int top) {
        Payloads.TradeRouteShrineData current = current();
        if (current == null) return;
        Point from = pointFor(current, bounds, left, top);
        for (Payloads.TradeRouteShrineData shrine : choices()) {
            Point to = pointFor(shrine, bounds, left, top);
            drawDashedLine(graphics, from.x, from.y, to.x, to.y,
                    shrine.index() == selectedIndex ? 0xFF49BFB5 : 0xFF6A7C70);
        }
    }

    private void drawDestinations(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int y = top + LIST_Y;
        for (Payloads.TradeRouteShrineData shrine : visibleChoices()) {
            boolean hovered = within(mouseX, mouseY, left + LIST_X, y, LIST_WIDTH, 26);
            boolean selected = shrine.index() == selectedIndex;
            if (selected) {
                graphics.fill(left + LIST_X + 3, y + 3,
                        left + LIST_X + LIST_WIDTH - 3, y + 24, 0x2237B5AC);
                graphics.fill(left + LIST_X + 4, y + 4,
                        left + LIST_X + 7, y + 23, 0xCC236B68);
            } else if (hovered) {
                graphics.fill(left + LIST_X + 3, y + 3,
                        left + LIST_X + LIST_WIDTH - 3, y + 24, 0x14FFFFFF);
            }
            VillageUiTheme.drawMarker(graphics, "shrine", left + LIST_X + 13, y + 13, 14);
            String name = VillageUiTheme.ellipsize(font, shrine.name().getString(), 87);
            graphics.drawString(font, name, left + LIST_X + 24, y + 4, INK, false);
            String detail = Component.translatable("screen.village-quest.wayshrine.list_detail",
                    distanceTo(shrine), shrine.cost()).getString();
            VillageUiTheme.drawStringScaled(graphics, font, detail,
                    left + LIST_X + 24, y + 15, MUTED, 0.62f);
            if (hovered) {
                graphics.setTooltipForNextFrame(font, destinationTooltip(shrine), mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }
        VillageUiTheme.drawScrollBar(graphics, left + LIST_X + LIST_WIDTH - 3, top + LIST_Y, 138,
                VISIBLE_ROWS * ROW_HEIGHT, choices().size() * ROW_HEIGHT,
                listScroll * ROW_HEIGHT, maxListScroll() * ROW_HEIGHT);
    }

    private void drawFooter(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        Payloads.TradeRouteShrineData selected = selected();
        int cooldown = cooldownSeconds();
        String travel = cooldown > 0
                ? Component.translatable("screen.village-quest.wayshrine.cooldown_action", cooldown).getString()
                : selected == null
                ? Component.translatable("screen.village-quest.wayshrine.select_action").getString()
                : Component.translatable("screen.village-quest.wayshrine.travel_action").getString();
        boolean paymentAvailable = selected != null && (useCharge
                ? data.charges() >= selected.chargeCost() : data.balance() >= selected.cost());
        boolean travelEnabled = selected != null && cooldown <= 0 && paymentAvailable;
        boolean travelHover = within(mouseX, mouseY,
                left + TRAVEL_X, top + FOOTER_Y, TRAVEL_WIDTH, FOOTER_HEIGHT);
        drawFooterAction(graphics, left + TRAVEL_X, top + FOOTER_Y, TRAVEL_WIDTH,
                travel, travelEnabled, travelHover, false);
        if (travelHover && selected != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(selected.name());
            tooltip.addAll(destinationTooltip(selected));
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
        boolean paymentHover = within(mouseX, mouseY,
                left + PAYMENT_X, top + FOOTER_Y, PAYMENT_WIDTH, FOOTER_HEIGHT);
        String payment = Component.translatable(useCharge
                ? "screen.village-quest.wayshrine.payment_charge"
                : "screen.village-quest.wayshrine.payment_coins").getString();
        drawFooterAction(graphics, left + PAYMENT_X, top + FOOTER_Y, PAYMENT_WIDTH,
                payment, true, paymentHover, useCharge);
        if (paymentHover) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable(
                    "screen.village-quest.wayshrine.payment_tooltip", data.charges(), data.chargesPerShard())),
                    mouseX, mouseY);
        }
        boolean renameHover = within(mouseX, mouseY,
                left + RENAME_X, top + FOOTER_Y, RENAME_WIDTH, FOOTER_HEIGHT);
        drawFooterAction(graphics, left + RENAME_X, top + FOOTER_Y, RENAME_WIDTH,
                Component.translatable("screen.village-quest.wayshrine.rename_action").getString(),
                current() != null && data.owner(), renameHover, false);
    }

    private void drawFooterAction(GuiGraphics graphics, int x, int y, int width,
                                  String label, boolean enabled, boolean hovered, boolean active) {
        if (!enabled) {
            graphics.fill(x + 3, y + 3, x + width - 3, y + FOOTER_HEIGHT - 3, 0x66261C16);
        } else if (active) {
            graphics.fill(x + 3, y + 3, x + width - 3, y + FOOTER_HEIGHT - 3, 0x6637B5AC);
        } else if (hovered) {
            graphics.fill(x + 3, y + 3, x + width - 3, y + FOOTER_HEIGHT - 3, 0x24FFF1C7);
        }
        float scale = 0.72f;
        int available = Math.max(4, (int) Math.floor((width - 10) / scale));
        String visible = VillageUiTheme.ellipsize(font, label, available);
        float textWidth = font.width(visible) * scale;
        float textHeight = font.lineHeight * scale;
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                x + (width - textWidth) / 2.0f,
                y + (FOOTER_HEIGHT - textHeight) / 2.0f - 1.5f,
                enabled ? VillageUiTheme.LIGHT_TEXT : VillageUiTheme.DISABLED_TEXT, scale);
    }

    private void drawRenameOverlay(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        graphics.fill(left + 76, top + 78, left + 356, top + 167, 0xD92A2119);
        VillageUiTheme.drawCard(graphics, left + 81, top + 83, 270, 79, false, false);
        String heading = Component.translatable("screen.village-quest.wayshrine.rename_title").getString();
        graphics.drawCenteredString(font, heading, left + WINDOW_WIDTH / 2, top + 92, INK);
        graphics.fill(left + 107, top + 109, left + 325, top + 137, 0xFF6B4726);
        graphics.fill(left + 109, top + 111, left + 323, top + 135, 0xFFE8D3A5);
        boolean saveHover = within(mouseX, mouseY, left + 107, top + 139, 104, 18);
        boolean cancelHover = within(mouseX, mouseY, left + 221, top + 139, 104, 18);
        VillageUiTheme.drawButton(graphics, font, left + 107, top + 139, 104, 18,
                Component.translatable("screen.village-quest.wayshrine.rename_save").getString(),
                nameField != null && !nameField.getValue().trim().isEmpty(), saveHover, false);
        VillageUiTheme.drawButton(graphics, font, left + 221, top + 139, 104, 18,
                Component.translatable("screen.village-quest.wayshrine.rename_cancel").getString(),
                true, cancelHover, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WINDOW_WIDTH, WINDOW_HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WINDOW_WIDTH, WINDOW_HEIGHT);
        if (renaming) {
            if (within(mouseX, mouseY, left + 107, top + 139, 104, 18)) {
                saveRename();
                return true;
            }
            if (within(mouseX, mouseY, left + 221, top + 139, 104, 18)) {
                closeRename();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }
        if (handleMapControlClick(mouseX, mouseY, left, top)) {
            return true;
        }
        int y = top + LIST_Y;
        for (Payloads.TradeRouteShrineData shrine : visibleChoices()) {
            if (within(mouseX, mouseY, left + LIST_X, y, LIST_WIDTH, 26)) {
                selectedIndex = shrine.index();
                focusMapOn(shrine);
                return true;
            }
            y += ROW_HEIGHT;
        }
        MapBounds bounds = bounds();
        for (Payloads.TradeRouteShrineData shrine : choices()) {
            Point point = pointFor(shrine, bounds, left, top);
            if (Math.abs(mouseX - point.x) <= 12 && Math.abs(mouseY - point.y) <= 12) {
                selectedIndex = shrine.index();
                return true;
            }
        }
        if (within(mouseX, mouseY,
                left + TRAVEL_X, top + FOOTER_Y, TRAVEL_WIDTH, FOOTER_HEIGHT)
                && selected() != null && cooldownSeconds() <= 0
                && (useCharge ? data.charges() >= selected().chargeCost() : data.balance() >= selected().cost())) {
            ClientPlayNetworking.send(new VillageNetworkPayloads.WayshrineTravelPayload(
                    data.currentIndex(), selectedIndex, useCharge && data.charges() >= selected().chargeCost()));
            onClose();
            return true;
        }
        if (within(mouseX, mouseY,
                left + PAYMENT_X, top + FOOTER_Y, PAYMENT_WIDTH, FOOTER_HEIGHT)) {
            useCharge = !useCharge && selected() != null && data.charges() >= selected().chargeCost();
            return true;
        }
        if (within(mouseX, mouseY,
                left + RENAME_X, top + FOOTER_Y, RENAME_WIDTH, FOOTER_HEIGHT)
                && current() != null && data.owner()) {
            openRename();
            return true;
        }
        if (within(mouseX, mouseY, left + MAP_X, top + MAP_Y, MAP_WIDTH, MAP_HEIGHT)) {
            mapDragging = true;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private List<Component> destinationTooltip(Payloads.TradeRouteShrineData shrine) {
        return List.of(
                shrine.name(),
                Component.translatable("screen.village-quest.wayshrine.map_coordinates",
                        shrine.worldX(), shrine.worldZ()),
                Component.translatable("screen.village-quest.wayshrine.cost", shrine.cost()),
                Component.translatable("screen.village-quest.wayshrine.charge_cost", shrine.chargeCost()),
                Component.translatable("screen.village-quest.wayshrine.result_cooldown", shrine.cooldownSeconds() / 60),
                Component.translatable("screen.village-quest.wayshrine.tier." + switch (shrine.bondTier()) {
                    case 0 -> "known";
                    case 2 -> "allied";
                    default -> "trusted";
                })
        );
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (mapDragging && click.button() == 0) {
            MapBounds bounds = bounds();
            centerX -= responsiveDrag(dragX, WINDOW_WIDTH, WINDOW_HEIGHT)
                    * (bounds.maxX - bounds.minX) / MAP_WIDTH;
            centerZ -= responsiveDrag(dragY, WINDOW_WIDTH, WINDOW_HEIGHT)
                    * (bounds.maxZ - bounds.minZ) / MAP_HEIGHT;
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && mapDragging) {
            mapDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int uiX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if (within(uiX, uiY, left + MAP_X, top + MAP_Y, MAP_WIDTH, MAP_HEIGHT)) {
            if (verticalAmount != 0.0) {
                setZoom(zoomLevel + (verticalAmount > 0.0 ? 1 : -1));
                return true;
            }
        }
        if (within(uiX, uiY, left + LIST_X, top + LIST_Y, LIST_WIDTH, VISIBLE_ROWS * ROW_HEIGHT)) {
            int direction = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll + direction));
            return direction != 0;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void openRename() {
        if (minecraft == null || current() == null) return;
        renaming = true;
        nameField.setVisible(true);
        nameField.setValue(current().name().getString());
        nameField.setFocused(true);
        nameField.moveCursorToEnd(false);
        setInitialFocus(nameField);
    }

    private void saveRename() {
        if (nameField == null || nameField.getValue().trim().isEmpty()) return;
        ClientPlayNetworking.send(new VillageNetworkPayloads.WayshrineRenamePayload(data.currentIndex(), nameField.getValue()));
        closeRename();
    }

    private void closeRename() {
        renaming = false;
        if (nameField != null) {
            nameField.setFocused(false);
            nameField.setVisible(false);
        }
    }

    @Override
    protected boolean suppressInventoryKeyClose() {
        return renaming;
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (renaming && key.key() == 257) {
            saveRename();
            return true;
        }
        if (renaming && key.key() == 256) {
            closeRename();
            return true;
        }
        return super.keyPressed(key);
    }

    private void resetMapCenter() {
        resetMapCenter(false);
    }

    /** Creates render-only block entities for shrines placed by older unreleased builds. */
    private void ensureWayshrineRenderers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        for (Payloads.TradeRouteShrineData shrine : data.destinations()) {
            BlockPos pos = new BlockPos(shrine.worldX(), shrine.worldY(), shrine.worldZ());
            if (client.level.isLoaded(pos)
                    && client.level.getBlockState(pos).is(ModBlocks.GUILD_WAYSHRINE)) {
                client.level.getBlockEntity(pos);
            }
        }
    }

    private void resetMapCenter(boolean ignoredAnimation) {
        if (data.destinations().isEmpty()) return;
        int minX = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldX).min().orElse(0);
        int maxX = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldX).max().orElse(0);
        int minZ = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldZ).min().orElse(0);
        int maxZ = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldZ).max().orElse(0);
        centerX = (minX + maxX) / 2.0;
        centerZ = (minZ + maxZ) / 2.0;
        zoomLevel = DEFAULT_ZOOM_LEVEL;
        SurfaceMapRenderer.invalidateScreen();
    }

    private void focusMapOn(Payloads.TradeRouteShrineData shrine) {
        centerX = shrine.worldX();
        centerZ = shrine.worldZ();
        SurfaceMapRenderer.invalidateScreen();
    }

    private boolean handleMapControlClick(int mouseX, int mouseY, int left, int top) {
        for (int i = 0; i < 3; i++) {
            int x = left + ZOOM_X;
            int y = top + ZOOM_Y + i * (CONTROL_SIZE + CONTROL_GAP);
            if (!within(mouseX, mouseY, x, y, CONTROL_SIZE, CONTROL_SIZE)) continue;
            if (i == 0) {
                setZoom(zoomLevel + 1);
            } else if (i == 1) {
                setZoom(zoomLevel - 1);
            } else {
                resetMapCenter(true);
            }
            return true;
        }
        return false;
    }

    private void setZoom(int value) {
        int next = Math.max(0, Math.min(ZOOM_FACTORS.length - 1, value));
        if (next == zoomLevel) return;
        zoomLevel = next;
        SurfaceMapRenderer.invalidateScreen();
    }

    private MapBounds bounds() {
        MapBounds base = networkBounds();
        double factor = ZOOM_FACTORS[zoomLevel];
        int halfWidth = Math.max(64, (int) Math.round((base.maxX - base.minX) * factor / 2.0));
        int halfHeight = Math.max(48, (int) Math.round((base.maxZ - base.minZ) * factor / 2.0));
        int minX = (int) Math.floor(centerX - halfWidth);
        int minZ = (int) Math.floor(centerZ - halfHeight);
        return new MapBounds(minX, minX + halfWidth * 2, minZ, minZ + halfHeight * 2);
    }

    private MapBounds networkBounds() {
        if (data.destinations().isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            int x = client.player == null ? 0 : client.player.getBlockX();
            int z = client.player == null ? 0 : client.player.getBlockZ();
            return new MapBounds(x - 192, x + 192, z - 128, z + 128);
        }
        int minX = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldX).min().orElse(0);
        int maxX = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldX).max().orElse(0);
        int minZ = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldZ).min().orElse(0);
        int maxZ = data.destinations().stream().mapToInt(Payloads.TradeRouteShrineData::worldZ).max().orElse(0);
        int paddingX = Math.max(72, (maxX - minX) / 8);
        int paddingZ = Math.max(54, (maxZ - minZ) / 8);
        return new MapBounds(minX - paddingX, maxX + paddingX, minZ - paddingZ, maxZ + paddingZ);
    }

    private Point pointFor(Payloads.TradeRouteShrineData shrine, MapBounds bounds, int left, int top) {
        int x = left + MAP_X + (int) Math.round((shrine.worldX() - bounds.minX)
                / (double) Math.max(1, bounds.maxX - bounds.minX) * MAP_WIDTH);
        int y = top + MAP_Y + (int) Math.round((shrine.worldZ() - bounds.minZ)
                / (double) Math.max(1, bounds.maxZ - bounds.minZ) * MAP_HEIGHT);
        return new Point(x, y);
    }

    private void drawDashedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            if ((i / 4) % 2 != 0) continue;
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private int distanceTo(Payloads.TradeRouteShrineData target) {
        Payloads.TradeRouteShrineData current = current();
        if (current == null) return 0;
        double dx = target.worldX() - current.worldX();
        double dz = target.worldZ() - current.worldZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private int cooldownSeconds() {
        long elapsed = Math.max(0L, (System.currentTimeMillis() - cooldownObservedAtMillis) / 1000L);
        return Math.max(0, data.cooldownSeconds() - (int) elapsed);
    }

    private List<Payloads.TradeRouteShrineData> choices() {
        return data.destinations().stream().filter(shrine -> !shrine.current()).toList();
    }

    private List<Payloads.TradeRouteShrineData> visibleChoices() {
        List<Payloads.TradeRouteShrineData> choices = choices();
        int from = Math.min(listScroll, choices.size());
        int to = Math.min(choices.size(), from + VISIBLE_ROWS);
        return choices.subList(from, to);
    }

    private int maxListScroll() {
        return Math.max(0, choices().size() - VISIBLE_ROWS);
    }

    private Payloads.TradeRouteShrineData current() {
        return data.destinations().stream().filter(Payloads.TradeRouteShrineData::current).findFirst().orElse(null);
    }

    private Payloads.TradeRouteShrineData selected() {
        return choices().stream().filter(value -> value.index() == selectedIndex).findFirst().orElse(null);
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private record MapBounds(int minX, int maxX, int minZ, int maxZ) {}
    private record Point(int x, int y) {}
}
