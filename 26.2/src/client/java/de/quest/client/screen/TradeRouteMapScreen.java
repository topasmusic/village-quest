package de.quest.client.screen;

import de.quest.client.config.VillageQuestClientConfig;
import de.quest.VillageQuest;
import de.quest.client.ui.SurfaceMapRenderer;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Full-screen guild map with route, village-bond, and guide views. */
public final class TradeRouteMapScreen extends CompatScreen {
    private enum ViewMode { MAP, ROUTES, BONDS, GUIDE }

    private static final Identifier BOARD_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/trade_route_board.png");
    private static final int WINDOW_WIDTH = 432;
    private static final int WINDOW_HEIGHT = 248;
    private static final int VIEW_X = 31;
    private static final int VIEW_Y = 43;
    private static final int VIEW_WIDTH = 370;
    private static final int VIEW_HEIGHT = 159;
    private static final int TAB_X = 25;
    private static final int TAB_Y = 10;
    private static final int TAB_SIZE = 22;
    private static final int TAB_GAP = 3;
    private static final int ZOOM_X = VIEW_X + VIEW_WIDTH - 25;
    private static final int ZOOM_Y = VIEW_Y + 34;
    private static final int CONTROL_SIZE = 20;
    private static final int CONTROL_GAP = 3;
    private static final int FOOTER_Y = 204;
    private static final int CHIP_WIDTH = 23;
    private static final int CHIP_HEIGHT = 17;
    private static final int FRAME = 0xFF4F321B;
    private static final int PAPER = 0xFFF5E7C7;
    private static final int INK = 0xFF3E2918;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF80694F;
    private static final int DANGEROUS = 0xFFB9573E;
    private static final int SECURED = 0xFF7E8A55;
    private static final int FLOURISHING = 0xFF3E927B;
    private static final int[] ROUTE_COLORS = {
            0xFFB9574E, 0xFF3E927B, 0xFF4E78A8, 0xFFB78936, 0xFF7B609D
    };
    private static final int ROAD_SHADOW = 0xFF604A31;
    private static final int ROAD = 0xFFD2B275;
    private static final int FERRY_SHADOW = 0xFF31515A;
    private static final int FERRY_ROUTE = 0xFF6EB7C4;
    private static final double[] ZOOM_FACTORS = {
            24.0, 16.0, 10.0, 6.0, 3.5, 2.0, 1.0, 0.70, 0.48, 0.32
    };
    private static final String[] ZOOM_LABELS = {
            "4%", "6%", "10%", "17%", "29%", "50%", "100%", "140%", "210%", "310%"
    };
    private static final int DEFAULT_ZOOM_LEVEL = 6;

    private Payloads.TradeRouteMapPayload data;
    private ViewMode viewMode = ViewMode.MAP;
    private int selectedRoute;
    private int zoomLevel = DEFAULT_ZOOM_LEVEL;
    private double centerX;
    private double centerZ;
    private boolean centerInitialized;
    private boolean closeNotified;
    private boolean mapDragging;
    private int pendingRemovalRoute = -1;
    private long removalConfirmUntil;

    public TradeRouteMapScreen(Payloads.TradeRouteMapPayload data) {
        super(Component.translatable("screen.village-quest.trade_route.title"));
        this.data = data;
        this.selectedRoute = data.routes().isEmpty() ? -1 : data.routes().getFirst().routeIndex();
    }

    public void updateData(Payloads.TradeRouteMapPayload data) {
        this.data = data;
        Payloads.TradeRouteLineData surveying = data.routes().stream()
                .filter(Payloads.TradeRouteLineData::surveying).findFirst().orElse(null);
        if (surveying != null) {
            selectedRoute = surveying.routeIndex();
        }
        if (data.routes().stream().noneMatch(route -> route.routeIndex() == selectedRoute)) {
            selectedRoute = data.routes().isEmpty() ? -1 : data.routes().getFirst().routeIndex();
            pendingRemovalRoute = -1;
        }
        if (!centerInitialized) {
            resetMapCenter(false);
        }
    }

    @Override
    protected void init() {
        closeNotified = false;
        pendingRemovalRoute = -1;
        if (!centerInitialized) {
            resetMapCenter(false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        returnToJournal();
    }

    private void returnToJournal() {
        notifyClosed();
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("vq journal open");
            return;
        }
        super.onClose();
    }

    private void closeToGame() {
        notifyClosed();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        float panelScale = beginResponsivePanel(graphics, WINDOW_WIDTH, WINDOW_HEIGHT);
        try {
            int left = (width - WINDOW_WIDTH) / 2;
            int top = (height - WINDOW_HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD_TEXTURE, left, top, 0.0f, 0.0f,
                    WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
            drawTabs(graphics, left, top, uiMouseX, uiMouseY);
            drawHeader(graphics, left, top);
            switch (viewMode) {
                case MAP -> drawMapView(graphics, left, top, uiMouseX, uiMouseY);
                case ROUTES -> drawRoutesView(graphics, left, top, uiMouseX, uiMouseY);
                case BONDS -> drawBondsView(graphics, left, top, uiMouseX, uiMouseY);
                case GUIDE -> drawGuideView(graphics, left, top);
            }
            super.render(graphics, uiMouseX, uiMouseY, delta);
        } finally {
            endResponsivePanel(graphics, panelScale);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WINDOW_WIDTH, WINDOW_HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WINDOW_WIDTH, WINDOW_HEIGHT);
        for (int i = 0; i < ViewMode.values().length; i++) {
            int x = left + TAB_X + i * (TAB_SIZE + TAB_GAP);
            if (within(mouseX, mouseY, x, top + TAB_Y, TAB_SIZE, TAB_SIZE)) {
                mapDragging = false;
                viewMode = ViewMode.values()[i];
                return true;
            }
        }
        return switch (viewMode) {
            case MAP -> {
                if (handleMapClick(mouseX, mouseY, left, top)) {
                    yield true;
                }
                if (within(mouseX, mouseY, left + VIEW_X, top + VIEW_Y,
                        VIEW_WIDTH, VIEW_HEIGHT)) {
                    mapDragging = true;
                    yield true;
                }
                yield super.mouseClicked(click, doubled);
            }
            case ROUTES -> handleRoutesClick(mouseX, mouseY, left, top) || super.mouseClicked(click, doubled);
            case BONDS -> super.mouseClicked(click, doubled);
            case GUIDE -> super.mouseClicked(click, doubled);
        };
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (mapDragging && click.button() == 0 && viewMode == ViewMode.MAP) {
            Bounds bounds = displayBounds();
            centerX -= responsiveDrag(dragX, WINDOW_WIDTH, WINDOW_HEIGHT)
                    * (bounds.maxX - bounds.minX) / VIEW_WIDTH;
            centerZ -= responsiveDrag(dragY, WINDOW_WIDTH, WINDOW_HEIGHT)
                    * (bounds.maxZ - bounds.minZ) / VIEW_HEIGHT;
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
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if (viewMode == ViewMode.MAP && within(uiMouseX, uiMouseY,
                left + VIEW_X, top + VIEW_Y, VIEW_WIDTH, VIEW_HEIGHT)) {
            setZoom(zoomLevel + (verticalAmount > 0.0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String[] icons = {"home", "quests", "trust", "guide"};
        String[] labels = {
                "screen.village-quest.trade_route.tab.map",
                "screen.village-quest.trade_route.tab.routes",
                "screen.village-quest.trade_route.tab.bonds",
                "screen.village-quest.trade_route.tab.guide"
        };
        for (int i = 0; i < icons.length; i++) {
            int x = left + TAB_X + i * (TAB_SIZE + TAB_GAP);
            int y = top + TAB_Y;
            boolean hovered = within(mouseX, mouseY, x, y, TAB_SIZE, TAB_SIZE);
            VillageUiTheme.drawTab(graphics, x, y, TAB_SIZE, TAB_SIZE,
                    viewMode.ordinal() == i, hovered);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon(icons[i]), x + 4, y + 4, 14);
            if (hovered) {
                graphics.setTooltipForNextFrame(font, Component.translatable(labels[i]), mouseX, mouseY);
            }
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        String key = switch (viewMode) {
            case MAP -> "screen.village-quest.trade_route.title";
            case ROUTES -> "screen.village-quest.trade_route.manage_title";
            case BONDS -> "screen.village-quest.trade_route.bonds_title";
            case GUIDE -> "screen.village-quest.trade_route.guide_title";
        };
        String value = Component.translatable(key).getString();
        graphics.drawString(font, value, left + (WINDOW_WIDTH - font.width(value)) / 2,
                top + 14, INK, false);
    }

    private void drawMapView(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int mapX = left + VIEW_X;
        int mapY = top + VIEW_Y;
        Bounds bounds = displayBounds();
        graphics.enableScissor(mapX, mapY, mapX + VIEW_WIDTH, mapY + VIEW_HEIGHT);
        SurfaceMapRenderer.drawScreen(graphics, mapX, mapY, VIEW_WIDTH, VIEW_HEIGHT,
                bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ, !mapDragging);
        drawRoutesOnMap(graphics, bounds, left, top, mouseX, mouseY);
        graphics.disableScissor();
        drawMapControls(graphics, left, top, mouseX, mouseY);
        drawMapFooter(graphics, left, top, mouseX, mouseY);
    }

    private void drawRoutesOnMap(GuiGraphics graphics, Bounds bounds, int left, int top,
                                 int mouseX, int mouseY) {
        VillageQuestClientConfig config = VillageQuestClientConfig.get();
        Payloads.TradeRouteNodeData home = data.nodes().stream()
                .filter(Payloads.TradeRouteNodeData::home).findFirst().orElse(null);
        if (home == null) {
            drawShrineMarkers(graphics, bounds, left, top, mouseX, mouseY);
            drawPlayerMarker(graphics, bounds, left, top, mouseX, mouseY, config);
            return;
        }
        for (Payloads.TradeRouteLineData route : sortedRoutes()) {
            Payloads.TradeRouteNodeData destination = node(route.routeIndex() + 1);
            if (destination == null) {
                continue;
            }
            List<WorldPoint> path = routePath(home, destination, route);
            if (config.showRouteLines()) {
                for (int i = 1; i < path.size(); i++) {
                    Point from = pointFor(path.get(i - 1).x, path.get(i - 1).z, bounds, left, top, false);
                    Point to = pointFor(path.get(i).x, path.get(i).z, bounds, left, top, false);
                    boolean ferry = path.get(i - 1).ocean || path.get(i).ocean;
                    if (ferry) {
                        drawDashedLine(graphics, from.x, from.y, to.x, to.y, FERRY_SHADOW,
                                route.routeIndex() == selectedRoute ? 4 : 3, 6, 3);
                        drawDashedLine(graphics, from.x, from.y, to.x, to.y, FERRY_ROUTE,
                                route.routeIndex() == selectedRoute ? 2 : 1, 6, 3);
                    } else {
                        drawLine(graphics, from.x, from.y, to.x, to.y, ROAD_SHADOW,
                                route.routeIndex() == selectedRoute ? 4 : 3);
                        drawLine(graphics, from.x, from.y, to.x, to.y, ROAD,
                                route.routeIndex() == selectedRoute ? 3 : 2);
                        drawLine(graphics, from.x, from.y, to.x, to.y,
                                routeColorByIndex(route.liveryIndex()), route.routeIndex() == selectedRoute ? 2 : 1);
                    }
                }
            }
            if (route.routeIndex() == selectedRoute) {
                for (Payloads.TradeRoutePointData waypoint : route.waypoints()) {
                    Point point = pointFor(waypoint.worldX(), waypoint.worldZ(), bounds, left, top, false);
                    VillageUiTheme.drawMarker(graphics, waypoint.ocean() ? "ferry" : "waypoint",
                            point.x, point.y, waypoint.ocean() ? 13 : 11);
                    if (Math.abs(mouseX - point.x) <= 7 && Math.abs(mouseY - point.y) <= 7) {
                        List<Component> tooltip = new ArrayList<>();
                        if (waypoint.ocean()) {
                            tooltip.add(Component.translatable(
                                    "screen.village-quest.trade_route.ferry_waypoint"));
                        }
                        tooltip.add(Component.literal("[" + waypoint.worldX() + ", " + waypoint.worldZ() + "]"));
                        graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                    }
                }
            }
            Payloads.TradeRouteCaravanData caravan = data.caravans().stream()
                    .filter(value -> value.routeIndex() == route.routeIndex()).findFirst().orElse(null);
            if (caravan != null && config.showCaravanMarkers()) {
                WorldPoint world = pointAlong(path, caravan.progress());
                Point point = pointFor(world.x, world.z, bounds, left, top, false);
                VillageUiTheme.drawMarker(graphics,
                        caravan.ferry() || caravan.boarding() ? "ferry"
                                : route.eventLabel().getString().isEmpty() ? "caravan" : "danger",
                        point.x, point.y, route.routeIndex() == selectedRoute ? 22 : 18);
                if (Math.abs(mouseX - point.x) <= 11 && Math.abs(mouseY - point.y) <= 11) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(route.name());
                    tooltip.add(Component.translatable("screen.village-quest.trade_route.caravan_coordinates",
                            (int) Math.round(world.x), (int) Math.round(world.z)));
                    tooltip.add(caravan.boarding()
                            ? Component.translatable("screen.village-quest.trade_route.ferry_boarding")
                            : caravan.ferry()
                            ? Component.translatable("screen.village-quest.trade_route.ferry_crossing",
                            formatEta(caravan.ferrySecondsRemaining()))
                            : Component.translatable(caravan.materialized()
                            ? "screen.village-quest.trade_route.caravan_nearby"
                            : "screen.village-quest.trade_route.caravan_simulated"));
                    if (!route.eventLabel().getString().isEmpty()) {
                        tooltip.add(Component.translatable("screen.village-quest.trade_route.event_tooltip",
                                route.eventLabel()));
                    }
                    graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
            }
        }
        if (config.showVillageMarkers()) {
            List<LabelBox> placedLabels = new ArrayList<>();
            for (Payloads.TradeRouteNodeData node : data.nodes()) {
                Point point = pointFor(node.worldX(), node.worldZ(), bounds, left, top, false);
                String marker = node.playerYard() ? "homestead" : node.home() ? "home" : "village";
                VillageUiTheme.drawMarker(graphics, marker,
                        point.x, point.y, node.playerYard() ? 27 : node.home() ? 27 : 22);
                boolean hovered = Math.abs(mouseX - point.x) <= 13 && Math.abs(mouseY - point.y) <= 13;
                if (hovered) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(node.name());
                    tooltip.add(Component.literal("X " + node.worldX() + "  Z " + node.worldZ()));
                    graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                } else {
                    float labelScale = nodeLabelScale();
                    if (labelScale > 0.0f) {
                        int renderedLimit = zoomLevel <= 3 ? 52 : zoomLevel <= 5 ? 64 : 80;
                        String label = compact(node.name().getString(), renderedLimit, labelScale);
                        float labelWidth = font.width(label) * labelScale;
                        float labelX = point.x - labelWidth / 2.0f;
                        float labelY = point.y + (node.home() ? 14 : 12);
                        LabelBox candidate = new LabelBox(labelX - 2.0f, labelY - 1.0f,
                                labelX + labelWidth + 2.0f,
                                labelY + font.lineHeight * labelScale + 1.0f);
                        if (placedLabels.stream().noneMatch(candidate::overlaps)) {
                            VillageUiTheme.drawStringScaled(graphics, font, label,
                                    labelX, labelY, INK, labelScale);
                            placedLabels.add(candidate);
                        }
                    }
                }
            }
        }
        drawShrineMarkers(graphics, bounds, left, top, mouseX, mouseY);
        drawPlayerMarker(graphics, bounds, left, top, mouseX, mouseY, config);
    }

    private void drawShrineMarkers(GuiGraphics graphics, Bounds bounds, int left, int top,
                                   int mouseX, int mouseY) {
        for (Payloads.TradeRouteShrineData shrine : data.shrines()) {
            Point point = pointFor(shrine.worldX(), shrine.worldZ(), bounds, left, top, false);
            VillageUiTheme.drawMarker(graphics, "shrine", point.x, point.y, 19);
            if (Math.abs(mouseX - point.x) <= 9 && Math.abs(mouseY - point.y) <= 9) {
                graphics.setTooltipForNextFrame(font, List.of(shrine.name(), Component.translatable(
                        "screen.village-quest.trade_route.shrine_coordinates",
                        shrine.worldX(), shrine.worldY(), shrine.worldZ())), mouseX, mouseY);
            }
        }
        for (Payloads.TradeRouteDecorationData decoration : data.decorations()) {
            Point point = pointFor(decoration.worldX(), decoration.worldZ(), bounds, left, top, false);
            VillageUiTheme.drawMarker(graphics, decoration.type() == 0 ? "notice" : "milestone",
                    point.x, point.y, decoration.type() == 0 ? 13 : 11);
            if (Math.abs(mouseX - point.x) <= 7 && Math.abs(mouseY - point.y) <= 7) {
                graphics.setTooltipForNextFrame(font, Component.translatable(
                        decoration.type() == 0 ? "screen.village-quest.trade_route.notice_coordinates"
                                : "screen.village-quest.trade_route.milestone_coordinates",
                        decoration.worldX(), decoration.worldY(), decoration.worldZ()), mouseX, mouseY);
            }
        }
    }

    private void drawPlayerMarker(GuiGraphics graphics, Bounds bounds, int left, int top,
                                  int mouseX, int mouseY, VillageQuestClientConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !config.showPlayerMarker()) {
            return;
        }
        Point player = pointFor(client.player.getX(), client.player.getZ(), bounds, left, top, true);
        VillageUiTheme.drawMarker(graphics, "player", player.x, player.y, 21);
        if (Math.abs(mouseX - player.x) <= 11 && Math.abs(mouseY - player.y) <= 11) {
            graphics.setTooltipForNextFrame(font,
                    Component.translatable("screen.village-quest.trade_route.player_coordinates",
                            client.player.getBlockX(), client.player.getBlockZ()), mouseX, mouseY);
        }
    }

    private void drawMapControls(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String[] icons = {"plus", "minus", "focus"};
        String[] labels = {
                "screen.village-quest.trade_route.zoom_in",
                "screen.village-quest.trade_route.zoom_out",
                "screen.village-quest.trade_route.recenter"
        };
        for (int i = 0; i < icons.length; i++) {
            int x = left + ZOOM_X;
            int y = top + ZOOM_Y + i * (CONTROL_SIZE + CONTROL_GAP);
            boolean enabled = i == 0 ? zoomLevel < ZOOM_FACTORS.length - 1
                    : i == 1 ? zoomLevel > 0 : true;
            boolean hovered = enabled && within(mouseX, mouseY, x, y, CONTROL_SIZE, CONTROL_SIZE);
            VillageUiTheme.drawButton(graphics, font, x, y, CONTROL_SIZE, CONTROL_SIZE,
                    "", enabled, hovered, false);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon(icons[i]), x + 3, y + 3, 14);
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

    private void drawMapFooter(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<Payloads.TradeRouteLineData> routes = sortedRoutes();
        for (int i = 0; i < 5; i++) {
            int x = left + VIEW_X + i * (CHIP_WIDTH + 3);
            int y = top + FOOTER_Y;
            Payloads.TradeRouteLineData route = i < routes.size() ? routes.get(i) : null;
            boolean selected = route != null && route.routeIndex() == selectedRoute;
            boolean hovered = route != null && within(mouseX, mouseY, x, y, CHIP_WIDTH, CHIP_HEIGHT);
            VillageUiTheme.drawButton(graphics, font, x, y, CHIP_WIDTH, CHIP_HEIGHT,
                    route == null ? "–" : Integer.toString(route.routeIndex() + 1),
                    route != null, hovered, selected);
        }
        Payloads.TradeRouteLineData selected = selectedRoute();
        String summary = selected == null
                ? Component.translatable("screen.village-quest.trade_route.empty").getString()
                : selected.name().getString() + " · " + selected.statusLabel().getString();
        summary = VillageUiTheme.ellipsize(font, summary, 145);
        VillageUiTheme.drawStringScaled(graphics, font, summary,
                left + VIEW_X + 137, top + FOOTER_Y + 6, MUTED, 0.75f);
        int manageX = left + VIEW_X + VIEW_WIDTH - 72;
        boolean hovered = within(mouseX, mouseY, manageX, top + FOOTER_Y, 72, CHIP_HEIGHT);
        VillageUiTheme.drawButton(graphics, font, manageX, top + FOOTER_Y, 72, CHIP_HEIGHT,
                Component.translatable("screen.village-quest.trade_route.manage").getString(),
                true, hovered, false);
    }

    private void drawRoutesView(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<Payloads.TradeRouteLineData> routes = sortedRoutes();
        int listX = left + VIEW_X;
        int listY = top + VIEW_Y + 5;
        int rowWidth = 168;
        int rowHeight = 29;
        for (int i = 0; i < routes.size(); i++) {
            Payloads.TradeRouteLineData route = routes.get(i);
            int y = listY + i * 30;
            boolean selected = route.routeIndex() == selectedRoute;
            boolean hovered = within(mouseX, mouseY, listX, y, rowWidth, rowHeight);
            VillageUiTheme.drawCard(graphics, listX, y, rowWidth, rowHeight, hovered, selected);
            graphics.fill(listX + 6, y + 5, listX + 9, y + rowHeight - 5,
                    routeColorByIndex(route.liveryIndex()));
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(route.name().getString(), 105, 0.80f),
                    listX + 14, y + 7, INK, 0.80f);
            String status = route.surveying()
                    ? Component.translatable("screen.village-quest.trade_route.surveying").getString()
                    : route.statusLabel().getString();
            VillageUiTheme.drawStringScaled(graphics, font, compact(status, 105, 0.72f),
                    listX + 14, y + 18, routeColor(route.status()), 0.72f);
            String quality = route.roadQuality() + "%";
            VillageUiTheme.drawStringScaled(graphics, font, quality,
                    listX + rowWidth - font.width(quality) * 0.75f - 8,
                    y + 7, MUTED, 0.75f);
            String operation = route.paused() ? "Ⅱ" : "▶";
            int operationX = listX + rowWidth - 17;
            VillageUiTheme.drawStringScaled(graphics, font, operation,
                    operationX, y + 18, route.paused() ? MUTED : routeColorByIndex(route.liveryIndex()), 0.70f);
            boolean operationHovered = within(mouseX, mouseY, operationX - 2, y + 15, 12, 12);
            if (operationHovered) {
                graphics.setTooltipForNextFrame(font, Component.translatable(route.paused()
                        ? "screen.village-quest.trade_route.state.paused.tooltip"
                        : "screen.village-quest.trade_route.state.running.tooltip"), mouseX, mouseY);
            } else if (hovered) {
                graphics.setTooltipForNextFrame(font, routeTooltip(route), mouseX, mouseY);
            }
        }
        if (routes.isEmpty()) {
            drawWrapped(graphics,
                    Component.translatable("screen.village-quest.trade_route.register_hint").getString(),
                    listX + 8, listY + 8, rowWidth - 16, MUTED, 8);
        }

        int detailX = left + VIEW_X + 181;
        int detailY = top + VIEW_Y + 5;
        int detailWidth = 189;
        int detailHeight = 82;
        VillageUiTheme.drawCard(graphics, detailX, detailY, detailWidth, detailHeight, false, true);
        Payloads.TradeRouteLineData selected = selectedRoute();
        if (selected != null) {
            graphics.fill(detailX + 6, detailY + 6, detailX + 9, detailY + detailHeight - 6,
                    routeColorByIndex(selected.liveryIndex()));
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(selected.name().getString(), detailWidth - 24, 0.86f),
                    detailX + 12, detailY + 8, INK, 0.86f);
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(selected.statusLabel().getString(), detailWidth - 24, 0.75f),
                    detailX + 12, detailY + 21, routeColor(selected.status()), 0.75f);
            String[] lines = {
                    Component.translatable("screen.village-quest.trade_route.route_stats_short",
                            selected.roadQuality(), selected.waypoints().size()).getString(),
                    Component.translatable("screen.village-quest.trade_route.earnings_short",
                            selected.lifetimeEarnings()).getString(),
                    Component.translatable("screen.village-quest.trade_route.specialization",
                            selected.specializationLabel()).getString(),
                    Component.translatable("screen.village-quest.trade_route.approach",
                            selected.incidentApproachLabel()).getString()
            };
            int y = detailY + 34;
            for (String line : lines) {
                VillageUiTheme.drawStringScaled(graphics, font,
                        compact(line, detailWidth - 24, 0.70f),
                        detailX + 12, y, BODY, 0.70f);
                y += 9;
            }
            if (!selected.eventLabel().getString().isEmpty()) {
                VillageUiTheme.drawStringScaled(graphics, font,
                        compact(selected.eventLabel().getString(), detailWidth - 24, 0.70f),
                        detailX + 12, detailY + 72, DANGEROUS, 0.66f);
            }
            drawRouteActions(graphics, selected, detailX, detailY + detailHeight + 7,
                    detailWidth, mouseX, mouseY);
        }
        String summary = VillageUiTheme.ellipsize(font, data.summary().getString(), VIEW_WIDTH - 8);
        float summaryX = left + VIEW_X + VIEW_WIDTH - 4 - font.width(summary) * 0.75f;
        VillageUiTheme.drawStringScaled(graphics, font, summary,
                summaryX, top + FOOTER_Y + 18, MUTED, 0.75f);
    }

    private void drawRouteActions(GuiGraphics graphics, Payloads.TradeRouteLineData route,
                                  int x, int y, int width, int mouseX, int mouseY) {
        int gap = 4;
        int buttonWidth = (width - gap) / 2;
        drawActionButton(graphics, x, y, buttonWidth,
                Component.translatable(route.paused()
                        ? "screen.village-quest.trade_route.resume_short"
                        : "screen.village-quest.trade_route.pause_short").getString(),
                !route.surveying(), mouseX, mouseY);
        drawActionButton(graphics, x + buttonWidth + gap, y, buttonWidth,
                Component.translatable(route.surveying()
                        ? "screen.village-quest.trade_route.survey_finish"
                        : "screen.village-quest.trade_route.survey_start").getString(),
                true, mouseX, mouseY);
        boolean confirming = pendingRemovalRoute == route.routeIndex()
                && removalConfirmUntil >= System.currentTimeMillis();
        drawActionButton(graphics, x, y + 22, buttonWidth,
                Component.translatable(route.surveying()
                        ? "screen.village-quest.trade_route.survey_cancel"
                        : "screen.village-quest.trade_route.rename").getString(),
                true, mouseX, mouseY);
        drawActionButton(graphics, x + buttonWidth + gap, y + 22, buttonWidth,
                Component.translatable(confirming
                        ? "screen.village-quest.trade_route.remove_confirm"
                        : "screen.village-quest.trade_route.remove").getString(),
                true, mouseX, mouseY);
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int width, String label,
                                  boolean enabled, int mouseX, int mouseY) {
        VillageUiTheme.drawButton(graphics, font, x, y, width, 18, label, enabled,
                enabled && within(mouseX, mouseY, x, y, width, 18), false);
    }

    private void drawGuideView(GuiGraphics graphics, int left, int top) {
        int x = left + VIEW_X + 10;
        int y = top + VIEW_Y + 8;
        int cardWidth = VIEW_WIDTH - 20;
        VillageUiTheme.drawCard(graphics, x, y, cardWidth, 66, false, true);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.trade_route.guide_navigation").getString(),
                x + 24, y + 8, INK, 0.86f);
        VillageUiTheme.drawWrappedScaled(graphics, font,
                Component.translatable("screen.village-quest.trade_route.guide_navigation.body").getString(),
                x + 24, y + 23, cardWidth - 48, BODY, 0.72f, 4);
        y += 70;
        VillageUiTheme.drawCard(graphics, x, y, cardWidth, 66, false, false);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.trade_route.guide_routes").getString(),
                x + 24, y + 8, INK, 0.86f);
        VillageUiTheme.drawWrappedScaled(graphics, font,
                Component.translatable("screen.village-quest.trade_route.survey_tooltip").getString(),
                x + 24, y + 23, cardWidth - 128, BODY, 0.72f, 4);
        VillageUiTheme.drawMarker(graphics, "home", x + cardWidth - 74, y + 31, 25);
        VillageUiTheme.drawMarker(graphics, "caravan", x + cardWidth - 38, y + 31, 23);
    }

    private void drawBondsView(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int cardWidth = 179;
        int cardHeight = 34;
        if (data.bonds().isEmpty()) {
            VillageUiTheme.drawCard(graphics, left + VIEW_X + 8, top + VIEW_Y + 8,
                    VIEW_WIDTH - 16, 72, false, true);
            VillageUiTheme.drawWrappedScaled(graphics, font,
                    Component.translatable("screen.village-quest.trade_route.bonds_empty").getString(),
                    left + VIEW_X + 24, top + VIEW_Y + 26, VIEW_WIDTH - 48, MUTED, 0.78f, 4);
        }
        for (int i = 0; i < data.bonds().size(); i++) {
            Payloads.TradeRouteBondData bond = data.bonds().get(i);
            int column = i / 4;
            int row = i % 4;
            int x = left + VIEW_X + column * (cardWidth + 8);
            int y = top + VIEW_Y + 5 + row * (cardHeight + 4);
            boolean hovered = within(mouseX, mouseY, x, y, cardWidth, cardHeight);
            VillageUiTheme.drawCard(graphics, x, y, cardWidth, cardHeight, hovered, false);
            VillageUiTheme.drawMarker(graphics, "village", x + 15, y + 17, 17);
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(bond.type().getString(), cardWidth - 78, 0.78f), x + 29, y + 6, INK, 0.78f);
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(bond.condition().getString() + " · " + bond.need().getString(),
                            cardWidth - 38, 0.62f), x + 29, y + 18, BODY, 0.62f);
            String level = bond.level().getString();
            VillageUiTheme.drawStringScaled(graphics, font, level,
                    x + cardWidth - font.width(level) * 0.68f - 7, y + 6, FLOURISHING, 0.68f);
            if (hovered) {
                graphics.setTooltipForNextFrame(font, List.of(
                        bond.type(), bond.level(), bond.condition(), bond.need(),
                        Component.translatable("screen.village-quest.trade_route.bond_supply",
                                bond.support(), 100, bond.energyProgress(), 3),
                        bond.request(),
                        Component.translatable("screen.village-quest.trade_route.bond_coordinates",
                                bond.worldX(), bond.worldZ()),
                        Component.translatable("screen.village-quest.trade_route.bond_requests",
                                bond.completions())), mouseX, mouseY);
            }
        }
        String summary = Component.translatable("screen.village-quest.trade_route.bonds_summary",
                data.bonds().size(), data.shrines().size()).getString();
        VillageUiTheme.drawStringScaled(graphics, font, summary,
                left + VIEW_X + 4, top + FOOTER_Y + 18, MUTED, 0.75f);
    }

    private boolean handleMapClick(int mouseX, int mouseY, int left, int top) {
        for (int i = 0; i < 3; i++) {
            int x = left + ZOOM_X;
            int y = top + ZOOM_Y + i * (CONTROL_SIZE + CONTROL_GAP);
            if (!within(mouseX, mouseY, x, y, CONTROL_SIZE, CONTROL_SIZE)) {
                continue;
            }
            if (i == 0) {
                setZoom(zoomLevel + 1);
            } else if (i == 1) {
                setZoom(zoomLevel - 1);
            } else {
                resetMapCenter(true);
            }
            return true;
        }
        List<Payloads.TradeRouteLineData> routes = sortedRoutes();
        for (int i = 0; i < routes.size() && i < 5; i++) {
            int x = left + VIEW_X + i * (CHIP_WIDTH + 3);
            if (within(mouseX, mouseY, x, top + FOOTER_Y, CHIP_WIDTH, CHIP_HEIGHT)) {
                selectedRoute = routes.get(i).routeIndex();
                return true;
            }
        }
        int manageX = left + VIEW_X + VIEW_WIDTH - 72;
        if (within(mouseX, mouseY, manageX, top + FOOTER_Y, 72, CHIP_HEIGHT)) {
            viewMode = ViewMode.ROUTES;
            return true;
        }
        return false;
    }

    private boolean handleRoutesClick(int mouseX, int mouseY, int left, int top) {
        List<Payloads.TradeRouteLineData> routes = sortedRoutes();
        int listX = left + VIEW_X;
        int listY = top + VIEW_Y + 5;
        for (int i = 0; i < routes.size(); i++) {
            if (within(mouseX, mouseY, listX, listY + i * 30, 168, 29)) {
                selectedRoute = routes.get(i).routeIndex();
                pendingRemovalRoute = -1;
                return true;
            }
        }
        Payloads.TradeRouteLineData route = selectedRoute();
        if (route == null) {
            return false;
        }
        int x = left + VIEW_X + 181;
        int y = top + VIEW_Y + 94;
        int width = 189;
        int gap = 4;
        int buttonWidth = (width - gap) / 2;
        if (within(mouseX, mouseY, x, y, buttonWidth, 18) && !route.surveying()) {
            sendAction(Payloads.TradeRouteActionPayload.ACTION_TOGGLE, route.routeIndex());
            return true;
        }
        if (within(mouseX, mouseY, x + buttonWidth + gap, y, buttonWidth, 18)) {
            sendAction(route.surveying()
                    ? Payloads.TradeRouteActionPayload.ACTION_SURVEY_FINISH
                    : Payloads.TradeRouteActionPayload.ACTION_SURVEY_START, route.routeIndex());
            if (!route.surveying()) {
                closeToGame();
            }
            return true;
        }
        if (within(mouseX, mouseY, x, y + 22, buttonWidth, 18)) {
            if (route.surveying()) {
                sendAction(Payloads.TradeRouteActionPayload.ACTION_SURVEY_CANCEL, route.routeIndex());
            } else {
                openRouteRename(route);
            }
            return true;
        }
        if (within(mouseX, mouseY, x + buttonWidth + gap, y + 22, buttonWidth, 18)) {
            long now = System.currentTimeMillis();
            if (pendingRemovalRoute == route.routeIndex() && removalConfirmUntil >= now) {
                pendingRemovalRoute = -1;
                sendAction(Payloads.TradeRouteActionPayload.ACTION_REMOVE, route.routeIndex());
            } else {
                pendingRemovalRoute = route.routeIndex();
                removalConfirmUntil = now + 30_000L;
            }
            return true;
        }
        return false;
    }

    private void sendAction(int action, int routeIndex) {
        ClientPlayNetworking.send(new Payloads.TradeRouteActionPayload(action, routeIndex));
    }

    private void setZoom(int value) {
        zoomLevel = Math.max(0, Math.min(ZOOM_FACTORS.length - 1, value));
        SurfaceMapRenderer.invalidateScreen();
    }

    private float nodeLabelScale() {
        return switch (zoomLevel) {
            case 0, 1 -> 0.0f;
            case 2 -> 0.50f;
            case 3 -> 0.60f;
            case 4 -> 0.70f;
            case 5 -> 0.82f;
            default -> 1.0f;
        };
    }

    private void resetMapCenter(boolean preferPlayer) {
        Minecraft client = Minecraft.getInstance();
        if (preferPlayer && client.player != null) {
            centerX = client.player.getX();
            centerZ = client.player.getZ();
        } else {
            Bounds bounds = networkBounds();
            centerX = (bounds.minX + bounds.maxX) / 2.0;
            centerZ = (bounds.minZ + bounds.maxZ) / 2.0;
        }
        centerInitialized = true;
        SurfaceMapRenderer.invalidateScreen();
    }

    private Bounds displayBounds() {
        Bounds base = networkBounds();
        double factor = ZOOM_FACTORS[zoomLevel];
        int halfWidth = Math.max(64, (int) Math.round((base.maxX - base.minX) * factor / 2.0));
        int halfHeight = Math.max(48, (int) Math.round((base.maxZ - base.minZ) * factor / 2.0));
        int minX = (int) Math.floor(centerX - halfWidth);
        int minZ = (int) Math.floor(centerZ - halfHeight);
        return new Bounds(minX, minX + halfWidth * 2, minZ, minZ + halfHeight * 2);
    }

    private Bounds networkBounds() {
        if (data.nodes().isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            int x = client.player == null ? 0 : client.player.getBlockX();
            int z = client.player == null ? 0 : client.player.getBlockZ();
            return new Bounds(x - 192, x + 192, z - 128, z + 128);
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Payloads.TradeRouteNodeData node : data.nodes()) {
            minX = Math.min(minX, node.worldX());
            maxX = Math.max(maxX, node.worldX());
            minZ = Math.min(minZ, node.worldZ());
            maxZ = Math.max(maxZ, node.worldZ());
        }
        for (Payloads.TradeRouteLineData route : data.routes()) {
            for (Payloads.TradeRoutePointData point : route.waypoints()) {
                minX = Math.min(minX, point.worldX());
                maxX = Math.max(maxX, point.worldX());
                minZ = Math.min(minZ, point.worldZ());
                maxZ = Math.max(maxZ, point.worldZ());
            }
        }
        for (Payloads.TradeRouteBondData bond : data.bonds()) {
            minX = Math.min(minX, bond.worldX()); maxX = Math.max(maxX, bond.worldX());
            minZ = Math.min(minZ, bond.worldZ()); maxZ = Math.max(maxZ, bond.worldZ());
        }
        for (Payloads.TradeRouteShrineData shrine : data.shrines()) {
            minX = Math.min(minX, shrine.worldX()); maxX = Math.max(maxX, shrine.worldX());
            minZ = Math.min(minZ, shrine.worldZ()); maxZ = Math.max(maxZ, shrine.worldZ());
        }
        for (Payloads.TradeRouteDecorationData decoration : data.decorations()) {
            minX = Math.min(minX, decoration.worldX()); maxX = Math.max(maxX, decoration.worldX());
            minZ = Math.min(minZ, decoration.worldZ()); maxZ = Math.max(maxZ, decoration.worldZ());
        }
        int paddingX = Math.max(72, (maxX - minX) / 8);
        int paddingZ = Math.max(54, (maxZ - minZ) / 8);
        return new Bounds(minX - paddingX, maxX + paddingX, minZ - paddingZ, maxZ + paddingZ);
    }

    private List<Payloads.TradeRouteLineData> sortedRoutes() {
        return data.routes().stream()
                .sorted(Comparator.comparingInt(Payloads.TradeRouteLineData::routeIndex)).toList();
    }

    private Payloads.TradeRouteLineData selectedRoute() {
        return data.routes().stream().filter(route -> route.routeIndex() == selectedRoute)
                .findFirst().orElse(null);
    }

    private Payloads.TradeRouteNodeData node(int index) {
        return data.nodes().stream().filter(node -> node.nodeIndex() == index).findFirst().orElse(null);
    }

    private List<Component> routeTooltip(Payloads.TradeRouteLineData route) {
        List<Component> tooltip = new ArrayList<>();
        addWrappedTooltip(tooltip, route.name());
        addWrappedTooltip(tooltip, Component.translatable(route.paused()
                ? "screen.village-quest.trade_route.state.paused"
                : "screen.village-quest.trade_route.state.running"));
        addWrappedTooltip(tooltip, route.statusLabel());
        addWrappedTooltip(tooltip, Component.translatable(
                "screen.village-quest.trade_route.quality_short", route.roadQuality()));
        addWrappedTooltip(tooltip, Component.translatable(
                "screen.village-quest.trade_route.waypoints", route.waypoints().size()));
        long ferryPoints = route.waypoints().stream().filter(Payloads.TradeRoutePointData::ocean).count();
        if (ferryPoints > 0) {
            addWrappedTooltip(tooltip, Component.translatable(
                    "screen.village-quest.trade_route.ferry_points", ferryPoints));
        }
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.trade_route.specialization",
                route.specializationLabel()));
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.trade_route.upgrades",
                route.upgradeSummary()));
        if (!route.eventHelp().getString().isEmpty()) {
            addWrappedTooltip(tooltip, route.eventHelp());
        }
        return tooltip;
    }

    private void addWrappedTooltip(List<Component> tooltip, Component line) {
        String text = line == null ? "" : line.getString().trim();
        if (text.isEmpty()) {
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(candidate) > 240) {
                tooltip.add(Component.literal(current.toString()));
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            tooltip.add(Component.literal(current.toString()));
        }
    }

    private void openRouteRename(Payloads.TradeRouteLineData route) {
        if (minecraft == null || route == null) {
            return;
        }
        notifyClosed();
        minecraft.gui.setScreen(new ChatScreen("/vq routes rename " + (route.routeIndex() + 1) + " ", false));
    }

    private List<WorldPoint> routePath(Payloads.TradeRouteNodeData home,
                                       Payloads.TradeRouteNodeData destination,
                                       Payloads.TradeRouteLineData route) {
        List<WorldPoint> path = new ArrayList<>(route.waypoints().size() + 2);
        path.add(new WorldPoint(home.worldX(), home.worldZ(), false));
        for (Payloads.TradeRoutePointData waypoint : route.waypoints()) {
            path.add(new WorldPoint(waypoint.worldX(), waypoint.worldZ(), waypoint.ocean()));
        }
        path.add(new WorldPoint(destination.worldX(), destination.worldZ(), false));
        return path;
    }

    private WorldPoint pointAlong(List<WorldPoint> path, int progress) {
        double total = 0.0;
        for (int i = 1; i < path.size(); i++) {
            total += path.get(i - 1).distance(path.get(i));
        }
        if (total <= 0.0) {
            return path.getFirst();
        }
        double remaining = total * Math.max(0, Math.min(10_000, progress)) / 10_000.0;
        for (int i = 1; i < path.size(); i++) {
            WorldPoint from = path.get(i - 1);
            WorldPoint to = path.get(i);
            double length = from.distance(to);
            if (remaining <= length) {
                double factor = remaining / Math.max(0.001, length);
                return new WorldPoint(from.x + (to.x - from.x) * factor,
                        from.z + (to.z - from.z) * factor, from.ocean || to.ocean);
            }
            remaining -= length;
        }
        return path.getLast();
    }

    private Point pointFor(double worldX, double worldZ, Bounds bounds,
                           int left, int top, boolean clamp) {
        double nx = (worldX - bounds.minX) / Math.max(1.0, bounds.maxX - bounds.minX);
        double nz = (worldZ - bounds.minZ) / Math.max(1.0, bounds.maxZ - bounds.minZ);
        if (clamp) {
            nx = Math.max(0.02, Math.min(0.98, nx));
            nz = Math.max(0.02, Math.min(0.98, nz));
        }
        return new Point(left + VIEW_X + (int) Math.round(nx * VIEW_WIDTH),
                top + VIEW_Y + (int) Math.round(nz * VIEW_HEIGHT));
    }

    private void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int radius = Math.max(0, thickness / 2);
        while (true) {
            graphics.fill(x0 - radius, y0 - radius, x0 + radius + 1, y0 + radius + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int twice = error * 2;
            if (twice >= dy) {
                error += dy;
                x0 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private void drawDashedLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                int color, int thickness, int dashLength, int gapLength) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        if (length <= 0.0) {
            return;
        }
        double cycle = Math.max(1, dashLength + gapLength);
        for (double start = 0.0; start < length; start += cycle) {
            double end = Math.min(length, start + dashLength);
            int sx = (int) Math.round(x0 + (x1 - x0) * (start / length));
            int sy = (int) Math.round(y0 + (y1 - y0) * (start / length));
            int ex = (int) Math.round(x0 + (x1 - x0) * (end / length));
            int ey = (int) Math.round(y0 + (y1 - y0) * (end / length));
            drawLine(graphics, sx, sy, ex, ey, color, thickness);
        }
    }

    private String formatEta(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private int routeColor(int status) {
        return switch (status) {
            case 3 -> FLOURISHING;
            case 2 -> SECURED;
            default -> DANGEROUS;
        };
    }

    private int routeColorByIndex(int routeIndex) {
        return ROUTE_COLORS[Math.floorMod(routeIndex, ROUTE_COLORS.length)];
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y,
                             int maxWidth, int color, int maxLines) {
        StringBuilder line = new StringBuilder();
        int count = 0;
        for (String word : text.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(next) > maxWidth) {
                graphics.drawString(font, line.toString(), x, y, color, false);
                y += font.lineHeight + 2;
                count++;
                if (count >= maxLines) {
                    return;
                }
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (!line.isEmpty() && count < maxLines) {
            graphics.drawString(font, VillageUiTheme.ellipsize(font, line.toString(), maxWidth),
                    x, y, color, false);
        }
    }

    private String compact(String text, int renderedWidth, float scale) {
        return VillageUiTheme.ellipsize(font, text,
                Math.max(1, (int) Math.floor(renderedWidth / scale)));
    }

    private boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void notifyClosed() {
        if (closeNotified) {
            return;
        }
        closeNotified = true;
        sendAction(Payloads.TradeRouteActionPayload.ACTION_CLOSE, -1);
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {}
    private record Point(int x, int y) {}
    private record LabelBox(float left, float top, float right, float bottom) {
        private boolean overlaps(LabelBox other) {
            return left < other.right && right > other.left
                    && top < other.bottom && bottom > other.top;
        }
    }
    private record WorldPoint(double x, double z, boolean ocean) {
        private double distance(WorldPoint other) {
            double dx = other.x - x;
            double dz = other.z - z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }
}
