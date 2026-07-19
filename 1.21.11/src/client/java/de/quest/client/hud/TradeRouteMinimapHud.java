package de.quest.client.hud;

import de.quest.VillageQuest;
import de.quest.client.screen.TradeRouteMapScreen;
import de.quest.client.ui.SurfaceMapRenderer;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Compact, non-pausing overview of the player's registered trade network. */
public final class TradeRouteMinimapHud {
    private static final Identifier HUD_LAYER_ID =
            Identifier.of(VillageQuest.MOD_ID, "trade_route_minimap");
    private static final int WIDTH = 120;
    private static final int HEIGHT = 94;
    private static final int MAP_X = 5;
    private static final int MAP_Y = 18;
    private static final int MAP_WIDTH = 110;
    private static final int MAP_HEIGHT = 61;
    private static final int FRAME = 0xE04F321B;
    private static final int PAPER = 0xE8E0C281;
    private static final int INK = 0xFF3E2918;
    private static final int HOME = 0xFFB17625;
    private static final int VILLAGE = 0xFF6B4C2E;
    private static final int DANGEROUS = 0xFFC34835;
    private static final int SECURED = 0xFF4D7B4A;
    private static final int FLOURISHING = 0xFF2E8C72;
    private static final int CARAVAN = 0xFFFFD45A;
    private static final int PLAYER = 0xFF5FE7FF;
    private static final int[] ROUTE_COLORS = {
            0xFFB9574E, 0xFF3E927B, 0xFF4E78A8, 0xFFB78936, 0xFF7B609D
    };

    private static Payloads.TradeRouteMapPayload data;
    private static boolean enabled;
    private static KeyBinding toggleKey;

    private TradeRouteMinimapHud() {}

    public static void register() {
        KeyBinding.Category category = KeyBinding.Category.create(
                Identifier.of(VillageQuest.MOD_ID, "trade_routes"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.village-quest.trade_route_minimap", GLFW.GLFW_KEY_COMMA, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                if (client.player != null && ClientPlayNetworking.canSend(Payloads.TradeRouteActionPayload.ID)) {
                    ClientPlayNetworking.send(new Payloads.TradeRouteActionPayload(
                            Payloads.TradeRouteActionPayload.ACTION_MINIMAP_TOGGLE, -1));
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disable());
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    public static void enable(Payloads.TradeRouteMapPayload payload) {
        data = payload;
        enabled = true;
    }

    public static void update(Payloads.TradeRouteMapPayload payload) {
        data = payload;
    }

    public static void disable() {
        enabled = false;
        data = null;
        SurfaceMapRenderer.clear();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void render(DrawContext graphics) {
        MinecraftClient client = MinecraftClient.getInstance();
        Payloads.TradeRouteMapPayload current = data;
        if (!enabled || current == null || client.player == null || client.options.hudHidden
                || client.currentScreen instanceof TradeRouteMapScreen) {
            return;
        }

        TextRenderer font = client.textRenderer;
        int left = 8;
        int top = 8;
        graphics.fill(left + 2, top + 2, left + WIDTH + 4, top + HEIGHT + 4, 0x70000000);
        graphics.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, 0xEE21140D);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFFB5792E);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFFF0D99F);
        VillageUiTheme.blitScaled(graphics, VillageUiTheme.control("button_normal"),
                left + 3, top + 2, WIDTH - 6, 15, 120, 32);
        String title = Text.translatable("hud.village-quest.trade_route_minimap").getString();
        float titleScale = 0.75f;
        VillageUiTheme.drawStringScaled(graphics, font, title,
                left + (WIDTH - font.getWidth(title) * titleScale) / 2.0f,
                top + 5, VillageUiTheme.LIGHT_TEXT, titleScale);
        graphics.fill(left + MAP_X - 1, top + MAP_Y - 1,
                left + MAP_X + MAP_WIDTH + 1, top + MAP_Y + MAP_HEIGHT + 1, 0xFF684223);
        graphics.fill(left + MAP_X, top + MAP_Y,
                left + MAP_X + MAP_WIDTH, top + MAP_Y + MAP_HEIGHT, PAPER);

        if (current.nodes().isEmpty()) {
            String empty = Text.translatable("screen.village-quest.trade_route.empty").getString();
            graphics.drawText(font, empty, left + (WIDTH - font.getWidth(empty)) / 2,
                    top + MAP_Y + MAP_HEIGHT / 2, 0xFF80694F, false);
            return;
        }

        Bounds bounds = localBounds(client);
        SurfaceMapRenderer.drawHud(graphics, left + MAP_X, top + MAP_Y, MAP_WIDTH, MAP_HEIGHT,
                bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
        graphics.enableScissor(left + MAP_X, top + MAP_Y,
                left + MAP_X + MAP_WIDTH, top + MAP_Y + MAP_HEIGHT);
        Payloads.TradeRouteNodeData home = current.nodes().stream()
                .filter(Payloads.TradeRouteNodeData::home).findFirst().orElse(null);
        if (home == null) {
            graphics.disableScissor();
            return;
        }
        List<Payloads.TradeRouteLineData> routes = current.routes().stream()
                .sorted(Comparator.comparingInt(Payloads.TradeRouteLineData::routeIndex)).toList();
        int nearby = 0;
        boolean hasEvent = false;
        for (Payloads.TradeRouteLineData route : routes) {
            Payloads.TradeRouteNodeData destination = node(current, route.routeIndex() + 1);
            if (destination == null) {
                continue;
            }
            List<WorldPoint> path = routePath(home, destination, route);
            for (int i = 1; i < path.size(); i++) {
                Point from = pointFor(path.get(i - 1).x(), path.get(i - 1).z(), bounds, left, top, true);
                Point to = pointFor(path.get(i).x(), path.get(i).z(), bounds, left, top, true);
                drawLine(graphics, from.x(), from.y(), to.x(), to.y(),
                        routeColorByIndex(route.routeIndex()));
            }
            Payloads.TradeRouteCaravanData caravan = current.caravans().stream()
                    .filter(value -> value.routeIndex() == route.routeIndex()).findFirst().orElse(null);
            if (caravan != null) {
                WorldPoint caravanWorld = pointAlong(path, caravan.progress());
                Point point = pointFor(caravanWorld.x(), caravanWorld.z(), bounds, left, top, true);
                boolean event = !route.eventLabel().getString().isEmpty();
                hasEvent |= event;
                if (caravan.materialized()) {
                    nearby++;
                }
                graphics.fill(point.x() - 2, point.y() - 2, point.x() + 3, point.y() + 3,
                        event ? DANGEROUS : FRAME);
                graphics.fill(point.x() - 1, point.y() - 1, point.x() + 2, point.y() + 2,
                        caravan.materialized() ? routeColorByIndex(route.routeIndex()) : PAPER);
            }
        }

        for (Payloads.TradeRouteNodeData node : current.nodes()) {
            Point point = pointFor(node.worldX(), node.worldZ(), bounds, left, top, true);
            graphics.fill(point.x() - 2, point.y() - 2, point.x() + 3, point.y() + 3, FRAME);
            graphics.fill(point.x() - 1, point.y() - 1, point.x() + 2, point.y() + 2,
                    node.home() ? HOME : VILLAGE);
        }

        Point player = pointFor(client.player.getX(), client.player.getZ(), bounds, left, top, true);
        graphics.fill(player.x() - 3, player.y(), player.x() + 4, player.y() + 1, FRAME);
        graphics.fill(player.x(), player.y() - 3, player.x() + 1, player.y() + 4, FRAME);
        graphics.fill(player.x() - 2, player.y(), player.x() + 3, player.y() + 1, PLAYER);
        graphics.fill(player.x(), player.y() - 2, player.x() + 1, player.y() + 3, PLAYER);
        graphics.disableScissor();

        String footer = Text.translatable("hud.village-quest.trade_route_minimap.footer",
                routes.size(), nearby, client.player.getBlockX(), client.player.getBlockZ()).getString();
        if (font.getWidth(footer) > WIDTH - 10) {
            footer = Text.translatable("hud.village-quest.trade_route_minimap.footer_short",
                    routes.size(), nearby).getString();
        }
        int footerX = left + 5;
        if (hasEvent) {
            graphics.drawText(font, "!", footerX, top + HEIGHT - 11, DANGEROUS, false);
            footerX += font.getWidth("!") + 3;
        }
        graphics.drawText(font, footer, footerX, top + HEIGHT - 11, INK, false);
    }

    private static Bounds localBounds(MinecraftClient client) {
        int radius = 192;
        int centerX = Math.floorDiv(client.player.getBlockX(), 8) * 8;
        int centerZ = Math.floorDiv(client.player.getBlockZ(), 8) * 8;
        return new Bounds(centerX - radius, centerX + radius, centerZ - radius, centerZ + radius);
    }

    private static Payloads.TradeRouteNodeData node(Payloads.TradeRouteMapPayload current, int index) {
        return current.nodes().stream().filter(node -> node.nodeIndex() == index).findFirst().orElse(null);
    }

    private static List<WorldPoint> routePath(Payloads.TradeRouteNodeData home,
                                              Payloads.TradeRouteNodeData destination,
                                              Payloads.TradeRouteLineData route) {
        List<WorldPoint> path = new ArrayList<>(route.waypoints().size() + 2);
        path.add(new WorldPoint(home.worldX(), home.worldZ()));
        for (Payloads.TradeRoutePointData waypoint : route.waypoints()) {
            path.add(new WorldPoint(waypoint.worldX(), waypoint.worldZ()));
        }
        path.add(new WorldPoint(destination.worldX(), destination.worldZ()));
        return path;
    }

    private static WorldPoint pointAlong(List<WorldPoint> path, int progress) {
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
            double distance = from.distance(to);
            if (remaining <= distance) {
                double factor = remaining / distance;
                return new WorldPoint(from.x() + (to.x() - from.x()) * factor,
                        from.z() + (to.z() - from.z()) * factor);
            }
            remaining -= distance;
        }
        return path.getLast();
    }

    private static Point pointFor(double worldX, double worldZ, Bounds bounds,
                                  int left, int top, boolean clamp) {
        int padding = 7;
        double nx = (worldX - bounds.minX()) / (double) (bounds.maxX() - bounds.minX());
        double nz = (worldZ - bounds.minZ()) / (double) (bounds.maxZ() - bounds.minZ());
        if (clamp) {
            nx = Math.max(0.0, Math.min(1.0, nx));
            nz = Math.max(0.0, Math.min(1.0, nz));
        }
        int x = left + MAP_X + padding + (int) Math.round(nx * (MAP_WIDTH - padding * 2));
        int y = top + MAP_Y + padding + (int) Math.round(nz * (MAP_HEIGHT - padding * 2));
        return new Point(x, y);
    }

    private static void drawLine(DrawContext graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
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

    private static int routeColor(int status) {
        return switch (status) {
            case 3 -> FLOURISHING;
            case 2 -> SECURED;
            default -> DANGEROUS;
        };
    }

    private static int routeColorByIndex(int routeIndex) {
        return ROUTE_COLORS[Math.floorMod(routeIndex, ROUTE_COLORS.length)];
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {}

    private record Point(int x, int y) {}

    private record WorldPoint(double x, double z) {
        private double distance(WorldPoint other) {
            double dx = other.x - x;
            double dz = other.z - z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }
}
