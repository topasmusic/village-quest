package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A painted, explorable overview of Village Quest's permanent progression.
 * The authored map stays free of words and status symbols; localized labels,
 * item previews and player-specific progression are rendered on top.
 */
public final class GuildPathScreen extends CompatScreen {
    private static final Identifier BOARD = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/journal_board.png");
    private static final Identifier MAP_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_path_map.png");
    private static final int WIDTH = 416;
    private static final int HEIGHT = 234;
    private static final int MAP_TEXTURE_WIDTH = 1774;
    private static final int MAP_TEXTURE_HEIGHT = 887;
    private static final int MAP_X = 10;
    private static final int MAP_Y = 25;
    private static final int MAP_WIDTH = 396;
    private static final int MAP_HEIGHT = 193;
    private static final int BASE_MAP_WIDTH = 780;
    private static final int BASE_MAP_HEIGHT = 390;
    private static final int MARKER_SIZE = 22;
    private static final int FRAME_TOP_HEIGHT = 34;
    private static final int FRAME_BOTTOM_Y = 214;
    private static final int FRAME_SIDE_WIDTH = 16;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF8A7661;
    private static final int TEAL = 0xFF236B68;
    private static final int GOLD = 0xFFB6812C;
    private static final int PARCHMENT = 0xFFE4C588;
    private static final Map<String, Landmark> LANDMARKS = Map.ofEntries(
            Map.entry("ledger", new Landmark(0.124f, 0.637f)),
            Map.entry("surveyor_compass", new Landmark(0.141f, 0.282f)),
            Map.entry("starreach_ring", new Landmark(0.324f, 0.829f)),
            Map.entry("merchant_seal", new Landmark(0.395f, 0.355f)),
            Map.entry("shepherd_flute", new Landmark(0.440f, 0.609f)),
            Map.entry("apiarist_smoker", new Landmark(0.569f, 0.767f)),
            Map.entry("lens", new Landmark(0.623f, 0.225f)),
            Map.entry("sigil", new Landmark(0.705f, 0.338f)),
            Map.entry("wayshrine", new Landmark(0.862f, 0.248f)),
            Map.entry("notice_board", new Landmark(0.772f, 0.592f)),
            Map.entry("courier_satchel", new Landmark(0.896f, 0.789f))
    );

    private Payloads.GuildPathPayload data;
    private int selected = -1;
    private double mapOffsetX;
    private double mapOffsetY;
    private boolean mapInitialized;
    private boolean mapDragging;
    private boolean mapDragged;
    private double dragDistance;
    private int pressedNode = -1;
    private boolean sessionClosed;

    public GuildPathScreen(Payloads.GuildPathPayload data) {
        super(Component.translatable("screen.village-quest.guild_path.title"));
        this.data = data;
    }

    public void updateData(Payloads.GuildPathPayload data) {
        this.data = data;
        this.selected = -1;
        clampMapOffset();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean keyPressed(KeyEvent key) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key)) {
            onClose();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiX = responsiveMouseX(mouseX, WIDTH, HEIGHT);
        int uiY = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            if (!mapInitialized) {
                centerOnIndex(firstCurrent(data));
                mapInitialized = true;
            }
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD, left, top, 0, 0,
                    WIDTH, HEIGHT, WIDTH, HEIGHT);
            drawMap(graphics, left, top, uiX, uiY);
            drawFrameOverlay(graphics, left, top);
            drawHeader(graphics, left, top);
            drawMapControls(graphics, left + MAP_X, top + MAP_Y, uiX, uiY);
            drawDetail(graphics, left + MAP_X, top + MAP_Y);
            drawFooter(graphics, left, top, uiX, uiY);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        int titleX = left + (WIDTH - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, top + 14, INK, false);
    }

    private void drawMap(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int mapX = left + MAP_X;
        int mapY = top + MAP_Y;
        int renderWidth = renderMapWidth();
        int renderHeight = renderMapHeight();
        int drawX = mapX + (int) Math.round(mapOffsetX);
        int drawY = mapY + (int) Math.round(mapOffsetY);

        graphics.enableScissor(mapX, mapY, mapX + MAP_WIDTH, mapY + MAP_HEIGHT);
        graphics.fill(mapX, mapY, mapX + MAP_WIDTH, mapY + MAP_HEIGHT, 0xFFB9955C);
        VillageUiTheme.blitScaled(graphics, MAP_TEXTURE, drawX, drawY,
                renderWidth, renderHeight, MAP_TEXTURE_WIDTH, MAP_TEXTURE_HEIGHT);

        int hovered = nodeAt(mouseX, mouseY, left, top);
        for (int index = 0; index < data.nodes().size(); index++) {
            Payloads.GuildPathNodeData node = data.nodes().get(index);
            Landmark landmark = landmark(node.nodeId(), index);
            int centerX = drawX + Math.round(landmark.x() * renderWidth);
            int centerY = drawY + Math.round(landmark.y() * renderHeight);
            if (centerX < mapX - MARKER_SIZE || centerX > mapX + MAP_WIDTH + MARKER_SIZE
                    || centerY < mapY - MARKER_SIZE || centerY > mapY + MAP_HEIGHT + MARKER_SIZE) {
                continue;
            }
            drawMarker(graphics, node, centerX, centerY, index == selected, index == hovered);
        }
        graphics.disableScissor();
    }

    private void drawFrameOverlay(GuiGraphics graphics, int left, int top) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD, left, top, 0, 0,
                WIDTH, FRAME_TOP_HEIGHT, WIDTH, HEIGHT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD, left, top + FRAME_BOTTOM_Y,
                0, FRAME_BOTTOM_Y, WIDTH, HEIGHT - FRAME_BOTTOM_Y, WIDTH, HEIGHT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD, left, top + FRAME_TOP_HEIGHT,
                0, FRAME_TOP_HEIGHT, FRAME_SIDE_WIDTH, FRAME_BOTTOM_Y - FRAME_TOP_HEIGHT, WIDTH, HEIGHT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD, left + WIDTH - FRAME_SIDE_WIDTH,
                top + FRAME_TOP_HEIGHT, WIDTH - FRAME_SIDE_WIDTH, FRAME_TOP_HEIGHT,
                FRAME_SIDE_WIDTH, FRAME_BOTTOM_Y - FRAME_TOP_HEIGHT, WIDTH, HEIGHT);
    }

    private void drawMarker(GuiGraphics graphics, Payloads.GuildPathNodeData node,
                            int centerX, int centerY, boolean selectedNode, boolean hovered) {
        int half = MARKER_SIZE / 2;
        int border = node.status() == 2 ? TEAL : node.status() == 1 ? GOLD : 0xFF766B5E;
        if (node.status() == 1) {
            long phase = (System.currentTimeMillis() / 280L) % 4L;
            int pulse = phase == 0L || phase == 3L ? 2 : 1;
            graphics.fill(centerX - half - pulse, centerY - half - pulse,
                    centerX + half + pulse, centerY + half + pulse, 0x559D6D22);
        }
        graphics.fill(centerX - half + 1, centerY - half + 2,
                centerX + half + 2, centerY + half + 3, 0x660E0906);
        graphics.fill(centerX - half, centerY - half,
                centerX + half, centerY + half, border);
        graphics.fill(centerX - half + 2, centerY - half + 2,
                centerX + half - 2, centerY + half - 2, PARCHMENT);
        if (selectedNode || hovered) {
            graphics.fill(centerX - half + 2, centerY - half + 2,
                    centerX - half + 5, centerY + half - 2, selectedNode ? TEAL : GOLD);
        }
        drawScaledItem(graphics, node.previewStack(), centerX - 6, centerY - 6, 0.75f);
        if (node.status() == 0) {
            graphics.fill(centerX - half + 2, centerY - half + 2,
                    centerX + half - 2, centerY + half - 2, 0x77493E34);
            drawLock(graphics, centerX + 2, centerY - 7);
        } else if (node.status() == 2) {
            drawCheck(graphics, centerX + 3, centerY + 3);
        }
        if (hovered) drawMarkerLabel(graphics, node.title().getString(), centerX, centerY - half - 4);
    }

    private void drawMarkerLabel(GuiGraphics graphics, String label, int centerX, int bottomY) {
        float scale = 0.62f;
        String visible = VillageUiTheme.ellipsize(font, label, 116);
        int textWidth = Math.round(font.width(visible) * scale);
        int width = textWidth + 8;
        int x = centerX - width / 2;
        int y = bottomY - 11;
        graphics.fill(x, y, x + width, y + 10, 0xEEDFC08A);
        graphics.fill(x, y, x + width, y + 1, 0xFF76512A);
        graphics.fill(x, y + 9, x + width, y + 10, 0xFF76512A);
        VillageUiTheme.drawStringScaled(graphics, font, visible, x + 4, y + 2, INK, scale);
    }

    private void drawMapControls(GuiGraphics graphics, int mapX, int mapY, int mouseX, int mouseY) {
        boolean centerHover = within(mouseX, mouseY, mapX + 6, mapY + 12, 58, 16);
        VillageUiTheme.drawButton(graphics, font, mapX + 6, mapY + 12, 58, 16,
                Component.translatable("screen.village-quest.guild_path.map.center").getString(),
                true, centerHover, false);
    }

    private void drawDetail(GuiGraphics graphics, int mapX, int mapY) {
        Payloads.GuildPathNodeData node = selectedNode();
        if (node == null) return;
        int width = 154;
        int height = 92;
        int x = mapX + MAP_WIDTH - width - 5;
        int y = mapY + MAP_HEIGHT - height - 5;
        VillageUiTheme.drawCard(graphics, x, y, width, height, true, false);
        drawScaledItem(graphics, node.previewStack(), x + 9, y + 8, 0.78f);
        VillageUiTheme.drawWrappedScaled(graphics, font, node.title().getString(),
                x + 29, y + 8, width - 38, INK, 0.67f, 2);
        Component status = Component.translatable("screen.village-quest.guild_path.node.status." + switch (node.status()) {
            case 2 -> "complete";
            case 1 -> "current";
            default -> "locked";
        });
        VillageUiTheme.drawStringScaled(graphics, font, status.getString(), x + 9, y + 25,
                node.status() == 2 ? TEAL : node.status() == 1 ? GOLD : MUTED, 0.58f);
        graphics.fill(x + 9, y + 35, x + width - 9, y + 36, 0xFFB89A70);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.guild_path.ability").getString(),
                x + 9, y + 40, GOLD, 0.57f);
        VillageUiTheme.drawWrappedScaled(graphics, font, node.ability().getString(),
                x + 9, y + 49, width - 18, BODY, 0.52f, 2);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.guild_path.requirement").getString(),
                x + 9, y + 67, GOLD, 0.57f);
        VillageUiTheme.drawWrappedScaled(graphics, font, node.requirement().getString(),
                x + 9, y + 76, width - 18, MUTED, 0.49f, 2);
    }

    private void drawFooter(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        graphics.fill(left + 19, top + 198, left + 112, top + 210, 0xAA2B1A10);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.guild_path.map.hint").getString(),
                left + 23, top + 201, 0xFFF1D29A, 0.58f);
        boolean closeHover = within(mouseX, mouseY, left + 326, top + 195, 70, 18);
        VillageUiTheme.drawButton(graphics, font, left + 326, top + 195, 70, 18,
                Component.translatable("screen.village-quest.guild_path.close").getString(),
                true, closeHover, false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WIDTH, HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        int mapX = left + MAP_X;
        int mapY = top + MAP_Y;
        if (within(mouseX, mouseY, left + 326, top + 195, 70, 18)) {
            onClose();
            return true;
        }
        if (within(mouseX, mouseY, mapX + 6, mapY + 12, 58, 16)) {
            selected = -1;
            centerOnIndex(firstCurrent(data));
            return true;
        }
        if (within(mouseX, mouseY, mapX, mapY, MAP_WIDTH, MAP_HEIGHT)) {
            selected = -1;
            mapDragging = true;
            mapDragged = false;
            dragDistance = 0.0;
            pressedNode = nodeAt(mouseX, mouseY, left, top);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (mapDragging && click.button() == 0) {
            double adjustedX = responsiveDrag(dragX, WIDTH, HEIGHT);
            double adjustedY = responsiveDrag(dragY, WIDTH, HEIGHT);
            mapOffsetX += adjustedX;
            mapOffsetY += adjustedY;
            dragDistance += Math.abs(adjustedX) + Math.abs(adjustedY);
            if (dragDistance > 2.0) mapDragged = true;
            clampMapOffset();
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && mapDragging) {
            mapDragging = false;
            if (!mapDragged && pressedNode >= 0 && pressedNode < data.nodes().size()) selected = pressedNode;
            pressedNode = -1;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override public void mouseMoved(double mouseX, double mouseY) {
        if (selected >= 0) selected = -1;
        super.mouseMoved(mouseX, mouseY);
    }

    private void centerOnIndex(int index) {
        Payloads.GuildPathNodeData node = index < 0 || index >= data.nodes().size()
                ? null : data.nodes().get(index);
        Landmark landmark = node == null ? new Landmark(0.5f, 0.5f) : landmark(node.nodeId(), index);
        mapOffsetX = MAP_WIDTH / 2.0 - landmark.x() * renderMapWidth();
        mapOffsetY = MAP_HEIGHT / 2.0 - landmark.y() * renderMapHeight();
        clampMapOffset();
    }

    private void clampMapOffset() {
        mapOffsetX = Math.max(MAP_WIDTH - renderMapWidth(), Math.min(0.0, mapOffsetX));
        mapOffsetY = Math.max(MAP_HEIGHT - renderMapHeight(), Math.min(0.0, mapOffsetY));
    }

    private int nodeAt(double mouseX, double mouseY, int left, int top) {
        int mapX = left + MAP_X;
        int mapY = top + MAP_Y;
        if (!within(mouseX, mouseY, mapX, mapY, MAP_WIDTH, MAP_HEIGHT)) return -1;
        int drawX = mapX + (int) Math.round(mapOffsetX);
        int drawY = mapY + (int) Math.round(mapOffsetY);
        int renderWidth = renderMapWidth();
        int renderHeight = renderMapHeight();
        for (int index = data.nodes().size() - 1; index >= 0; index--) {
            Landmark landmark = landmark(data.nodes().get(index).nodeId(), index);
            int centerX = drawX + Math.round(landmark.x() * renderWidth);
            int centerY = drawY + Math.round(landmark.y() * renderHeight);
            if (within(mouseX, mouseY, centerX - MARKER_SIZE / 2, centerY - MARKER_SIZE / 2,
                    MARKER_SIZE, MARKER_SIZE)) return index;
        }
        return -1;
    }

    @Override public void onClose() {
        if (!sessionClosed && ClientPlayNetworking.canSend(Payloads.QuestMasterSessionPayload.ID)) {
            sessionClosed = true;
            ClientPlayNetworking.send(new Payloads.QuestMasterSessionPayload(
                    data.questMasterEntityId(), Payloads.QuestMasterSessionPayload.ACTION_CLOSE));
        }
        super.onClose();
    }

    private int renderMapWidth() { return BASE_MAP_WIDTH; }
    private int renderMapHeight() { return BASE_MAP_HEIGHT; }

    private Payloads.GuildPathNodeData selectedNode() {
        return selected < 0 || selected >= data.nodes().size() ? null : data.nodes().get(selected);
    }

    private static Landmark landmark(String id, int fallbackIndex) {
        Landmark position = LANDMARKS.get(id);
        if (position != null) return position;
        float progress = Math.max(0.0f, Math.min(1.0f, fallbackIndex / 10.0f));
        return new Landmark(0.08f + progress * 0.84f, 0.5f);
    }

    private static int firstCurrent(Payloads.GuildPathPayload data) {
        for (int i = 0; i < data.nodes().size(); i++) if (data.nodes().get(i).status() == 1) return i;
        for (int i = data.nodes().size() - 1; i >= 0; i--) if (data.nodes().get(i).status() == 2) return i;
        return 0;
    }

    private static void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) return;
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        graphics.renderItem(stack, 0, 0);
        matrices.popMatrix();
    }

    private static void drawLock(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 4, x + 7, y + 10, 0xFFD9A83B);
        graphics.fill(x + 1, y + 1, x + 2, y + 5, 0xFFD9A83B);
        graphics.fill(x + 5, y + 1, x + 6, y + 5, 0xFFD9A83B);
        graphics.fill(x + 2, y, x + 5, y + 1, 0xFFD9A83B);
        graphics.fill(x + 3, y + 6, x + 4, y + 9, 0xFF5A3518);
    }

    private static void drawCheck(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 2, x + 2, y + 4, 0xFFEBF6D2);
        graphics.fill(x + 2, y + 3, x + 4, y + 5, 0xFFEBF6D2);
        graphics.fill(x + 4, y, x + 6, y + 4, 0xFFEBF6D2);
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private record Landmark(float x, float y) {}
}
