package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.client.ui.SurfaceMapRenderer;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.KingdomUiHints;
import de.quest.network.Payloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Full-screen guild map with route, village-bond, and guide views. */
public final class TradeRouteMapScreen extends CompatScreen {
    private final KingdomUiHints hints=new KingdomUiHints();
    private enum ViewMode { MAP, ROUTES, BONDS, GUIDE }

    private static final int WINDOW_WIDTH = 432;
    private static final int WINDOW_HEIGHT = 326;
    private static final int VIEW_X = 32;
    private static final int VIEW_Y = 73;
    private static final int VIEW_WIDTH = 368;
    private static final int VIEW_HEIGHT = 166;
    private static final int TAB_X = 32;
    private static final int TAB_Y = 45;
    private static final int TAB_SIZE = 22;
    private static final int TAB_WIDTH = 89;
    private static final int TAB_GAP = 3;
    private static final int ZOOM_X = VIEW_X + VIEW_WIDTH - 25;
    private static final int ZOOM_Y = VIEW_Y + 34;
    private static final int CONTROL_SIZE = 20;
    private static final int CONTROL_GAP = 3;
    private static final int FOOTER_Y = 242;
    private static final int CHIP_WIDTH = 23;
    private static final int CHIP_HEIGHT = 17;
    private static final int FRAME = 0xFF4F321B;
    private static final int PAPER = 0xFFF5E7C7;
    private static final int INK = 0xFF3E2918;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF80694F;
    private static final int DANGEROUS = 0xFFB9573E;
    private static final int SUSPENDED = 0xFF9B6A29;
    private static final int LIFE_ABANDONED = 1;
    private static final int LIFE_RECOVERING = 2;
    private static final int BONDS_PER_PAGE = 8;
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
    private int bondPage;
    private int zoomLevel = DEFAULT_ZOOM_LEVEL;
    private double centerX;
    private double centerZ;
    private boolean centerInitialized;
    private boolean closeNotified;
    private int heartbeatTicks;
    private boolean mapDragging;
    private int pendingRemovalRoute = -1;
    private long removalConfirmUntil;
    private int villageX, villageZ, villagePage;
    private boolean villageOpen;
    private Component villageTitle = Component.empty();

    public TradeRouteMapScreen(Payloads.TradeRouteMapPayload data) {
        super(Component.translatable("screen.village-quest.trade_route.title"));
        this.data = data;
        this.selectedRoute = data.routes().isEmpty() ? -1 : data.routes().getFirst().routeIndex();
    }

    public void updateData(Payloads.TradeRouteMapPayload data) {
        this.data = data;
        bondPage = Math.min(bondPage, Math.max(0, (data.bonds().size() - 1) / BONDS_PER_PAGE));
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
    public void tick() {
        super.tick();
        if (++heartbeatTicks >= 100) {
            heartbeatTicks = 0;
            sendAction(Payloads.TradeRouteActionPayload.ACTION_MAP_HEARTBEAT, -1);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if(villageOpen) { villageOpen=false; return; }
        returnToJournal();
    }

    private void returnToJournal() {
        notifyClosed();
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            UiTestTour.command("vq journal open");
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
        VillageUiTheme.drawScreenShade(graphics, width, height); hints.clear();
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        float panelScale = beginResponsivePanel(graphics, WINDOW_WIDTH, WINDOW_HEIGHT);
        try {
            int left = (width - WINDOW_WIDTH) / 2;
            int top = (height - WINDOW_HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
            de.quest.client.ui.JournalV3Visuals.insetBoard(graphics,left,top,WINDOW_WIDTH,WINDOW_HEIGHT);
            drawTabs(graphics, left, top, uiMouseX, uiMouseY);
            drawHeader(graphics, left, top);
            KingdomUiAssets.closeButton(graphics,left,top,WINDOW_WIDTH,WINDOW_HEIGHT,KingdomUiAssets.closeBounds(left,top,WINDOW_WIDTH,WINDOW_HEIGHT).contains(uiMouseX,uiMouseY));
            if(KingdomUiAssets.closeBounds(left,top,WINDOW_WIDTH,WINDOW_HEIGHT).contains(uiMouseX,uiMouseY)) hints.setTooltipForNextFrame(font,
                    Component.translatable("screen.village-quest.building.close"),uiMouseX,uiMouseY);
            switch (viewMode) {
                case MAP -> drawMapView(graphics, left, top, uiMouseX, uiMouseY);
                case ROUTES -> drawRoutesView(graphics, left, top, uiMouseX, uiMouseY);
                case BONDS -> drawBondsView(graphics, left, top, uiMouseX, uiMouseY);
                case GUIDE -> drawGuideView(graphics, left, top);
            }
            VillageUiTheme.drawButton(graphics,font,left+31,top + 270,88,16,
                    Component.translatable("screen.village-quest.trade_route.back").getString(),true,
                    within(uiMouseX,uiMouseY,left+31,top + 270,88,16),false);
            if(villageOpen) drawVillageDetails(graphics,left,top,uiMouseX,uiMouseY);
            super.render(graphics, uiMouseX, uiMouseY, delta);
            hints.draw(graphics,font,left,top,WINDOW_WIDTH,WINDOW_HEIGHT);
        } finally {
            endResponsivePanel(graphics, panelScale);
        }
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.handleMouseClicked(click, doubled);
        }
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WINDOW_WIDTH, WINDOW_HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WINDOW_WIDTH, WINDOW_HEIGHT);
        if(villageOpen) {
            if(KingdomUiAssets.closeBounds(left,top,WINDOW_WIDTH,WINDOW_HEIGHT).contains(mouseX,mouseY)) { villageOpen=false; return true; }
            int pages=villagePages();
            if(within(mouseX,mouseY,left+315,top + 198,24,18)) villagePage=Math.max(0,villagePage-1);
            if(within(mouseX,mouseY,left+373,top + 198,24,18)) villagePage=Math.min(pages-1,villagePage+1);
            if(within(mouseX,mouseY,left+31,top + 198,120,18) && villageRoute()!=null && data.manageable()) {
                selectedRoute=villageRoute().routeIndex(); villageOpen=false; viewMode=ViewMode.ROUTES;
            }
            return true;
        }
        if(KingdomUiAssets.closeBounds(left,top,WINDOW_WIDTH,WINDOW_HEIGHT).contains(mouseX,mouseY)) { closeToGame(); return true; }
        if(within(mouseX,mouseY,left+31,top + 270,88,16)) { returnToJournal(); return true; }
        for (int i = 0; i < ViewMode.values().length; i++) {
            int x = left + TAB_X + i * (TAB_WIDTH + TAB_GAP);
            if (within(mouseX, mouseY, x, top + TAB_Y, TAB_WIDTH, TAB_SIZE)) {
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
                yield super.handleMouseClicked(click, doubled);
            }
            case ROUTES -> handleRoutesClick(mouseX, mouseY, left, top) || super.handleMouseClicked(click, doubled);
            case BONDS -> handleBondsClick(mouseX, mouseY, left, top)
                    || super.handleMouseClicked(click, doubled);
            case GUIDE -> super.handleMouseClicked(click, doubled);
        };
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (mapDragging && click.button() == InputConstants.MOUSE_BUTTON_LEFT && viewMode == ViewMode.MAP) {
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
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && mapDragging) {
            mapDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if(villageOpen) { villagePage=Math.max(0,Math.min(villagePages()-1,villagePage+(verticalAmount>0?-1:1))); return true; }
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if (viewMode == ViewMode.MAP && within(uiMouseX, uiMouseY,
                left + VIEW_X, top + VIEW_Y, VIEW_WIDTH, VIEW_HEIGHT)) {
            setZoom(zoomLevel + (verticalAmount > 0.0 ? 1 : -1));
            return true;
        }
        if (viewMode == ViewMode.BONDS && verticalAmount != 0.0 && within(uiMouseX, uiMouseY,
                left + VIEW_X, top + VIEW_Y, VIEW_WIDTH, VIEW_HEIGHT)) {
            setBondPage(bondPage + (verticalAmount > 0.0 ? -1 : 1));
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
            int x = left + TAB_X + i * (TAB_WIDTH + TAB_GAP);
            int y = top + TAB_Y;
            boolean hovered = within(mouseX, mouseY, x, y, TAB_WIDTH, TAB_SIZE);
            VillageUiTheme.drawTab(graphics, x, y, TAB_WIDTH, TAB_SIZE,
                    viewMode.ordinal() == i, hovered);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon(icons[i]), x + 4, y + 4, 14);
            VillageUiTheme.drawStringScaled(graphics,font,compact(Component.translatable(labels[i]).getString(),TAB_WIDTH-26,.75f),x+22,y+7,VillageUiTheme.LIGHT_TEXT,.75f);
            if (hovered) {
                hints.setTooltipForNextFrame(font, Component.translatable(labels[i]), mouseX, mouseY);
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
        de.quest.client.ui.JournalV3Visuals.title(graphics,font,value,left+98,top+5,236,"compass");
    }

    private void drawMapView(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int mapX = left + VIEW_X;
        int mapY = top + VIEW_Y;
        Bounds bounds = displayBounds();
        graphics.enableScissor(mapX, mapY, mapX + VIEW_WIDTH, mapY + VIEW_HEIGHT);
        SurfaceMapRenderer.drawScreen(graphics, mapX, mapY, VIEW_WIDTH, VIEW_HEIGHT,
                bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ, !mapDragging);
        drawRoutesOnMap(graphics, bounds, left, top, mouseX, mouseY);
        for(var bond:VillageQuestClientConfig.get().showVillageMarkers()?data.bonds():List.<Payloads.TradeRouteBondData>of()) {
            if(data.nodes().stream().anyMatch(n->n.worldX()==bond.worldX() && n.worldZ()==bond.worldZ()))continue;
            Point p=pointFor(bond.worldX(),bond.worldZ(),bounds,left,top,false);
            KingdomUiAssets.detailIcon(graphics,KingdomUiAssets.DetailIcon.FOUNDING,p.x-11,p.y-11,22);
            drawLifeBadge(graphics,p.x+7,p.y+5,bond.lifeStatus());
            if(Math.abs(mouseX-p.x)<=12 && Math.abs(mouseY-p.y)<=12)
                hints.setTooltipForNextFrame(font,List.of(bond.type(),Component.translatable("screen.village-quest.trade_route.village_open")),mouseX,mouseY);
        }
        graphics.disableScissor();
        VillageUiTheme.drawMapRim(graphics,mapX,mapY,VIEW_WIDTH,VIEW_HEIGHT);
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
                    if (route.settlementSuspended()) {
                        drawDashedLine(graphics, from.x, from.y, to.x, to.y, MUTED,
                                route.routeIndex() == selectedRoute ? 3 : 2, 5, 4);
                    } else if (ferry) {
                        drawDashedLine(graphics, from.x, from.y, to.x, to.y, FERRY_SHADOW,
                                route.routeIndex() == selectedRoute ? 4 : 3, 6, 3);
                        drawDashedLine(graphics, from.x, from.y, to.x, to.y, FERRY_ROUTE,
                                route.routeIndex() == selectedRoute ? 2 : 1, 6, 3);
                    } else {
                        if(route.routeIndex()==selectedRoute)
                            drawLine(graphics,from.x,from.y,to.x,to.y,0xFFF1D49A,7);
                        drawLine(graphics, from.x, from.y, to.x, to.y, ROAD_SHADOW,
                                route.routeIndex() == selectedRoute ? 5 : 4);
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
                        hints.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
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
                    hints.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
            }
        }
        if (config.showVillageMarkers()) {
            List<LabelBox> placedLabels = new ArrayList<>();
            for(var marker:data.nodes()) {
                Point p=pointFor(marker.worldX(),marker.worldZ(),bounds,left,top,false);
                placedLabels.add(new LabelBox(p.x-13,p.y-13,p.x+13,p.y+13));
            }
            for(var marker:data.shrines()) {
                Point p=shrinePoint(marker,bounds,left,top);
                placedLabels.add(new LabelBox(p.x-10,p.y-10,p.x+10,p.y+10));
            }
            for (Payloads.TradeRouteNodeData node : data.nodes()) {
                Point point = pointFor(node.worldX(), node.worldZ(), bounds, left, top, false);
                KingdomUiAssets.detailIcon(graphics,node.home()?KingdomUiAssets.DetailIcon.THRONE:KingdomUiAssets.DetailIcon.FOUNDING,
                        point.x-12, point.y-12,24);
                if(!node.playerYard()) drawLifeBadge(graphics,point.x+7,point.y+5,node.lifeStatus());
                boolean hovered = Math.abs(mouseX - point.x) <= 13 && Math.abs(mouseY - point.y) <= 13;
                if (hovered) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(node.name());
                    if (!node.playerYard()) {
                        tooltip.add(lifeStatusLabel(node.lifeStatus()));
                    }
                    tooltip.add(Component.literal("X " + node.worldX() + "  Z " + node.worldZ()));
                    hints.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                } else {
                    float labelScale = nodeLabelScale();
                    if (labelScale > 0.0f) {
                        int renderedLimit = zoomLevel <= 3 ? 80 : zoomLevel <= 5 ? 120 : 180;
                        String label = compact(node.name().getString(), renderedLimit, labelScale);
                        float labelWidth = font.width(label) * labelScale;
                        float labelX = Math.max(left+VIEW_X+3,Math.min(left+VIEW_X+VIEW_WIDTH-labelWidth-3,point.x-labelWidth/2f));
                        float labelY = Math.min(top+VIEW_Y+VIEW_HEIGHT-10,point.y+(node.home()?14:12));
                        LabelBox candidate = new LabelBox(labelX - 2.0f, labelY - 1.0f,
                                labelX + labelWidth + 2.0f,
                                labelY + font.lineHeight * labelScale + 1.0f);
                        if (placedLabels.stream().noneMatch(candidate::overlaps)) {
                            de.quest.client.ui.JournalV3Visuals.paper(graphics,(int)labelX-3,(int)labelY-2,Math.round(labelWidth)+6,12);
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
            Point anchor=pointFor(shrine.worldX(),shrine.worldZ(),bounds,left,top,false);
            Point point=shrinePoint(shrine,bounds,left,top);
            if(!anchor.equals(point))drawLine(graphics,anchor.x,anchor.y,point.x,point.y,INK,1);
            VillageUiTheme.drawMarker(graphics, "shrine", point.x, point.y, 17);
            if (Math.abs(mouseX - point.x) <= 9 && Math.abs(mouseY - point.y) <= 9) {
                hints.setTooltipForNextFrame(font, List.of(shrine.name(), Component.translatable(
                        "screen.village-quest.trade_route.shrine_coordinates",
                        shrine.worldX(), shrine.worldY(), shrine.worldZ())), mouseX, mouseY);
            }
        }
        for (Payloads.TradeRouteDecorationData decoration : data.decorations()) {
            Point point = pointFor(decoration.worldX(), decoration.worldZ(), bounds, left, top, false);
            VillageUiTheme.drawMarker(graphics, decoration.type() == 0 ? "notice" : "milestone",
                    point.x, point.y, decoration.type() == 0 ? 13 : 11);
            if (Math.abs(mouseX - point.x) <= 7 && Math.abs(mouseY - point.y) <= 7) {
                hints.setTooltipForNextFrame(font, Component.translatable(
                        decoration.type() == 0 ? "screen.village-quest.trade_route.notice_coordinates"
                                : "screen.village-quest.trade_route.milestone_coordinates",
                        decoration.worldX(), decoration.worldY(), decoration.worldZ()), mouseX, mouseY);
            }
        }
    }

    /** Offset a co-located shrine badge, retaining a leader to its true world anchor. */
    private Point shrinePoint(Payloads.TradeRouteShrineData shrine,Bounds bounds,int left,int top) {
        Point p=pointFor(shrine.worldX(),shrine.worldZ(),bounds,left,top,false);
        boolean grouped=VillageQuestClientConfig.get().showVillageMarkers() &&
                (data.nodes().stream().anyMatch(n->{Point q=pointFor(n.worldX(),n.worldZ(),bounds,left,top,false);return Math.abs(q.x-p.x)<24 && Math.abs(q.y-p.y)<24;})
                || data.bonds().stream().anyMatch(n->{Point q=pointFor(n.worldX(),n.worldZ(),bounds,left,top,false);return Math.abs(q.x-p.x)<24 && Math.abs(q.y-p.y)<24;}));
        return grouped?new Point(Math.clamp(p.x+21,left+VIEW_X+10,left+VIEW_X+VIEW_WIDTH-10),
                Math.clamp(p.y-18,top+VIEW_Y+10,top+VIEW_Y+VIEW_HEIGHT-10)):p;
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
            hints.setTooltipForNextFrame(font,
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
                hints.setTooltipForNextFrame(font, Component.translatable(labels[i]), mouseX, mouseY);
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
        VillageUiTheme.drawPaper(graphics,left+VIEW_X,top+FOOTER_Y-3,VIEW_WIDTH,30);
        Payloads.TradeRouteLineData selected = selectedRoute();
        VillageUiTheme.drawButton(graphics,font,left+VIEW_X,top+FOOTER_Y,VIEW_WIDTH-82,CHIP_HEIGHT,
                selected==null?"—":selected.name().getString(),selected!=null,
                within(mouseX,mouseY,left+VIEW_X,top+FOOTER_Y,VIEW_WIDTH-82,CHIP_HEIGHT),true);
        if(selected!=null && within(mouseX,mouseY,left+VIEW_X,top+FOOTER_Y,VIEW_WIDTH-82,CHIP_HEIGHT))
            hints.setTooltipForNextFrame(font,List.of(selected.name(),Component.translatable("screen.village-quest.trade_route.next_route")),mouseX,mouseY);
        String summary = selected == null
                ? Component.translatable("screen.village-quest.trade_route.empty").getString()
                : (selected.availabilityLabel().getString().isEmpty()
                ? selected.statusLabel().getString() : selected.availabilityLabel().getString());
        summary = VillageUiTheme.ellipsize(font, summary, 500);
        VillageUiTheme.drawStringScaled(graphics, font, summary,
                left + VIEW_X + 5, top + FOOTER_Y + 20, MUTED, 0.75f);
        int manageX = left + VIEW_X + VIEW_WIDTH - 72;
        boolean hovered = within(mouseX, mouseY, manageX, top + FOOTER_Y, 72, CHIP_HEIGHT);
        VillageUiTheme.drawButton(graphics, font, manageX, top + FOOTER_Y, 72, CHIP_HEIGHT,
                Component.translatable("screen.village-quest.trade_route.manage").getString(),
                data.manageable() && !routes.isEmpty(), hovered, false);
        if(hovered && (!data.manageable() || routes.isEmpty())) hints.setTooltipForNextFrame(font,
                Component.translatable("screen.village-quest.trade_route.empty_help"),mouseX,mouseY);
        VillageUiTheme.drawPaper(graphics,left+124,top + 269,270,17);
        String[] legend={"road","ferry","suspended"};
        for(int i=0;i<legend.length;i++) {
            int x=left+130+i*88,y=top + 278;
            if(i==0) drawLine(graphics,x,y,x+12,y,ROAD_SHADOW,2);
            else drawDashedLine(graphics,x,y,x+12,y,i==1?FERRY_ROUTE:MUTED,2,3,2);
            VillageUiTheme.drawStringScaled(graphics,font,compact(Component.translatable(
                    "screen.village-quest.trade_route.legend."+legend[i]).getString(),66,.62f),x+17,y-3,INK,.62f);
        }
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
                    : !route.availabilityLabel().getString().isEmpty()
                    ? route.availabilityLabel().getString()
                    : route.statusLabel().getString();
            VillageUiTheme.drawStringScaled(graphics, font, compact(status, 105, 0.72f),
                    listX + 14, y + 18,
                    route.settlementSuspended() ? SUSPENDED : route.paused() ? MUTED : INK, 0.82f);
            String quality = route.roadQuality() + "%";
            VillageUiTheme.drawStringScaled(graphics, font, quality,
                    listX + rowWidth - font.width(quality) * 0.75f - 8,
                    y + 7, MUTED, 0.75f);
            String operation = route.paused() || route.settlementSuspended() ? "Ⅱ" : "▶";
            int operationX = listX + rowWidth - 17;
            VillageUiTheme.drawStringScaled(graphics, font, operation,
                    operationX, y + 18, route.paused() || route.settlementSuspended()
                            ? MUTED : routeColorByIndex(route.liveryIndex()), 0.70f);
            boolean operationHovered = within(mouseX, mouseY, operationX - 2, y + 15, 12, 12);
            if (operationHovered) {
                hints.setTooltipForNextFrame(font, route.settlementSuspended()
                        ? route.availabilityLabel() : Component.translatable(route.paused()
                        ? "screen.village-quest.trade_route.state.paused.tooltip"
                        : "screen.village-quest.trade_route.state.running.tooltip"), mouseX, mouseY);
            } else if (hovered) {
                hints.setTooltipForNextFrame(font, routeTooltip(route), mouseX, mouseY);
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
                    compact(selected.availabilityLabel().getString().isEmpty()
                            ? selected.statusLabel().getString()
                            : selected.availabilityLabel().getString(), detailWidth - 24, 0.85f),
                    detailX + 12, detailY + 21,
                    selected.settlementSuspended() ? SUSPENDED : selected.paused() ? MUTED : INK, 0.85f);
            String[] lines = {
                    Component.translatable("screen.village-quest.trade_route.route_stats_short",
                            selected.roadQuality(), selected.waypoints().size()).getString(),
                    Component.translatable("screen.village-quest.trade_route.earnings_short",
                            selected.lifetimeEarnings()).getString(),
                    Component.translatable("screen.village-quest.trade_route.specialization",
                            selected.specializationLabel()).getString(),
                    Component.translatable("screen.village-quest.trade_route.approach",
                            selected.incidentApproachLabel().getString().isBlank()?Component.translatable("screen.village-quest.ui_review.none"):selected.incidentApproachLabel()).getString()
            };
            int y = detailY + 34;
            for (String line : lines) {
                VillageUiTheme.drawStringScaled(graphics, font,
                        compact(line, detailWidth - 24, 0.82f),
                        detailX + 12, y, INK, 0.82f);
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
                data.manageable() && !route.surveying(), mouseX, mouseY);
        drawActionButton(graphics, x + buttonWidth + gap, y, buttonWidth,
                Component.translatable(route.surveying()
                        ? "screen.village-quest.trade_route.survey_finish"
                        : "screen.village-quest.trade_route.survey_start").getString(),
                data.manageable(), mouseX, mouseY);
        boolean confirming = pendingRemovalRoute == route.routeIndex()
                && removalConfirmUntil >= System.currentTimeMillis();
        drawActionButton(graphics, x, y + 22, buttonWidth,
                Component.translatable(route.surveying()
                        ? "screen.village-quest.trade_route.survey_cancel"
                        : "screen.village-quest.trade_route.rename").getString(),
                data.manageable(), mouseX, mouseY);
        drawActionButton(graphics, x + buttonWidth + gap, y + 22, buttonWidth,
                Component.translatable(confirming
                        ? "screen.village-quest.trade_route.remove_confirm"
                        : "screen.village-quest.trade_route.remove").getString(),
                data.manageable(), mouseX, mouseY);
        graphics.fill(x+buttonWidth+gap,y+42,x+width,y+43,DANGEROUS);
        if(confirming) graphics.fill(x+buttonWidth+gap,y+22,x+buttonWidth+gap+3,y+40,DANGEROUS);
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int width, String label,
                                  boolean enabled, int mouseX, int mouseY) {
        VillageUiTheme.drawButton(graphics, font, x, y, width, 18, label, enabled,
                enabled && within(mouseX, mouseY, x, y, width, 18), false);
        if(!enabled && !data.manageable() && within(mouseX,mouseY,x,y,width,18))
            hints.setTooltipForNextFrame(font,Component.translatable("message.village-quest.trade_route.locked"),mouseX,mouseY);
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
                x + 24, y + 23, cardWidth - 48, INK, 0.85f, 4);
        y += 70;
        VillageUiTheme.drawCard(graphics, x, y, cardWidth, 66, false, false);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.trade_route.guide_routes").getString(),
                x + 24, y + 8, INK, 0.86f);
        VillageUiTheme.drawWrappedScaled(graphics, font,
                Component.translatable("screen.village-quest.trade_route.survey_tooltip").getString(),
                x + 24, y + 23, cardWidth - 128, INK, 0.85f, 4);
        VillageUiTheme.drawMarker(graphics, "home", x + cardWidth - 74, y + 31, 25);
        VillageUiTheme.drawMarker(graphics, "caravan", x + cardWidth - 38, y + 31, 23);
    }

    private void drawBondsView(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int cardWidth = 179;
        int cardHeight = 34;
        int start = bondPage * BONDS_PER_PAGE;
        int end = Math.min(data.bonds().size(), start + BONDS_PER_PAGE);
        if (data.bonds().isEmpty()) {
            VillageUiTheme.drawCard(graphics, left + VIEW_X + 8, top + VIEW_Y + 8,
                    VIEW_WIDTH - 16, 72, false, true);
            VillageUiTheme.drawWrappedScaled(graphics, font,
                    Component.translatable("screen.village-quest.trade_route.bonds_empty").getString(),
                    left + VIEW_X + 24, top + VIEW_Y + 26, VIEW_WIDTH - 48, MUTED, 0.78f, 4);
        }
        for (int i = start; i < end; i++) {
            Payloads.TradeRouteBondData bond = data.bonds().get(i);
            int visible = i - start;
            int column = visible / 4;
            int row = visible % 4;
            int x = left + VIEW_X + column * (cardWidth + 8);
            int y = top + VIEW_Y + 5 + row * (cardHeight + 4);
            boolean hovered = within(mouseX, mouseY, x, y, cardWidth, cardHeight);
            VillageUiTheme.drawCard(graphics, x, y, cardWidth, cardHeight, hovered, false);
            VillageUiTheme.drawMarker(graphics,
                    bond.lifeStatus() == LIFE_ABANDONED ? "danger" : "village",
                    x + 15, y + 17, 17);
            drawLifeBadge(graphics,x+20,y+20,bond.lifeStatus());
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(bond.type().getString(), cardWidth - 78, 0.78f), x + 29, y + 6, INK, 0.78f);
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(!bond.connected()
                                    ? Component.translatable("screen.village-quest.trade_route.bond_historical").getString()
                                    : bond.lifeStatus() == 0
                                    ? bond.condition().getString() + " · " + bond.need().getString()
                                    : lifeStatusLabel(bond.lifeStatus()).getString(),
                            cardWidth - 38, 0.62f), x + 29, y + 18, BODY, 0.62f);
            String level = bond.level().getString();
            VillageUiTheme.drawStringScaled(graphics, font, level,
                    x + cardWidth - font.width(level) * 0.68f - 7, y + 6, FLOURISHING, 0.68f);
            if (hovered) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(bond.type());
                tooltip.add(bond.identityOrigin());
                tooltip.add(bond.level());
                if (!bond.connected()) {
                    tooltip.add(Component.translatable(
                            "screen.village-quest.trade_route.bond_historical_detail"));
                    tooltip.add(lifeStatusLabel(bond.lifeStatus()));
                } else if (bond.lifeStatus() == 0) {
                    tooltip.add(lifeStatusLabel(bond.lifeStatus()));
                    tooltip.add(bond.condition());
                    tooltip.add(bond.need());
                    tooltip.add(bond.request());
                } else {
                    tooltip.add(lifeStatusLabel(bond.lifeStatus()));
                    tooltip.add(Component.translatable(
                            "screen.village-quest.village_life.resettle_hint"));
                }
                if (bond.connected()) tooltip.add(Component.translatable(
                        "screen.village-quest.trade_route.bond_supply",
                        bond.support(), 100, bond.energyProgress(), 3));
                tooltip.add(Component.translatable("screen.village-quest.trade_route.bond_coordinates",
                        bond.worldX(), bond.worldZ()));
                tooltip.add(Component.translatable("screen.village-quest.trade_route.bond_requests",
                        bond.completions()));
                hints.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }
        String summary = Component.translatable("screen.village-quest.trade_route.bonds_summary",
                data.bonds().size(), data.shrines().size()).getString();
        VillageUiTheme.drawStringScaled(graphics, font, summary,
                left + VIEW_X + 4, top + FOOTER_Y + 18, MUTED, 0.75f);
        int pages = Math.max(1, (data.bonds().size() + BONDS_PER_PAGE - 1) / BONDS_PER_PAGE);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.trade_route.bond_page",
                        bondPage + 1, pages).getString(),
                left + VIEW_X + VIEW_WIDTH - 111, top + FOOTER_Y + 18, MUTED, 0.72f);
        drawActionButton(graphics, left + VIEW_X + VIEW_WIDTH - 54, top + FOOTER_Y + 10,
                24, "<", bondPage > 0, mouseX, mouseY);
        drawActionButton(graphics, left + VIEW_X + VIEW_WIDTH - 26, top + FOOTER_Y + 10,
                24, ">", bondPage + 1 < pages, mouseX, mouseY);
    }

    private boolean handleBondsClick(int mouseX, int mouseY, int left, int top) {
        for(int n=0;n<BONDS_PER_PAGE;n++) {
            int index=bondPage*BONDS_PER_PAGE+n; if(index>=data.bonds().size()) break;
            int x=left+VIEW_X+(n/4)*187,y=top+VIEW_Y+5+(n%4)*38;
            if(within(mouseX,mouseY,x,y,179,34)) { var b=data.bonds().get(index); openVillage(b.worldX(),b.worldZ(),b.type()); return true; }
        }
        int y = top + FOOTER_Y + 10;
        if (within(mouseX, mouseY, left + VIEW_X + VIEW_WIDTH - 54, y, 24, 18)) {
            setBondPage(bondPage - 1);
            return true;
        }
        if (within(mouseX, mouseY, left + VIEW_X + VIEW_WIDTH - 26, y, 24, 18)) {
            setBondPage(bondPage + 1);
            return true;
        }
        return false;
    }

    private void setBondPage(int requested) {
        bondPage = Math.max(0, Math.min(requested,
                Math.max(0, (data.bonds().size() - 1) / BONDS_PER_PAGE)));
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
        if(VillageQuestClientConfig.get().showVillageMarkers() && within(mouseX,mouseY,left+VIEW_X,top+VIEW_Y,VIEW_WIDTH,VIEW_HEIGHT)) {
            Bounds bounds=displayBounds();
            for(var bond:data.bonds()) {
                Point p=pointFor(bond.worldX(),bond.worldZ(),bounds,left,top,false);
                if(Math.abs(mouseX-p.x)<=12 && Math.abs(mouseY-p.y)<=12) { openVillage(bond.worldX(),bond.worldZ(),bond.type()); return true; }
            }
            for(var node:data.nodes()) {
                Point p=pointFor(node.worldX(),node.worldZ(),bounds,left,top,false);
                if(Math.abs(mouseX-p.x)<=12 && Math.abs(mouseY-p.y)<=12) { openVillage(node.worldX(),node.worldZ(),node.name()); return true; }
            }
        }
        List<Payloads.TradeRouteLineData> routes = sortedRoutes();
        if(!routes.isEmpty() && within(mouseX,mouseY,left+VIEW_X,top+FOOTER_Y,VIEW_WIDTH-82,CHIP_HEIGHT)) {
            int current=0;for(int i=0;i<routes.size();i++)if(routes.get(i).routeIndex()==selectedRoute)current=i;
            selectedRoute=routes.get((current+1)%routes.size()).routeIndex();
            return true;
        }
        int manageX = left + VIEW_X + VIEW_WIDTH - 72;
        if (data.manageable() && !data.routes().isEmpty() && within(mouseX, mouseY, manageX, top + FOOTER_Y, 72, CHIP_HEIGHT)) {
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
        if (route == null || !data.manageable()) {
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
        UiTestTour.send(new Payloads.TradeRouteActionPayload(action, routeIndex));
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
        if (data.nodes().isEmpty() && data.bonds().isEmpty()) {
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

    private void openVillage(int x,int z,Component title) {
        villageX=x; villageZ=z; villageTitle=title; villagePage=0; villageOpen=true; mapDragging=false;
    }
    private Payloads.TradeRouteLineData villageRoute() {
        return data.nodes().stream().filter(n -> !n.home() && n.worldX()==villageX && n.worldZ()==villageZ)
                .flatMap(n -> data.routes().stream().filter(r -> r.routeIndex()==n.nodeIndex()-1)).findFirst().orElse(null);
    }
    List<String> villageLines() {
        var paragraphs=new ArrayList<String>();
        paragraphs.add(Component.translatable("screen.village-quest.trade_route.bond_coordinates",villageX,villageZ).getString());
        var bond=data.bonds().stream().filter(b -> b.worldX()==villageX && b.worldZ()==villageZ).findFirst();
        if(bond.isPresent()) {
            var b=bond.get(); if(b.details().isEmpty()) paragraphs.add(lifeStatusLabel(b.lifeStatus()).getString());
            paragraphs.add(Component.translatable("screen.village-quest.trade_route.bond_recognition",b.level()).getString());
            if(b.connected() && b.lifeStatus()==0) {
                paragraphs.add(b.condition().getString()); paragraphs.add(b.need().getString()); paragraphs.add(b.request().getString());
                paragraphs.add(Component.translatable("screen.village-quest.trade_route.bond_supply",b.support(),100,b.energyProgress(),3).getString());
            } else paragraphs.add(Component.translatable("screen.village-quest.trade_route."+(!b.connected()?"bond_not_connected":"bond_inactive")).getString());
            b.details().forEach(c -> paragraphs.add(c.getString()));
        }
        var route=villageRoute();
        boolean home=data.nodes().stream().anyMatch(n -> n.home() && n.worldX()==villageX && n.worldZ()==villageZ);
        if(home) {
            paragraphs.add(data.summary().getString());
            data.routes().forEach(r -> paragraphs.add(r.name().getString()+" · "+(r.availabilityLabel().getString().isBlank()?r.statusLabel():r.availabilityLabel()).getString()));
        } else paragraphs.add(route==null?Component.translatable("screen.village-quest.trade_route.village_no_route").getString():route.name().getString());
        if(route!=null) {
            paragraphs.add((route.availabilityLabel().getString().isBlank()?route.statusLabel():route.availabilityLabel()).getString());
            paragraphs.add(Component.translatable("screen.village-quest.trade_route.route_stats_short",route.roadQuality(),route.waypoints().size()).getString());
        }
        return BuildingScreen.travelLines(font,paragraphs);
    }
    private int villagePages() { return Math.max(1,(villageLines().size()+12)/13); }
    private void drawVillageDetails(GuiGraphics g,int l,int t,int mx,int my) {
        hints.clear();
        KingdomUiAssets.panel(g,l,t,WINDOW_WIDTH,WINDOW_HEIGHT);
        KingdomUiAssets.header(g,font,villageTitle.getString(),l,t,WINDOW_WIDTH);
        KingdomUiAssets.detailIcon(g,KingdomUiAssets.DetailIcon.FOUNDING,l+26,t+9,28);
        KingdomUiAssets.card(g,l+27,t + 36,378,157,false);
        var lines=villageLines(); villagePage=Math.min(villagePage,villagePages()-1);
        for(int i=0;i<13 && villagePage*13+i<lines.size();i++) VillageUiTheme.drawStringScaled(g,font,
                lines.get(villagePage*13+i),l+38,t + 44+i*11,INK,.8f);
        KingdomUiAssets.closeButton(g,l,t,WINDOW_WIDTH,WINDOW_HEIGHT,KingdomUiAssets.closeBounds(l,t,WINDOW_WIDTH,WINDOW_HEIGHT).contains(mx,my));
        if(KingdomUiAssets.closeBounds(l,t,WINDOW_WIDTH,WINDOW_HEIGHT).contains(mx,my)) hints.setTooltipForNextFrame(font,Component.translatable("screen.village-quest.building.back"),mx,my);
        drawActionButton(g,l+31,t + 198,120,Component.translatable("screen.village-quest.trade_route.manage_route").getString(),villageRoute()!=null && data.manageable(),mx,my);
        if(villageRoute()==null || !data.manageable()) {
            var reason=Component.translatable("screen.village-quest.trade_route."+(villageRoute()==null?"manage_no_route":"read_only"));
            VillageUiTheme.drawWrappedScaled(g,font,reason.getString(),l+160,t + 200,143,MUTED,.65f,3);
            if(within(mx,my,l+31,t + 198,120,18)) hints.setTooltipForNextFrame(font,reason,mx,my);
        }
        drawActionButton(g,l+315,t + 198,24,"<",villagePage>0,mx,my);
        VillageUiTheme.drawStringScaled(g,font,(villagePage+1)+"/"+villagePages(),l+343,t + 204,MUTED,.7f);
        drawActionButton(g,l+373,t + 198,24,">",villagePage+1<villagePages(),mx,my);
    }

    private Payloads.TradeRouteLineData selectedRoute() {
        return data.routes().stream().filter(route -> route.routeIndex() == selectedRoute)
                .findFirst().orElse(null);
    }

    private Payloads.TradeRouteNodeData node(int index) {
        return data.nodes().stream().filter(node -> node.nodeIndex() == index).findFirst().orElse(null);
    }

    private void drawLifeBadge(GuiGraphics graphics,int x,int y,int status) {
        int color=status==LIFE_ABANDONED?DANGEROUS:status==LIFE_RECOVERING?SUSPENDED:SECURED;
        String glyph=status==LIFE_ABANDONED?"!":status==LIFE_RECOVERING?"~":"+";
        graphics.fill(x-1,y-1,x+8,y+9,PAPER);
        VillageUiTheme.drawStringScaled(graphics,font,glyph,x,y,color,.8f);
    }
    private static Component lifeStatusLabel(int status) {
        return Component.translatable("screen.village-quest.trade_route.saved_village_status",Component.translatable(status == LIFE_ABANDONED
                ? "screen.village-quest.village_life.abandoned"
                : status == LIFE_RECOVERING
                ? "screen.village-quest.village_life.recovering"
                : "screen.village-quest.village_life.active"));
    }

    private List<Component> routeTooltip(Payloads.TradeRouteLineData route) {
        List<Component> tooltip = new ArrayList<>();
        addWrappedTooltip(tooltip, route.name());
        addWrappedTooltip(tooltip, Component.translatable(route.paused()
                ? "screen.village-quest.trade_route.state.paused"
                : "screen.village-quest.trade_route.state.running"));
        addWrappedTooltip(tooltip, route.statusLabel());
        addWrappedTooltip(tooltip, route.availabilityLabel());
        addWrappedTooltip(tooltip, Component.translatable(
                "screen.village-quest.trade_route.quality_short", route.roadQuality()));
        addWrappedTooltip(tooltip, Component.translatable(
                "screen.village-quest.trade_route.waypoints", route.waypoints().size()));
        addWrappedTooltip(tooltip, Component.translatable(
                "screen.village-quest.trade_route.distance_direct", route.straightBlocks()));
        addWrappedTooltip(tooltip, Component.translatable(route.surveying()
                        ? "screen.village-quest.trade_route.distance_draft"
                        : "screen.village-quest.trade_route.distance_path", route.surveyedBlocks()));
        addWrappedTooltip(tooltip, Component.translatable(
                "screen.village-quest.trade_route.travel_estimate",
                route.estimatedBlocks(), route.estimatedMinutes(), route.expectedLegs()));
        if (route.unusuallyLong()) {
            addWrappedTooltip(tooltip, Component.translatable(
                    "screen.village-quest.trade_route.long_warning"));
        }
        long ferryPoints = route.waypoints().stream().filter(Payloads.TradeRoutePointData::ocean).count();
        if (ferryPoints > 0) {
            addWrappedTooltip(tooltip, Component.translatable(
                    "screen.village-quest.trade_route.ferry_points", ferryPoints));
        }
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.trade_route.specialization",
                route.specializationLabel()));
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.trade_route.upgrades",
                route.upgradeSummary()));
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.caravan_crew.master",
                de.quest.caravan.NpcNames.component(route.masterName())));
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.caravan_crew.trader",
                de.quest.caravan.NpcNames.component(route.traderName())));
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.caravan_crew.guard",
                de.quest.caravan.NpcNames.component(route.guardName())));
        addWrappedTooltip(tooltip, Component.translatable("screen.village-quest.caravan_crew.courier",
                de.quest.caravan.NpcNames.component(route.courierName())));
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
        UiTestTour.navigate(new ChatScreen("/vq routes rename " + (route.routeIndex() + 1) + " ", false));
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
