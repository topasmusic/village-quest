package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.InventoryJournalTutorialState;
import de.quest.client.ui.TutorialHintRenderer;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import de.quest.network.Payloads.JournalActionPayload;
import de.quest.quest.special.RelicQuestProgressionService;
import de.quest.registry.ModItems;
import de.quest.reputation.ReputationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Compact, tabbed journal. The old linear book could exceed fourteen pages and made
 * reference material compete with the player's active quests. This view keeps the
 * same data, but reveals details only when the player expands a card.
 */
public class JournalScreen extends CompatScreen {
    private static final Identifier BOARD_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/journal_board.png");
    private static final Identifier ATLAS_FRAME_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_atlas_frame.png");
    private static final Identifier ATLAS_PATH_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_path_map.png");
    private static final Identifier ATLAS_CHARTERS_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_charters_map.png");
    private static final Identifier ATLAS_TRUST_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_trust_roster.png");
    private static final int WINDOW_WIDTH = 416;
    private static final int WINDOW_HEIGHT = 234;
    private static final int TAB_X = 22;
    private static final int TAB_Y = 30;
    private static final int TAB_WIDTH = 45;
    private static final int TAB_HEIGHT = 31;
    private static final int TAB_GAP = 2;
    private static final int CONTENT_X = 76;
    private static final int CONTENT_Y = 40;
    private static final int CONTENT_WIDTH = 313;
    private static final int CONTENT_TOP = 56;
    private static final int CONTENT_BOTTOM = 197;
    private static final int CARD_GAP = 5;
    private static final int CARD_COLLAPSED_HEIGHT = 31;
    private static final int CARD_TEXT_INSET = 20;
    private static final float CARD_TITLE_SCALE = 0.84f;
    private static final float CARD_BODY_SCALE = 0.72f;
    private static final int CARD_DETAIL_STEP = 8;
    private static final int REPUTATION_PROGRESS_HEIGHT = 19;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_Y = 203;
    private static final int DONE_BUTTON_WIDTH = 78;
    private static final int FOOTER_RIGHT_INSET = 24;
    private static final int HEADER_WALLET_RIGHT_INSET = 40;
    private static final int HEADER_WALLET_TOP = 10;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF8A7661;
    private static final int FRAME_DARK = 0xFF4C2C19;
    private static final int FRAME_LIGHT = 0xFFB88943;
    private static final int PAPER = 0xFFF5E7C7;
    private static final int PAPER_HOVER = 0xFFFFF0D5;
    private static final int PAPER_EXPANDED = 0xFFFFE8B8;
    private static final int TEAL = 0xFF236B68;
    private static final int TEAL_HOVER = 0xFF2E8580;
    private static final int GOLD = 0xFF9A6620;
    private static final int GREEN = 0xFF47713F;
    private static final int BLUE = 0xFF3F667F;
    private static final int RED = 0xFF9B4337;
    private static final int PURPLE = 0xFF725083;
    private static final int SCROLL_TRACK = 0x335B3C21;
    private static final int SCROLL_THUMB = 0xAA7A522B;
    private static final int ATLAS_MAP_X = 8;
    private static final int ATLAS_MAP_Y = 18;
    private static final int ATLAS_MAP_WIDTH = 400;
    private static final int ATLAS_MAP_HEIGHT = 207;
    private static final int ATLAS_RENDER_WIDTH = 780;
    private static final int ATLAS_RENDER_HEIGHT = 390;
    private static final int ATLAS_TEXTURE_WIDTH = 1774;
    private static final int ATLAS_TEXTURE_HEIGHT = 887;
    private static final int ATLAS_MARKER_SIZE = 25;
    private static final int ATLAS_DETAIL_WIDTH = 150;
    private static final int ATLAS_DETAIL_HEIGHT = 88;
    private static final int ATLAS_INTRO_X = 104;
    private static final int ATLAS_INTRO_Y = 65;
    private static final int ATLAS_INTRO_WIDTH = 224;
    private static final int ATLAS_INTRO_HEIGHT = 100;
    private static final long ATLAS_HINT_DURATION_MS = 4200L;
    private static final int TRUST_LIST_X = 80;
    private static final int TRUST_LIST_Y = 57;
    private static final int TRUST_LIST_WIDTH = 275;
    private static final int TRUST_ROW_HEIGHT = 25;
    private static final int TRUST_ROW_STEP = 26;
    private static final int TRUST_ICON_X = 84;
    private static final int TRUST_ICON_SIZE = 24;
    private static final int TRUST_TEXT_X = 120;
    private static final int TRUST_PROGRESS_X = 256;
    private static final int TRUST_PROGRESS_WIDTH = 78;
    private static final int CHARTER_MARKER_SIZE = 30;
    private static final Map<String, Landmark> PATH_LANDMARKS = Map.ofEntries(
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
    private static final Map<String, Landmark> CHARTER_LANDMARKS = Map.ofEntries(
            Map.entry("village_ledger", new Landmark(0.155f, 0.735f)),
            Map.entry("apiary_charter", new Landmark(0.165f, 0.475f)),
            Map.entry("forge_charter", new Landmark(0.275f, 0.190f)),
            Map.entry("market_charter", new Landmark(0.500f, 0.455f)),
            Map.entry("pasture_charter", new Landmark(0.505f, 0.760f)),
            Map.entry("watch_bell", new Landmark(0.610f, 0.170f)),
            Map.entry("caravan_yard", new Landmark(0.825f, 0.715f)),
            Map.entry("wayshrine_network", new Landmark(0.835f, 0.195f))
    );
    private static final Map<String, Landmark> TRUST_LANDMARKS = Map.of(
            "farming", new Landmark(0.175f, 0.235f),
            "crafting", new Landmark(0.815f, 0.245f),
            "animals", new Landmark(0.180f, 0.705f),
            "trade", new Landmark(0.815f, 0.715f),
            "monster_hunting", new Landmark(0.500f, 0.165f)
    );

    private enum Section {
        OVERVIEW("screen.village-quest.journal.v2.tab.overview"),
        QUESTS("screen.village-quest.journal.v2.tab.quests"),
        ATLAS("screen.village-quest.journal.v2.tab.atlas"),
        GUIDE("screen.village-quest.journal.v2.tab.guide");

        private final String key;

        Section(String key) {
            this.key = key;
        }
    }

    private enum AtlasPage {
        PATH("screen.village-quest.guild_atlas.page.path", ATLAS_PATH_TEXTURE),
        CHARTERS("screen.village-quest.guild_atlas.page.charters", ATLAS_CHARTERS_TEXTURE),
        TRUST("screen.village-quest.guild_atlas.page.trust", ATLAS_TRUST_TEXTURE);

        private final String key;
        private final Identifier texture;

        AtlasPage(String key, Identifier texture) {
            this.key = key;
            this.texture = texture;
        }
    }

    private record AtlasNode(
            String id,
            ItemStack previewStack,
            Component title,
            Component ability,
            Component requirement,
            int status,
            Landmark landmark,
            AtlasEmblem emblem
    ) {
        private AtlasNode(String id, ItemStack previewStack, Component title, Component ability,
                          Component requirement, int status, Landmark landmark) {
            this(id, previewStack, title, ability, requirement, status, landmark, AtlasEmblem.ITEM);
        }
    }

    private enum AtlasEmblem {
        ITEM(null),
        CHARTER_LEDGER("charters/village_ledger"),
        CHARTER_APIARY("charters/apiary"),
        CHARTER_FORGE("charters/forge"),
        CHARTER_MARKET("charters/market"),
        CHARTER_PASTURE("charters/pasture"),
        CHARTER_WATCH("charters/watch"),
        CHARTER_CARAVAN("charters/caravan_yard"),
        CHARTER_WAYSHRINE("charters/wayshrine_network"),
        FARMING("trust/farming"),
        CRAFTING("trust/crafting"),
        ANIMALS("trust/animals"),
        TRADE("trust/trade"),
        WARDEN("trust/road_warden");

        private final Identifier texture;

        AtlasEmblem(String textureName) {
            this.texture = textureName == null ? null : Identifier.fromNamespaceAndPath(
                    VillageQuest.MOD_ID, "textures/gui/" + textureName + ".png");
        }
    }

    private record AtlasDetailPlacement(int x, int y) {}

    private record Landmark(float x, float y) {}

    private record JournalCard(
            String id,
            Component title,
            Component subtitle,
            List<Component> details,
            int accent,
            int cancelAction
    ) {}

    private record SpecialItemEntry(String nameKey, String loreKey) {}

    private record ProjectEntry(String keyPrefix, boolean unlocked) {}

    private record ReputationProgress(int current, int floor, int target, boolean complete) {}

    public static class JournalData {
        public final int total;
        public final int discovered;
        public final int completed;
        public final int active;
        public final long currencyBalance;
        public final int farmingReputation;
        public final int craftingReputation;
        public final int animalReputation;
        public final int tradeReputation;
        public final int monsterReputation;
        public final boolean hasStarreachRing;
        public final boolean hasMerchantSeal;
        public final boolean hasShepherdFlute;
        public final boolean hasApiaristSmoker;
        public final boolean hasSurveyorCompass;
        public final boolean hasCaravanLedger;
        public final boolean dailyActive;
        public final Component dailyTitle;
        public final Component dailyProgress;
        public final boolean weeklyActive;
        public final Component weeklyTitle;
        public final Component weeklyProgress;
        public final boolean storyActive;
        public final Component storyTitle;
        public final Component storyProgress;
        public final boolean pilgrimActive;
        public final Component pilgrimTitle;
        public final Component pilgrimProgress;
        public final boolean specialActive;
        public final Component specialTitle;
        public final Component specialProgress;
        public final boolean hasVillageLedgerProject;
        public final boolean hasApiaryCharterProject;
        public final boolean hasForgeCharterProject;
        public final boolean hasMarketCharterProject;
        public final boolean hasPastureCharterProject;
        public final boolean hasWatchBellProject;
        public final boolean hasCaravanYardProject;
        public final boolean hasWayshrineNetworkProject;
        public final List<Payloads.GuildPathNodeData> guildPathNodes;

        public JournalData(
                int total, int discovered, int completed, int active, long currencyBalance,
                int farmingReputation, int craftingReputation, int animalReputation,
                int tradeReputation, int monsterReputation,
                boolean hasStarreachRing, boolean hasMerchantSeal, boolean hasShepherdFlute,
                boolean hasApiaristSmoker, boolean hasSurveyorCompass, boolean hasCaravanLedger,
                boolean dailyActive, Component dailyTitle, Component dailyProgress,
                boolean weeklyActive, Component weeklyTitle, Component weeklyProgress,
                boolean storyActive, Component storyTitle, Component storyProgress,
                boolean pilgrimActive, Component pilgrimTitle, Component pilgrimProgress,
                boolean specialActive, Component specialTitle, Component specialProgress,
                boolean hasVillageLedgerProject, boolean hasApiaryCharterProject,
                boolean hasForgeCharterProject, boolean hasMarketCharterProject,
                boolean hasPastureCharterProject, boolean hasWatchBellProject,
                boolean hasCaravanYardProject, boolean hasWayshrineNetworkProject,
                List<Payloads.GuildPathNodeData> guildPathNodes
        ) {
            this.total = total;
            this.discovered = discovered;
            this.completed = completed;
            this.active = active;
            this.currencyBalance = currencyBalance;
            this.farmingReputation = farmingReputation;
            this.craftingReputation = craftingReputation;
            this.animalReputation = animalReputation;
            this.tradeReputation = tradeReputation;
            this.monsterReputation = monsterReputation;
            this.hasStarreachRing = hasStarreachRing;
            this.hasMerchantSeal = hasMerchantSeal;
            this.hasShepherdFlute = hasShepherdFlute;
            this.hasApiaristSmoker = hasApiaristSmoker;
            this.hasSurveyorCompass = hasSurveyorCompass;
            this.hasCaravanLedger = hasCaravanLedger;
            this.dailyActive = dailyActive;
            this.dailyTitle = safe(dailyTitle);
            this.dailyProgress = safe(dailyProgress);
            this.weeklyActive = weeklyActive;
            this.weeklyTitle = safe(weeklyTitle);
            this.weeklyProgress = safe(weeklyProgress);
            this.storyActive = storyActive;
            this.storyTitle = safe(storyTitle);
            this.storyProgress = safe(storyProgress);
            this.pilgrimActive = pilgrimActive;
            this.pilgrimTitle = safe(pilgrimTitle);
            this.pilgrimProgress = safe(pilgrimProgress);
            this.specialActive = specialActive;
            this.specialTitle = safe(specialTitle);
            this.specialProgress = safe(specialProgress);
            this.hasVillageLedgerProject = hasVillageLedgerProject;
            this.hasApiaryCharterProject = hasApiaryCharterProject;
            this.hasForgeCharterProject = hasForgeCharterProject;
            this.hasMarketCharterProject = hasMarketCharterProject;
            this.hasPastureCharterProject = hasPastureCharterProject;
            this.hasWatchBellProject = hasWatchBellProject;
            this.hasCaravanYardProject = hasCaravanYardProject;
            this.hasWayshrineNetworkProject = hasWayshrineNetworkProject;
            this.guildPathNodes = guildPathNodes == null ? List.of() : List.copyOf(guildPathNodes);
        }

        public boolean hasAnySpecialItem() {
            return hasStarreachRing || hasMerchantSeal || hasShepherdFlute || hasApiaristSmoker
                    || hasSurveyorCompass || hasCaravanLedger;
        }

        private static Component safe(Component value) {
            return value == null ? Component.empty() : value;
        }
    }

    private JournalData data;
    private Section section = Section.OVERVIEW;
    private String expandedCardId = "overview_progress";
    private int scrollOffset;
    private int scrollMax;
    private boolean closeNotified;
    private boolean navigating;
    private AtlasPage atlasPage = AtlasPage.PATH;
    private final double[] atlasOffsetX = new double[AtlasPage.values().length];
    private final double[] atlasOffsetY = new double[AtlasPage.values().length];
    private final boolean[] atlasInitialized = new boolean[AtlasPage.values().length];
    private boolean atlasDragging;
    private double atlasDragDistance;
    private int atlasHoveredNode = -1;
    private boolean atlasHintChecked;
    private long atlasHintUntilMs;
    private boolean atlasIntroChecked;
    private boolean atlasIntroVisible;
    public JournalScreen(JournalData data) {
        super(Component.translatable("screen.village-quest.journal.title"));
        this.data = data;
    }

    public void updateData(JournalData data) {
        this.data = data;
        clampScroll();
    }

    @Override
    protected void init() {
        this.closeNotified = false;
        this.navigating = false;
        this.scrollOffset = 0;
        this.atlasDragging = false;
        this.atlasHoveredNode = -1;
        this.atlasHintChecked = false;
        this.atlasHintUntilMs = 0L;
        this.atlasIntroChecked = false;
        this.atlasIntroVisible = false;
        ensureExpandedCard();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(key)) {
            onClose();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void onClose() {
        if (!this.closeNotified && !this.navigating) {
            this.closeNotified = true;
            sendJournalClose();
        }
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
            if (section == Section.ATLAS) {
                drawAtlas(graphics, left, top, uiMouseX, uiMouseY);
            } else {
                drawJournalBackground(graphics, left, top);
                drawHeader(graphics, left, top);
                drawTabs(graphics, left, top, uiMouseX, uiMouseY);
                drawCards(graphics, left, top, uiMouseX, uiMouseY);
                drawFooterButtons(graphics, left, top, uiMouseX, uiMouseY);
            }
            super.render(graphics, uiMouseX, uiMouseY, delta);

            if (shouldShowQuestMasterTutorial()) {
                int buttonX = left + WINDOW_WIDTH - FOOTER_RIGHT_INSET - DONE_BUTTON_WIDTH - 84;
                TutorialHintRenderer.drawHint(
                        graphics, font,
                        Component.translatable("screen.village-quest.journal.active.questmaster_button_tutorial"),
                        width, height, buttonX, top + BUTTON_Y, 78, BUTTON_HEIGHT,
                        TutorialHintRenderer.Placement.ABOVE, true,
                        (int) Math.round(Math.sin(System.currentTimeMillis() / 180.0d) * 2.0d)
                );
            }
        } finally {
            endResponsivePanel(graphics, panelScale);
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        String titleText = title.getString();
        graphics.drawString(font, titleText, left + (WINDOW_WIDTH - font.width(titleText)) / 2,
                top + 14, INK, false);
        String sectionText = Component.translatable(section.key).getString();
        graphics.drawString(font, sectionText, left + CONTENT_X + 7, top + CONTENT_Y + 3, GOLD, false);
        VillageUiTheme.drawWalletStrip(graphics, font, left, top, WINDOW_WIDTH, data.currencyBalance,
                HEADER_WALLET_RIGHT_INSET, HEADER_WALLET_TOP);
    }

    private void drawTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String[] icons = {"home", "quests", "guide", "story"};
        Section[] sections = Section.values();
        for (int i = 0; i < sections.length; i++) {
            Section candidate = sections[i];
            int x = left + TAB_X;
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            boolean selected = candidate == section;
            boolean hovered = within(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
            VillageUiTheme.drawTab(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, selected, hovered);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon(icons[i]),
                    x + (TAB_WIDTH - 21) / 2, y + (TAB_HEIGHT - 21) / 2, 21);
            if (hovered) {
                graphics.setTooltipForNextFrame(font, Component.translatable(candidate.key), mouseX, mouseY);
            }
        }
    }

    private void drawAtlas(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        ensureAtlasInitialized();
        startAtlasIntroIfNeeded();
        if (!atlasIntroVisible) {
            startAtlasHintIfNeeded();
        }
        List<AtlasNode> nodes = atlasNodes();
        if (atlasPage == AtlasPage.TRUST) {
            atlasDragging = false;
            drawTrustRoster(graphics, left, top, mouseX, mouseY, nodes);
            drawSharedOuterFrame(graphics, left, top);
            String atlasTitle = Component.translatable("screen.village-quest.guild_atlas.title").getString();
            graphics.drawString(font, atlasTitle, left + (WINDOW_WIDTH - font.width(atlasTitle)) / 2,
                    top + 14, INK, false);
            drawTabs(graphics, left, top, mouseX, mouseY);
            drawAtlasPageTabs(graphics, left, top, mouseX, mouseY);
            drawAtlasFooter(graphics, left, top, mouseX, mouseY);
            if (atlasIntroVisible) {
                drawAtlasIntro(graphics, left, top, mouseX, mouseY, nodes);
            }
            return;
        }
        int hovered = atlasNodeAt(mouseX, mouseY, left, top, nodes);
        AtlasDetailPlacement previousDetail = atlasDetailPlacement(left, top, nodes, atlasHoveredNode);
        if (hovered >= 0) {
            atlasHoveredNode = hovered;
        } else if (previousDetail == null || !within(mouseX, mouseY,
                previousDetail.x(), previousDetail.y(), ATLAS_DETAIL_WIDTH, ATLAS_DETAIL_HEIGHT)) {
            atlasHoveredNode = -1;
        }
        AtlasDetailPlacement detail = atlasDetailPlacement(left, top, nodes, atlasHoveredNode);

        int mapX = left + ATLAS_MAP_X;
        int mapY = top + ATLAS_MAP_Y;
        int drawX = mapX + (int) Math.round(currentAtlasOffsetX());
        int drawY = mapY + (int) Math.round(currentAtlasOffsetY());
        graphics.enableScissor(mapX, mapY, mapX + ATLAS_MAP_WIDTH, mapY + ATLAS_MAP_HEIGHT);
        graphics.fill(mapX, mapY, mapX + ATLAS_MAP_WIDTH, mapY + ATLAS_MAP_HEIGHT, 0xFF9C7A45);
        VillageUiTheme.blitScaled(graphics, atlasPage.texture, drawX, drawY,
                ATLAS_RENDER_WIDTH, ATLAS_RENDER_HEIGHT, ATLAS_TEXTURE_WIDTH, ATLAS_TEXTURE_HEIGHT);
        drawAtlasFog(graphics, mapX, mapY, drawX, drawY, nodes);
        for (int i = 0; i < nodes.size(); i++) {
            AtlasNode node = nodes.get(i);
            int centerX = drawX + Math.round(node.landmark().x() * ATLAS_RENDER_WIDTH);
            int centerY = drawY + Math.round(node.landmark().y() * ATLAS_RENDER_HEIGHT);
            if (centerX < mapX - ATLAS_MARKER_SIZE || centerX > mapX + ATLAS_MAP_WIDTH + ATLAS_MARKER_SIZE
                    || centerY < mapY - ATLAS_MARKER_SIZE || centerY > mapY + ATLAS_MAP_HEIGHT + ATLAS_MARKER_SIZE) {
                continue;
            }
            drawAtlasMarker(graphics, node, centerX, centerY, i == hovered || i == atlasHoveredNode);
        }
        graphics.disableScissor();

        drawSharedOuterFrame(graphics, left, top);
        String atlasTitle = Component.translatable("screen.village-quest.guild_atlas.title").getString();
        graphics.drawString(font, atlasTitle, left + (WINDOW_WIDTH - font.width(atlasTitle)) / 2,
                top + 14, INK, false);
        drawTabs(graphics, left, top, mouseX, mouseY);
        drawAtlasPageTabs(graphics, left, top, mouseX, mouseY);
        drawAtlasCurrentButton(graphics, left, top, mouseX, mouseY);
        drawAtlasDetail(graphics, nodes, detail);
        drawAtlasFooter(graphics, left, top, mouseX, mouseY);
        if (atlasIntroVisible) {
            drawAtlasIntro(graphics, left, top, mouseX, mouseY, nodes);
        }
    }

    private void drawAtlasPageTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int[] widths = {40, 65, 57};
        int x = left + 235;
        AtlasPage[] pages = AtlasPage.values();
        for (int i = 0; i < pages.length; i++) {
            AtlasPage page = pages[i];
            boolean hovered = within(mouseX, mouseY, x, top + 33, widths[i], 15);
            VillageUiTheme.drawButton(graphics, font, x, top + 33, widths[i], 15,
                    compact(Component.translatable(page.key).getString(), widths[i] - 8, 0.58f),
                    true, hovered, page == atlasPage);
            x += widths[i] + 2;
        }
    }

    private void drawAtlasCurrentButton(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + 73;
        int y = top + 34;
        boolean hovered = within(mouseX, mouseY, x, y, 49, 15);
        VillageUiTheme.drawButton(graphics, font, x, y, 49, 15,
                Component.translatable("screen.village-quest.guild_atlas.current").getString(),
                true, hovered, false);
    }

    private void drawTrustRoster(GuiGraphics graphics, int left, int top, int mouseX, int mouseY,
                                 List<AtlasNode> nodes) {
        graphics.fill(left + ATLAS_MAP_X, top + ATLAS_MAP_Y,
                left + ATLAS_MAP_X + ATLAS_MAP_WIDTH, top + ATLAS_MAP_Y + ATLAS_MAP_HEIGHT,
                0xFFF0D39B);
        VillageUiTheme.blitScaled(graphics, ATLAS_TRUST_TEXTURE,
                left + ATLAS_MAP_X, top + ATLAS_MAP_Y,
                ATLAS_MAP_WIDTH, ATLAS_MAP_HEIGHT, ATLAS_TEXTURE_WIDTH, ATLAS_TEXTURE_HEIGHT);

        int hovered = trustRosterNodeAt(mouseX, mouseY, left, top, nodes.size());
        atlasHoveredNode = hovered;
        for (int i = 0; i < nodes.size(); i++) {
            AtlasNode node = nodes.get(i);
            int rowY = top + TRUST_LIST_Y + i * TRUST_ROW_STEP;
            if (i == hovered) {
                graphics.fill(left + TRUST_LIST_X + 2, rowY + 2,
                        left + TRUST_LIST_X + TRUST_LIST_WIDTH - 2, rowY + TRUST_ROW_HEIGHT - 2,
                        0x24FFF3CB);
            }

            drawAtlasEmblem(graphics, node.emblem(),
                    left + TRUST_ICON_X + TRUST_ICON_SIZE / 2,
                    rowY + TRUST_ICON_SIZE / 2 + 1);

            int accent = trustAccent(node.id());
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(node.title().getString(), TRUST_PROGRESS_X - TRUST_TEXT_X - 8, 0.62f),
                    left + TRUST_TEXT_X, rowY + 4, INK, 0.62f);
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(node.ability().getString(), TRUST_PROGRESS_X - TRUST_TEXT_X - 8, 0.47f),
                    left + TRUST_TEXT_X, rowY + 14, MUTED, 0.47f);

            ReputationService.ReputationTrack track = trustTrack(node.id());
            ReputationProgress progress = reputationProgress(track);
            String progressLabel = progress.complete()
                    ? Component.translatable("screen.village-quest.guild_path.node.status.complete").getString()
                    : progress.current() + "/" + progress.target();
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(progressLabel, TRUST_PROGRESS_WIDTH, 0.50f),
                    left + TRUST_PROGRESS_X, rowY + 4,
                    progress.complete() ? TEAL : BODY, 0.50f);

            int barX = left + TRUST_PROGRESS_X;
            int barY = rowY + 15;
            graphics.fill(barX, barY, barX + TRUST_PROGRESS_WIDTH, barY + 5, FRAME_DARK);
            graphics.fill(barX + 1, barY + 1, barX + TRUST_PROGRESS_WIDTH - 1, barY + 4,
                    0xFF6A4A2B);
            int range = Math.max(1, progress.target() - progress.floor());
            int earned = progress.complete()
                    ? range
                    : Math.max(0, Math.min(range, progress.current() - progress.floor()));
            int filled = Math.round((TRUST_PROGRESS_WIDTH - 2) * (earned / (float) range));
            if (filled > 0) {
                graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + 4, accent);
            }
        }

        if (hovered >= 0 && hovered < nodes.size()) {
            AtlasNode node = nodes.get(hovered);
            ReputationProgress progress = reputationProgress(trustTrack(node.id()));
            Component progressLine = progress.complete()
                    ? Component.translatable("screen.village-quest.journal.v2.reputation.progress.complete")
                    : Component.translatable("screen.village-quest.journal.v2.reputation.progress",
                            progress.current(), progress.target());
            graphics.setTooltipForNextFrame(font, List.of(
                    node.title(), node.ability(), progressLine, node.requirement()), mouseX, mouseY);
        }
    }

    private static int trustRosterNodeAt(int mouseX, int mouseY, int left, int top, int nodeCount) {
        for (int i = 0; i < nodeCount; i++) {
            int rowY = top + TRUST_LIST_Y + i * TRUST_ROW_STEP;
            if (within(mouseX, mouseY, left + TRUST_LIST_X, rowY,
                    TRUST_LIST_WIDTH, TRUST_ROW_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    private static ReputationService.ReputationTrack trustTrack(String id) {
        return switch (id) {
            case "farming" -> ReputationService.ReputationTrack.FARMING;
            case "crafting" -> ReputationService.ReputationTrack.CRAFTING;
            case "animals" -> ReputationService.ReputationTrack.ANIMALS;
            case "trade" -> ReputationService.ReputationTrack.TRADE;
            default -> ReputationService.ReputationTrack.MONSTER_HUNTING;
        };
    }

    private static int trustAccent(String id) {
        return switch (id) {
            case "farming" -> GREEN;
            case "crafting" -> GOLD;
            case "animals" -> TEAL;
            case "trade" -> BLUE;
            default -> RED;
        };
    }

    private void drawAtlasMarker(GuiGraphics graphics, AtlasNode node,
                                 int centerX, int centerY, boolean hovered) {
        if (atlasPage == AtlasPage.CHARTERS && node.emblem() != AtlasEmblem.ITEM) {
            drawCharterMarker(graphics, node, centerX, centerY, hovered);
            return;
        }
        int half = ATLAS_MARKER_SIZE / 2;
        int x = centerX - half;
        int y = centerY - half;
        int accent = node.status() == 2 ? TEAL : node.status() == 1 ? GOLD : 0xFF706457;
        if (node.status() == 1) {
            long phase = (System.currentTimeMillis() / 310L) % 5L;
            int pulse = phase == 0L || phase == 4L ? 2 : 1;
            graphics.fill(centerX - half - pulse, centerY - half - pulse,
                    centerX + half + pulse + 1, centerY + half + pulse + 1, 0x3A9D6D22);
        }
        drawAtlasMarkerFrame(graphics, x, y, accent, hovered,
                node.status() == 0 ? 0xFFD1B989 : 0xFFF0D7A1);
        if (node.emblem() == AtlasEmblem.ITEM) {
            drawScaledItem(graphics, node.previewStack(), centerX - 6, centerY - 7, 0.72f);
        } else {
            drawAtlasEmblem(graphics, node.emblem(), centerX, centerY);
        }
        if (node.status() == 0) {
            graphics.fill(x + 4, y + 4, x + ATLAS_MARKER_SIZE - 4,
                    y + ATLAS_MARKER_SIZE - 4, 0x69493E34);
            drawLock(graphics, x + 15, y + 13);
        } else if (node.status() == 2) {
            drawAtlasCompletionSeal(graphics, x + 15, y + 15);
        }
        if (hovered) {
            drawAtlasMarkerLabel(graphics, node.title().getString(), centerX, centerY - half - 4);
        }
    }

    private void drawCharterMarker(GuiGraphics graphics, AtlasNode node,
                                   int centerX, int centerY, boolean hovered) {
        if (node.status() == 1) {
            long phase = (System.currentTimeMillis() / 310L) % 5L;
            int alpha = phase == 0L || phase == 4L ? 0x52 : 0x34;
            graphics.fill(centerX - 10, centerY - 14, centerX + 10, centerY - 12,
                    (alpha << 24) | 0x00D7A34B);
        }
        drawCharterMarkerBacking(graphics, centerX, centerY, hovered);
        drawAtlasEmblem(graphics, node.emblem(), centerX, centerY, CHARTER_MARKER_SIZE);
        if (node.status() == 0) {
            graphics.fill(centerX + 3, centerY + 3, centerX + 13, centerY + 14, 0xC443342B);
            drawLock(graphics, centerX + 5, centerY + 4);
        } else if (node.status() == 2) {
            drawAtlasCompletionSeal(graphics, centerX + 8, centerY + 8);
        }
        if (hovered) {
            drawAtlasMarkerLabel(graphics, node.title().getString(), centerX,
                    centerY - CHARTER_MARKER_SIZE / 2 - 4);
        }
    }

    private static void drawCharterMarkerBacking(GuiGraphics graphics, int centerX, int centerY,
                                                  boolean hovered) {
        int shadow = 0x8A160D08;
        graphics.fill(centerX - 8, centerY - 13, centerX + 10, centerY + 17, shadow);
        graphics.fill(centerX - 13, centerY - 8, centerX + 15, centerY + 12, shadow);

        int outline = hovered ? 0xFFD7A23E : 0xFF2B190F;
        graphics.fill(centerX - 8, centerY - 15, centerX + 9, centerY + 16, outline);
        graphics.fill(centerX - 12, centerY - 12, centerX + 13, centerY + 13, outline);
        graphics.fill(centerX - 15, centerY - 8, centerX + 16, centerY + 9, outline);
    }

    private static void drawAtlasEmblem(GuiGraphics graphics, AtlasEmblem emblem,
                                        int centerX, int centerY) {
        drawAtlasEmblem(graphics, emblem, centerX, centerY, TRUST_ICON_SIZE);
    }

    private static void drawAtlasEmblem(GuiGraphics graphics, AtlasEmblem emblem,
                                        int centerX, int centerY, int size) {
        if (emblem.texture == null) {
            return;
        }
        VillageUiTheme.blitScaled(graphics, emblem.texture,
                centerX - size / 2, centerY - size / 2,
                size, size, 32, 32);
    }

    private void drawAtlasMarkerFrame(GuiGraphics graphics, int x, int y, int accent,
                                      boolean hovered, int paperColor) {
        int size = ATLAS_MARKER_SIZE;
        graphics.fill(x + 3, y + 3, x + size + 2, y + size + 2, 0x660E0906);
        graphics.fill(x + 3, y, x + size - 3, y + size, 0xFF3A2417);
        graphics.fill(x, y + 3, x + size, y + size - 3, 0xFF3A2417);
        graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, accent);
        graphics.fill(x + 4, y + 4, x + size - 4, y + size - 4, paperColor);
        graphics.fill(x + 5, y + 5, x + size - 5, y + 6, hovered ? 0xFFFFF0C8 : 0xFFF7DFAC);
        graphics.fill(x + 4, y + size - 6, x + size - 4, y + size - 4,
                nodeFrameFooterColor(accent, hovered));
        graphics.fill(x + 2, y + 2, x + 5, y + 5, 0xFFC9943E);
        graphics.fill(x + size - 5, y + 2, x + size - 2, y + 5, 0xFFC9943E);
    }

    private static int nodeFrameFooterColor(int accent, boolean hovered) {
        return hovered ? 0xFFD7A34B : accent;
    }

    private static void drawAtlasCompletionSeal(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 1, y, x + 6, y + 7, 0xFF4A2B19);
        graphics.fill(x, y + 1, x + 7, y + 6, 0xFF4A2B19);
        graphics.fill(x + 1, y + 1, x + 6, y + 6, 0xFF236B68);
        drawCheck(graphics, x + 1, y + 1);
    }

    private void drawAtlasMarkerLabel(GuiGraphics graphics, String label, int centerX, int bottomY) {
        float scale = 0.58f;
        String visible = VillageUiTheme.ellipsize(font, label, 118);
        int textWidth = Math.round(font.width(visible) * scale);
        int labelWidth = textWidth + 8;
        int x = centerX - labelWidth / 2;
        int y = bottomY - 10;
        graphics.fill(x, y, x + labelWidth, y + 9, 0xEEDFC08A);
        graphics.fill(x, y, x + labelWidth, y + 1, 0xFF76512A);
        graphics.fill(x, y + 8, x + labelWidth, y + 9, 0xFF76512A);
        VillageUiTheme.drawStringScaled(graphics, font, visible, x + 4, y + 2, INK, scale);
    }

    private AtlasDetailPlacement atlasDetailPlacement(int left, int top, List<AtlasNode> nodes,
                                                       int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            return null;
        }
        Landmark landmark = nodes.get(nodeIndex).landmark();
        int mapX = left + ATLAS_MAP_X;
        int mapY = top + ATLAS_MAP_Y;
        int drawX = mapX + (int) Math.round(currentAtlasOffsetX());
        int drawY = mapY + (int) Math.round(currentAtlasOffsetY());
        int centerX = drawX + Math.round(landmark.x() * ATLAS_RENDER_WIDTH);
        int centerY = drawY + Math.round(landmark.y() * ATLAS_RENDER_HEIGHT);
        int markerHalf = (atlasPage == AtlasPage.CHARTERS ? CHARTER_MARKER_SIZE : ATLAS_MARKER_SIZE) / 2;
        int minX = left + 74;
        int maxX = left + WINDOW_WIDTH - ATLAS_DETAIL_WIDTH - 10;
        int rightCandidate = centerX + markerHalf + 10;
        int leftCandidate = centerX - markerHalf - ATLAS_DETAIL_WIDTH - 10;
        int x;
        if (rightCandidate <= maxX) {
            x = Math.max(minX, rightCandidate);
        } else if (leftCandidate >= minX) {
            x = Math.min(maxX, leftCandidate);
        } else {
            x = centerX < left + WINDOW_WIDTH / 2 ? maxX : minX;
        }
        int minY = top + 52;
        int maxY = top + 112;
        int y = Math.max(minY, Math.min(maxY, centerY - ATLAS_DETAIL_HEIGHT / 2));
        return new AtlasDetailPlacement(x, y);
    }

    private void drawAtlasDetail(GuiGraphics graphics, List<AtlasNode> nodes,
                                 AtlasDetailPlacement placement) {
        if (placement == null || atlasHoveredNode < 0 || atlasHoveredNode >= nodes.size()) {
            return;
        }
        AtlasNode node = nodes.get(atlasHoveredNode);
        int x = placement.x();
        int y = placement.y();
        drawAtlasTranslucentCard(graphics, x, y, ATLAS_DETAIL_WIDTH, ATLAS_DETAIL_HEIGHT);
        drawScaledItem(graphics, node.previewStack(), x + 8, y + 6, 0.68f);
        VillageUiTheme.drawWrappedScaled(graphics, font, node.title().getString(),
                x + 27, y + 6, ATLAS_DETAIL_WIDTH - 35, INK, 0.58f, 2);
        Component status = Component.translatable("screen.village-quest.guild_path.node.status." + switch (node.status()) {
            case 2 -> "complete";
            case 1 -> "current";
            default -> "locked";
        });
        VillageUiTheme.drawStringScaled(graphics, font, status.getString(), x + 8, y + 23,
                node.status() == 2 ? TEAL : node.status() == 1 ? GOLD : MUTED, 0.52f);
        graphics.fill(x + 8, y + 31, x + ATLAS_DETAIL_WIDTH - 8, y + 32, 0xCCB89A70);
        String abilityLabel = Component.translatable(switch (atlasPage) {
            case PATH -> "screen.village-quest.guild_path.ability";
            case CHARTERS -> "screen.village-quest.guild_atlas.detail.effect";
            case TRUST -> "screen.village-quest.guild_atlas.detail.standing";
        }).getString();
        String requirementLabel = Component.translatable(switch (atlasPage) {
            case PATH -> "screen.village-quest.guild_path.requirement";
            case CHARTERS -> "screen.village-quest.guild_atlas.detail.chronicle";
            case TRUST -> "screen.village-quest.guild_atlas.detail.next";
        }).getString();
        String abilityText = stripLeadingLabel(node.ability().getString(), abilityLabel);
        VillageUiTheme.drawStringScaled(graphics, font, abilityLabel, x + 8, y + 35, GOLD, 0.50f);
        int abilityLines = VillageUiTheme.drawWrappedScaled(graphics, font, abilityText,
                x + 8, y + 43, ATLAS_DETAIL_WIDTH - 16, BODY, 0.44f, 2);
        int requirementLabelY = y + 43 + Math.max(1, abilityLines) * 7 + 2;
        VillageUiTheme.drawStringScaled(graphics, font, requirementLabel, x + 8, requirementLabelY, GOLD, 0.50f);
        int requirementY = requirementLabelY + 8;
        int availableLines = Math.max(1, (y + ATLAS_DETAIL_HEIGHT - 6 - requirementY) / 7);
        VillageUiTheme.drawWrappedScaled(graphics, font, node.requirement().getString(),
                x + 8, requirementY, ATLAS_DETAIL_WIDTH - 16, MUTED, 0.42f, availableLines);
    }

    private static void drawAtlasTranslucentCard(GuiGraphics graphics, int x, int y,
                                                 int width, int height) {
        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, 0x660E0906);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xF2F5E7C7);
        graphics.fill(x, y, x + width, y + 2, 0xD94C2C19);
        graphics.fill(x, y + height - 2, x + width, y + height, 0xD94C2C19);
        graphics.fill(x, y + 2, x + 2, y + height - 2, 0xD94C2C19);
        graphics.fill(x + width - 2, y + 2, x + width, y + height - 2, 0xD94C2C19);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, 0xC8D7A34B);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0xC89A6620);
        graphics.fill(x + 2, y + 3, x + 3, y + height - 3, 0xC8D7A34B);
        graphics.fill(x + width - 3, y + 3, x + width - 2, y + height - 3, 0xC89A6620);
    }

    private void drawAtlasFog(GuiGraphics graphics, int mapX, int mapY, int drawX, int drawY,
                              List<AtlasNode> nodes) {
        if (nodes.isEmpty() || nodes.stream().noneMatch(node -> node.status() == 0)) {
            return;
        }
        List<Landmark> revealed = nodes.stream()
                .filter(node -> node.status() > 0)
                .map(AtlasNode::landmark)
                .toList();
        if (revealed.isEmpty()) {
            return;
        }
        int cell = 10;
        for (int textureY = 0; textureY < ATLAS_RENDER_HEIGHT; textureY += cell) {
            int y = drawY + textureY;
            int bottom = Math.min(drawY + ATLAS_RENDER_HEIGHT, y + cell);
            if (bottom <= mapY || y >= mapY + ATLAS_MAP_HEIGHT) {
                continue;
            }
            for (int textureX = 0; textureX < ATLAS_RENDER_WIDTH; textureX += cell) {
                int x = drawX + textureX;
                int right = Math.min(drawX + ATLAS_RENDER_WIDTH, x + cell);
                if (right <= mapX || x >= mapX + ATLAS_MAP_WIDTH) {
                    continue;
                }
                double normalizedX = (textureX + (right - x) * 0.5) / ATLAS_RENDER_WIDTH;
                double normalizedY = (textureY + (bottom - y) * 0.5) / ATLAS_RENDER_HEIGHT;
                double nearest = Double.MAX_VALUE;
                for (Landmark landmark : revealed) {
                    double dx = normalizedX - landmark.x();
                    double dy = (normalizedY - landmark.y()) * 0.85;
                    nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy));
                }
                double transition = Math.max(0.0, Math.min(1.0, (nearest - 0.14) / 0.24));
                double smooth = transition * transition * (3.0 - 2.0 * transition);
                int alpha = (int) Math.round(0xB8 * smooth);
                if (alpha > 0) {
                    graphics.fill(x, y, right, bottom, (alpha << 24) | 0x000C151C);
                }
            }
        }
    }

    private void drawAtlasIntro(GuiGraphics graphics, int left, int top, int mouseX, int mouseY,
                                List<AtlasNode> nodes) {
        int x = left + ATLAS_INTRO_X;
        int y = top + ATLAS_INTRO_Y;
        VillageUiTheme.drawCard(graphics, x, y, ATLAS_INTRO_WIDTH, ATLAS_INTRO_HEIGHT, true, true);
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.guild_atlas.tutorial.title").getString(),
                x + 12, y + 10, INK, 0.76f);
        VillageUiTheme.drawWrappedScaled(graphics, font,
                Component.translatable("screen.village-quest.guild_atlas.tutorial.explore").getString(),
                x + 12, y + 27, ATLAS_INTRO_WIDTH - 24, BODY, 0.52f, 2);
        VillageUiTheme.drawWrappedScaled(graphics, font,
                Component.translatable("screen.village-quest.guild_atlas.tutorial.fog").getString(),
                x + 12, y + 43, ATLAS_INTRO_WIDTH - 24, MUTED, 0.49f, 2);
        AtlasNode current = nodes.stream().filter(node -> node.status() == 1).findFirst()
                .orElseGet(() -> nodes.isEmpty() ? null : nodes.getLast());
        String next = current == null ? "-" : current.title().getString();
        VillageUiTheme.drawStringScaled(graphics, font,
                Component.translatable("screen.village-quest.guild_atlas.tutorial.next", next).getString(),
                x + 12, y + 64, GOLD, 0.52f);
        boolean hovered = within(mouseX, mouseY, x + ATLAS_INTRO_WIDTH - 73,
                y + ATLAS_INTRO_HEIGHT - 23, 61, 16);
        VillageUiTheme.drawButton(graphics, font, x + ATLAS_INTRO_WIDTH - 73,
                y + ATLAS_INTRO_HEIGHT - 23, 61, 16,
                Component.translatable("screen.village-quest.guild_atlas.tutorial.dismiss").getString(),
                true, hovered, false);
    }

    private void drawAtlasFooter(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        long hintRemaining = atlasPage == AtlasPage.TRUST
                ? 0L : atlasHintUntilMs - System.currentTimeMillis();
        if (hintRemaining > 0L) {
            int alpha = hintRemaining < 700L ? (int) Math.max(0L, 170L * hintRemaining / 700L) : 170;
            graphics.fill(left + 72, top + 207, left + 166, top + 219, alpha << 24 | 0x002B1A10);
            VillageUiTheme.drawStringScaled(graphics, font,
                    Component.translatable("screen.village-quest.guild_atlas.hint").getString(),
                    left + 76, top + 210, (Math.min(255, alpha + 70) << 24) | 0x00F1D29A, 0.56f);
        }
        boolean closeHover = within(mouseX, mouseY, left + 330, top + 204, 68, 17);
        VillageUiTheme.drawButton(graphics, font, left + 330, top + 204, 68, 17,
                Component.translatable("screen.village-quest.guild_path.close").getString(),
                true, closeHover, false);
    }

    private void drawSharedOuterFrame(GuiGraphics graphics, int left, int top) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS_FRAME_TEXTURE, left, top, 0.0f, 0.0f,
                WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    private void drawJournalBackground(GuiGraphics graphics, int left, int top) {
        graphics.fill(left + ATLAS_MAP_X, top + ATLAS_MAP_Y,
                left + ATLAS_MAP_X + ATLAS_MAP_WIDTH, top + ATLAS_MAP_Y + ATLAS_MAP_HEIGHT,
                0xFF5B351F);
        graphics.enableScissor(left + 16, top + 26, left + 400, top + 217);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD_TEXTURE, left, top, 0.0f, 0.0f,
                WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
        graphics.disableScissor();
        drawSharedOuterFrame(graphics, left, top);
    }

    private void startAtlasHintIfNeeded() {
        if (atlasHintChecked) {
            return;
        }
        atlasHintChecked = true;
        if (InventoryJournalTutorialState.shouldShowAtlasDragHint()) {
            atlasHintUntilMs = System.currentTimeMillis() + ATLAS_HINT_DURATION_MS;
            InventoryJournalTutorialState.markAtlasDragHintSeen();
        }
    }

    private void startAtlasIntroIfNeeded() {
        if (atlasIntroChecked) {
            return;
        }
        atlasIntroChecked = true;
        atlasIntroVisible = InventoryJournalTutorialState.shouldShowAtlasIntro();
    }

    private static String stripLeadingLabel(String text, String label) {
        if (text == null || text.isBlank() || label == null || label.isBlank()
                || text.length() < label.length()
                || !text.regionMatches(true, 0, label, 0, label.length())) {
            return text == null ? "" : text;
        }
        String remainder = text.substring(label.length()).stripLeading();
        if (remainder.startsWith(":")) {
            remainder = remainder.substring(1).stripLeading();
        }
        return remainder.isBlank() ? text : remainder;
    }

    private void drawCards(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<JournalCard> cards = cardsForSection();
        int viewportX = left + CONTENT_X + 5;
        int viewportY = top + CONTENT_TOP;
        int viewportWidth = CONTENT_WIDTH - 10;
        int cardWidth = viewportWidth - 5;
        int viewportHeight = CONTENT_BOTTOM - CONTENT_TOP;
        int contentHeight = contentHeight(cards, cardWidth);
        scrollMax = Math.max(0, contentHeight - viewportHeight);
        clampScroll();

        graphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
        int y = viewportY - scrollOffset;
        for (JournalCard card : cards) {
            int cardHeight = cardHeight(card, cardWidth);
            if (y + cardHeight >= viewportY && y <= viewportY + viewportHeight) {
                drawCard(graphics, card, viewportX, y, cardWidth, cardHeight, mouseX, mouseY);
            }
            y += cardHeight + CARD_GAP;
        }
        graphics.disableScissor();
        drawScrollBar(graphics, viewportX + viewportWidth - 3, viewportY, viewportHeight, contentHeight);
    }

    private void drawCard(GuiGraphics graphics, JournalCard card, int x, int y, int cardWidth,
                          int cardHeight, int mouseX, int mouseY) {
        boolean expanded = card.id().equals(expandedCardId);
        boolean hovered = within(mouseX, mouseY, x, y, cardWidth, cardHeight);
        VillageUiTheme.drawCard(graphics, x, y, cardWidth, cardHeight, hovered, expanded);
        graphics.fill(x + 6, y + 6, x + 9, y + cardHeight - 6, card.accent());
        VillageUiTheme.drawStringScaled(graphics, font,
                compact(card.title().getString(), cardWidth - CARD_TEXT_INSET - 28, CARD_TITLE_SCALE),
                x + CARD_TEXT_INSET, y + 6, INK, CARD_TITLE_SCALE);
        VillageUiTheme.drawStringScaled(graphics, font,
                compact(card.subtitle().getString(), cardWidth - CARD_TEXT_INSET - 28, CARD_BODY_SCALE),
                x + CARD_TEXT_INSET, y + 18, MUTED, CARD_BODY_SCALE);
        VillageUiTheme.blitScaled(graphics,
                VillageUiTheme.control(expanded ? "chevron_up" : "chevron_down"),
                x + cardWidth - 20, y + 7, 12, 12, 16, 16);
        if (card.cancelAction() >= 0) {
            VillageUiTheme.blitScaled(graphics, VillageUiTheme.control("close"),
                    x + cardWidth - 38, y + 5, 15, 15, 24, 24);
        }

        if (!expanded) {
            return;
        }
        int lineY = y + CARD_COLLAPSED_HEIGHT;
        ReputationService.ReputationTrack reputationTrack = reputationTrack(card);
        if (reputationTrack != null) {
            ReputationProgress progress = reputationProgress(reputationTrack);
            String progressLabel = progress.complete()
                    ? Component.translatable("screen.village-quest.journal.v2.reputation.progress.complete").getString()
                    : Component.translatable("screen.village-quest.journal.v2.reputation.progress",
                            progress.current(), progress.target()).getString();
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(progressLabel, cardWidth - CARD_TEXT_INSET - 18, 0.64f),
                    x + CARD_TEXT_INSET, lineY, BODY, 0.64f);

            int barX = x + CARD_TEXT_INSET;
            int barY = lineY + 9;
            int barWidth = cardWidth - CARD_TEXT_INSET - 18;
            graphics.fill(barX, barY, barX + barWidth, barY + 6, FRAME_DARK);
            graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 5, 0xFF6A4A2B);
            int range = Math.max(1, progress.target() - progress.floor());
            int earned = progress.complete()
                    ? range
                    : Math.max(0, Math.min(range, progress.current() - progress.floor()));
            int filled = Math.round((barWidth - 2) * (earned / (float) range));
            if (filled > 0) {
                graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + 5, card.accent());
            }
            lineY += REPUTATION_PROGRESS_HEIGHT;
        }
        for (String line : compactDetails(card, cardWidth)) {
            VillageUiTheme.drawStringScaled(graphics, font, line,
                    x + CARD_TEXT_INSET, lineY, BODY, CARD_BODY_SCALE);
            lineY += CARD_DETAIL_STEP;
        }
    }

    private void drawFooterButtons(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int doneX = left + WINDOW_WIDTH - FOOTER_RIGHT_INSET - DONE_BUTTON_WIDTH;
        int questMasterX = doneX - 84;
        int prosperityX = questMasterX - 74;
        int mapX = prosperityX - 58;
        if (data.hasCaravanLedger) {
            drawButton(graphics, mapX, top + BUTTON_Y, 52,
                    Component.translatable("screen.village-quest.journal.v2.button.map").getString(),
                    within(mouseX, mouseY, mapX, top + BUTTON_Y, 52, BUTTON_HEIGHT));
        }
        if (canOpenProsperity()) {
            drawButton(graphics, prosperityX, top + BUTTON_Y, 68,
                    Component.translatable("screen.village-quest.journal.v2.button.prosperity").getString(),
                    within(mouseX, mouseY, prosperityX, top + BUTTON_Y, 68, BUTTON_HEIGHT));
        }
        drawButton(graphics, questMasterX, top + BUTTON_Y, 78,
                Component.translatable("screen.village-quest.journal.active.questmaster_button").getString(),
                within(mouseX, mouseY, questMasterX, top + BUTTON_Y, 78, BUTTON_HEIGHT));
        drawButton(graphics, doneX, top + BUTTON_Y, DONE_BUTTON_WIDTH,
                Component.translatable("screen.village-quest.journal.done").getString(),
                within(mouseX, mouseY, doneX, top + BUTTON_Y, DONE_BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int buttonWidth, String label, boolean hovered) {
        VillageUiTheme.drawButton(graphics, font, x, y, buttonWidth, BUTTON_HEIGHT,
                label, true, hovered, false);
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
        Section[] sections = Section.values();
        for (int i = 0; i < sections.length; i++) {
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            if (within(mouseX, mouseY, left + TAB_X, y, TAB_WIDTH, TAB_HEIGHT)) {
                section = sections[i];
                scrollOffset = 0;
                expandedCardId = "";
                ensureExpandedCard();
                playClick();
                return true;
            }
        }

        if (section == Section.ATLAS) {
            return handleAtlasMouseClicked(mouseX, mouseY, left, top)
                    || super.mouseClicked(click, doubled);
        }

        int doneX = left + WINDOW_WIDTH - FOOTER_RIGHT_INSET - DONE_BUTTON_WIDTH;
        int questMasterX = doneX - 84;
        int prosperityX = questMasterX - 74;
        int mapX = prosperityX - 58;
        if (data.hasCaravanLedger && within(mouseX, mouseY, mapX, top + BUTTON_Y, 52, BUTTON_HEIGHT)) {
            openRouteMap();
            return true;
        }
        if (canOpenProsperity()
                && within(mouseX, mouseY, prosperityX, top + BUTTON_Y, 68, BUTTON_HEIGHT)) {
            openProsperity();
            return true;
        }
        if (within(mouseX, mouseY, questMasterX, top + BUTTON_Y, 78, BUTTON_HEIGHT)) {
            summonQuestMasterFromJournal();
            return true;
        }
        if (within(mouseX, mouseY, doneX, top + BUTTON_Y, DONE_BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onClose();
            return true;
        }

        List<JournalCard> cards = cardsForSection();
        int cardX = left + CONTENT_X + 5;
        int cardWidth = CONTENT_WIDTH - 15;
        int y = top + CONTENT_TOP - scrollOffset;
        for (JournalCard card : cards) {
            int height = cardHeight(card, cardWidth);
            if (within(mouseX, mouseY, cardX, y, cardWidth, height)) {
                if (card.cancelAction() >= 0
                        && within(mouseX, mouseY, cardX + cardWidth - 40, y + 3, 18, 19)) {
                    ClientPlayNetworking.send(new JournalActionPayload(card.cancelAction()));
                    playClick();
                    return true;
                }
                expandedCardId = card.id().equals(expandedCardId) ? "" : card.id();
                clampScroll();
                playPageTurn();
                return true;
            }
            y += height + CARD_GAP;
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean handleAtlasMouseClicked(int mouseX, int mouseY, int left, int top) {
        if (atlasIntroVisible) {
            int introX = left + ATLAS_INTRO_X;
            int introY = top + ATLAS_INTRO_Y;
            if (within(mouseX, mouseY, introX + ATLAS_INTRO_WIDTH - 73,
                    introY + ATLAS_INTRO_HEIGHT - 23, 61, 16)) {
                atlasIntroVisible = false;
                InventoryJournalTutorialState.markAtlasIntroSeen();
                InventoryJournalTutorialState.markAtlasDragHintSeen();
                atlasHintChecked = true;
                atlasHintUntilMs = 0L;
                playClick();
            }
            return true;
        }
        int x = left + 235;
        int[] widths = {40, 65, 57};
        AtlasPage[] pages = AtlasPage.values();
        for (int i = 0; i < pages.length; i++) {
            if (within(mouseX, mouseY, x, top + 33, widths[i], 15)) {
                atlasPage = pages[i];
                atlasHoveredNode = -1;
                ensureAtlasInitialized();
                playPageTurn();
                return true;
            }
            x += widths[i] + 2;
        }
        if (atlasPage != AtlasPage.TRUST
                && within(mouseX, mouseY, left + 73, top + 34, 49, 15)) {
            centerAtlasOnCurrent();
            playClick();
            return true;
        }
        if (within(mouseX, mouseY, left + 330, top + 204, 68, 17)) {
            onClose();
            return true;
        }
        if (atlasPage == AtlasPage.TRUST) {
            return trustRosterNodeAt(mouseX, mouseY, left, top, atlasNodes().size()) >= 0;
        }
        AtlasDetailPlacement detail = atlasDetailPlacement(left, top, atlasNodes(), atlasHoveredNode);
        if (detail != null && within(mouseX, mouseY,
                detail.x(), detail.y(), ATLAS_DETAIL_WIDTH, ATLAS_DETAIL_HEIGHT)) {
            return true;
        }
        if (within(mouseX, mouseY, left + ATLAS_MAP_X, top + ATLAS_MAP_Y,
                ATLAS_MAP_WIDTH, ATLAS_MAP_HEIGHT)) {
            atlasDragging = true;
            atlasDragDistance = 0.0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (section == Section.ATLAS && atlasDragging && click.button() == 0) {
            double adjustedX = responsiveDrag(dragX, WINDOW_WIDTH, WINDOW_HEIGHT);
            double adjustedY = responsiveDrag(dragY, WINDOW_WIDTH, WINDOW_HEIGHT);
            int index = atlasPage.ordinal();
            atlasOffsetX[index] += adjustedX;
            atlasOffsetY[index] += adjustedY;
            atlasDragDistance += Math.abs(adjustedX) + Math.abs(adjustedY);
            clampAtlasOffset();
            atlasHoveredNode = -1;
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && atlasDragging) {
            atlasDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (section == Section.ATLAS) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if (scrollMax > 0 && within(uiMouseX, uiMouseY,
                left + CONTENT_X, top + CONTENT_TOP, CONTENT_WIDTH, CONTENT_BOTTOM - CONTENT_TOP)) {
            scrollOffset -= (int) Math.signum(verticalAmount) * 22;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private List<JournalCard> cardsForSection() {
        return switch (section) {
            case OVERVIEW -> overviewCards();
            case QUESTS -> activeQuestCards();
            case ATLAS -> List.of();
            case GUIDE -> guideCards();
        };
    }

    private List<JournalCard> overviewCards() {
        int reputationTotal = reputationTotal();
        return List.of(
                card("overview_progress", "screen.village-quest.journal.v2.overview.progress",
                        Component.translatable("screen.village-quest.journal.v2.overview.progress_short",
                                data.completed, data.discovered),
                        List.of(
                                Component.translatable("screen.village-quest.journal.summary.total", data.total),
                                Component.translatable("screen.village-quest.journal.summary.discovered", data.discovered),
                                Component.translatable("screen.village-quest.journal.summary.active", data.active),
                                Component.translatable("screen.village-quest.journal.summary.completed", data.completed)
                ), TEAL),
                card("overview_village", "screen.village-quest.journal.v2.overview.village",
                        Component.translatable("screen.village-quest.journal.summary.reputation", reputationTotal),
                        List.of(
                                Component.translatable("screen.village-quest.journal.summary.projects", completedProjectCount())
                        ), GOLD),
                card("overview_next", "screen.village-quest.journal.v2.overview.next",
                        Component.translatable("screen.village-quest.journal.v2.overview.next_short"),
                        List.of(
                                Component.translatable("screen.village-quest.journal.v2.overview.next_body"),
                                Component.translatable(data.hasCaravanLedger
                                        ? "screen.village-quest.journal.v2.overview.routes_ready"
                                        : "screen.village-quest.journal.v2.overview.routes_locked")
                        ), BLUE)
        );
    }

    private List<JournalCard> activeQuestCards() {
        List<JournalCard> cards = new ArrayList<>();
        addActiveCard(cards, data.dailyActive, "daily", "screen.village-quest.journal.active.daily",
                data.dailyTitle, data.dailyProgress, "screen.village-quest.journal.active.daily_hint",
                BLUE, JournalActionPayload.ACTION_CANCEL_DAILY);
        addActiveCard(cards, data.weeklyActive, "weekly", "screen.village-quest.journal.active.weekly",
                data.weeklyTitle, data.weeklyProgress, "screen.village-quest.journal.active.weekly_hint",
                GOLD, JournalActionPayload.ACTION_CANCEL_WEEKLY);
        addActiveCard(cards, data.storyActive, "story", "screen.village-quest.journal.active.story",
                data.storyTitle, data.storyProgress, "screen.village-quest.journal.v2.active.story_hint",
                GREEN, -1);
        addActiveCard(cards, data.pilgrimActive, "pilgrim", "screen.village-quest.journal.active.pilgrim",
                data.pilgrimTitle, data.pilgrimProgress, "screen.village-quest.journal.v2.active.pilgrim_hint",
                0xFF9B6B34, -1);
        addActiveCard(cards, data.specialActive, "special", "screen.village-quest.journal.active.special",
                data.specialTitle, data.specialProgress, "screen.village-quest.journal.v2.active.special_hint",
                PURPLE, -1);
        if (cards.isEmpty()) {
            cards.add(card("active_none", "screen.village-quest.journal.active.none",
                    Component.translatable("screen.village-quest.journal.active.none_hint"),
                    List.of(Component.translatable("screen.village-quest.journal.v2.active.none_body")), MUTED));
        }
        return cards;
    }

    private void addActiveCard(List<JournalCard> cards, boolean active, String id, String labelKey,
                               Component title, Component progress, String hintKey, int accent, int cancelAction) {
        if (!active) {
            return;
        }
        List<Component> details = new ArrayList<>();
        if (!progress.getString().isBlank()) {
            details.add(progress);
        }
        details.add(Component.translatable(hintKey));
        cards.add(new JournalCard(id, title.getString().isBlank() ? Component.translatable(labelKey) : title,
                Component.translatable(labelKey), List.copyOf(details), accent, cancelAction));
    }

    private List<JournalCard> reputationCards() {
        List<JournalCard> cards = new ArrayList<>();
        addReputationCard(cards, ReputationService.ReputationTrack.FARMING, data.farmingReputation, GREEN);
        addReputationCard(cards, ReputationService.ReputationTrack.CRAFTING, data.craftingReputation, GOLD);
        addReputationCard(cards, ReputationService.ReputationTrack.ANIMALS, data.animalReputation, TEAL);
        addReputationCard(cards, ReputationService.ReputationTrack.TRADE, data.tradeReputation, BLUE);
        addReputationCard(cards, ReputationService.ReputationTrack.MONSTER_HUNTING, data.monsterReputation, RED);
        return cards;
    }

    private void addReputationCard(List<JournalCard> cards, ReputationService.ReputationTrack track,
                                   int value, int accent) {
        Component rank = Component.translatable(ReputationService.rankFor(value).translationKey());
        cards.add(new JournalCard("rep_" + track.name(), Component.translatable(track.translationKey()),
                Component.translatable("screen.village-quest.journal.v2.reputation.short", value, rank),
                List.of(Component.literal(storyAwareNextUnlockLine(track, value))), accent, -1));
    }

    private List<JournalCard> collectionCards() {
        List<JournalCard> cards = new ArrayList<>();
        List<ProjectEntry> projects = List.of(
                new ProjectEntry("quest.village-quest.project.village_ledger", data.hasVillageLedgerProject),
                new ProjectEntry("quest.village-quest.project.apiary_charter", data.hasApiaryCharterProject),
                new ProjectEntry("quest.village-quest.project.forge_charter", data.hasForgeCharterProject),
                new ProjectEntry("quest.village-quest.project.market_charter", data.hasMarketCharterProject),
                new ProjectEntry("quest.village-quest.project.pasture_charter", data.hasPastureCharterProject),
                new ProjectEntry("quest.village-quest.project.watch_bell", data.hasWatchBellProject),
                new ProjectEntry("quest.village-quest.project.caravan_yard", data.hasCaravanYardProject)
        );
        int index = 0;
        for (ProjectEntry project : projects) {
            List<Component> details = new ArrayList<>();
            details.add(Component.translatable(project.keyPrefix() + ".description"));
            details.add(Component.translatable(project.keyPrefix() + ".effect"));
            if (project.unlocked()) {
                details.add(Component.translatable(project.keyPrefix() + ".memory"));
            }
            cards.add(new JournalCard("project_" + index++, Component.translatable(project.keyPrefix() + ".title"),
                    Component.translatable(project.unlocked()
                            ? "screen.village-quest.journal.projects.built"
                            : "screen.village-quest.journal.projects.locked"),
                    List.copyOf(details), project.unlocked() ? GREEN : MUTED, -1));
        }
        for (SpecialItemEntry item : ownedSpecialItems()) {
            cards.add(new JournalCard("item_" + item.nameKey(), Component.translatable(item.nameKey()),
                    Component.translatable("screen.village-quest.journal.v2.collection.owned"),
                    List.of(Component.translatable(item.loreKey())), PURPLE, -1));
        }
        return cards;
    }

    private List<SpecialItemEntry> ownedSpecialItems() {
        List<SpecialItemEntry> items = new ArrayList<>();
        if (data.hasStarreachRing) items.add(new SpecialItemEntry("item.village-quest.starreach_ring", "item.village-quest.starreach_ring.lore"));
        if (data.hasMerchantSeal) items.add(new SpecialItemEntry("item.village-quest.merchant_seal", "item.village-quest.merchant_seal.lore"));
        if (data.hasShepherdFlute) items.add(new SpecialItemEntry("item.village-quest.shepherd_flute", "item.village-quest.shepherd_flute.lore"));
        if (data.hasApiaristSmoker) items.add(new SpecialItemEntry("item.village-quest.apiarists_smoker", "item.village-quest.apiarists_smoker.lore"));
        if (data.hasSurveyorCompass) items.add(new SpecialItemEntry("item.village-quest.surveyors_compass", "screen.village-quest.journal.relics.surveyors_compass"));
        if (data.hasCaravanLedger) items.add(new SpecialItemEntry("item.village-quest.caravan_ledger", "item.village-quest.caravan_ledger.lore"));
        return items;
    }

    private List<JournalCard> guideCards() {
        return List.of(
                guide("guide_start", "start", GREEN),
                guide("guide_quests", "quests", GOLD),
                guide("guide_prosperity", "prosperity", PURPLE),
                guide("guide_routes", "routes", TEAL),
                guide("guide_controls", "controls", BLUE)
        );
    }

    private JournalCard guide(String id, String suffix, int accent) {
        return new JournalCard(id,
                Component.translatable("screen.village-quest.journal.v2.guide." + suffix + ".title"),
                Component.translatable("screen.village-quest.journal.v2.guide." + suffix + ".short"),
                List.of(Component.translatable("screen.village-quest.journal.v2.guide." + suffix + ".body")),
                accent, -1);
    }

    private JournalCard card(String id, String titleKey, Component subtitle, List<Component> details, int accent) {
        return new JournalCard(id, Component.translatable(titleKey), subtitle, details, accent, -1);
    }

    private List<AtlasNode> atlasNodes() {
        return switch (atlasPage) {
            case PATH -> pathAtlasNodes();
            case CHARTERS -> charterAtlasNodes();
            case TRUST -> trustAtlasNodes();
        };
    }

    private List<AtlasNode> pathAtlasNodes() {
        List<AtlasNode> nodes = new ArrayList<>();
        for (int i = 0; i < data.guildPathNodes.size(); i++) {
            Payloads.GuildPathNodeData node = data.guildPathNodes.get(i);
            Landmark fallback = new Landmark(0.08f + Math.min(1.0f, i / 10.0f) * 0.84f, 0.5f);
            nodes.add(new AtlasNode(node.nodeId(), node.previewStack(), node.title(), node.ability(),
                    node.requirement(), node.status(), PATH_LANDMARKS.getOrDefault(node.nodeId(), fallback)));
        }
        return List.copyOf(nodes);
    }

    private List<AtlasNode> charterAtlasNodes() {
        String[] ids = {"village_ledger", "apiary_charter", "forge_charter", "market_charter",
                "pasture_charter", "watch_bell", "caravan_yard", "wayshrine_network"};
        Item[] items = {ModItems.VILLAGE_LEDGER_PLAQUE, ModItems.APIARY_CHARTER_PLAQUE,
                ModItems.FORGE_CHARTER_PLAQUE, ModItems.MARKET_CHARTER_PLAQUE,
                ModItems.PASTURE_CHARTER_PLAQUE, ModItems.WATCH_BELL_RELIQUARY,
                ModItems.CARAVAN_LEDGER, ModItems.GUILD_WAYSHRINE};
        AtlasEmblem[] emblems = {AtlasEmblem.CHARTER_LEDGER, AtlasEmblem.CHARTER_APIARY,
                AtlasEmblem.CHARTER_FORGE, AtlasEmblem.CHARTER_MARKET,
                AtlasEmblem.CHARTER_PASTURE, AtlasEmblem.CHARTER_WATCH,
                AtlasEmblem.CHARTER_CARAVAN, AtlasEmblem.CHARTER_WAYSHRINE};
        boolean[] complete = {data.hasVillageLedgerProject, data.hasApiaryCharterProject,
                data.hasForgeCharterProject, data.hasMarketCharterProject,
                data.hasPastureCharterProject, data.hasWatchBellProject,
                data.hasCaravanYardProject, data.hasWayshrineNetworkProject};
        int firstIncomplete = -1;
        for (int i = 0; i < complete.length; i++) {
            if (!complete[i]) {
                firstIncomplete = i;
                break;
            }
        }
        List<AtlasNode> nodes = new ArrayList<>(ids.length);
        for (int i = 0; i < ids.length; i++) {
            String prefix = "quest.village-quest.project." + ids[i];
            int status = complete[i] ? 2 : i == firstIncomplete ? 1 : 0;
            nodes.add(new AtlasNode(ids[i], itemStack(items[i]),
                    Component.translatable(prefix + ".title"),
                    Component.translatable(prefix + ".effect"),
                    Component.translatable(complete[i] ? prefix + ".memory" : prefix + ".description"),
                    status, CHARTER_LANDMARKS.get(ids[i]), emblems[i]));
        }
        return List.copyOf(nodes);
    }

    private List<AtlasNode> trustAtlasNodes() {
        return List.of(
                trustAtlasNode("farming", ModItems.APIARY_CHARTER_PLAQUE,
                        ReputationService.ReputationTrack.FARMING, data.farmingReputation),
                trustAtlasNode("crafting", ModItems.FORGE_CHARTER_PLAQUE,
                        ReputationService.ReputationTrack.CRAFTING, data.craftingReputation),
                trustAtlasNode("animals", ModItems.PASTURE_CHARTER_PLAQUE,
                        ReputationService.ReputationTrack.ANIMALS, data.animalReputation),
                trustAtlasNode("trade", ModItems.MARKET_CHARTER_PLAQUE,
                        ReputationService.ReputationTrack.TRADE, data.tradeReputation),
                trustAtlasNode("monster_hunting", ModItems.WATCH_BELL_RELIQUARY,
                        ReputationService.ReputationTrack.MONSTER_HUNTING, data.monsterReputation)
        );
    }

    private AtlasNode trustAtlasNode(String id, Item item, ReputationService.ReputationTrack track, int value) {
        Component rank = Component.translatable(ReputationService.rankFor(value).translationKey());
        ReputationProgress progress = reputationProgress(track);
        return new AtlasNode(id, itemStack(item), Component.translatable(track.translationKey()),
                Component.translatable("screen.village-quest.guild_atlas.trust.standing", value, rank),
                Component.literal(storyAwareNextUnlockLine(track, value)), progress.complete() ? 2 : 1,
                TRUST_LANDMARKS.get(id), switch (id) {
                    case "farming" -> AtlasEmblem.FARMING;
                    case "crafting" -> AtlasEmblem.CRAFTING;
                    case "animals" -> AtlasEmblem.ANIMALS;
                    case "trade" -> AtlasEmblem.TRADE;
                    default -> AtlasEmblem.WARDEN;
                });
    }

    private static ItemStack itemStack(Item item) {
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void ensureAtlasInitialized() {
        int index = atlasPage.ordinal();
        if (!atlasInitialized[index]) {
            centerAtlasOnCurrent();
            atlasInitialized[index] = true;
        }
    }

    private void centerAtlasOnCurrent() {
        if (atlasPage == AtlasPage.TRUST) {
            int index = atlasPage.ordinal();
            atlasOffsetX[index] = 0.0;
            atlasOffsetY[index] = 0.0;
            return;
        }
        List<AtlasNode> nodes = atlasNodes();
        AtlasNode target = null;
        for (AtlasNode node : nodes) {
            if (node.status() == 1) {
                target = node;
                break;
            }
        }
        if (target == null) {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                if (nodes.get(i).status() == 2) {
                    target = nodes.get(i);
                    break;
                }
            }
        }
        Landmark landmark = target == null ? new Landmark(0.5f, 0.5f) : target.landmark();
        int index = atlasPage.ordinal();
        atlasOffsetX[index] = ATLAS_MAP_WIDTH / 2.0 - landmark.x() * ATLAS_RENDER_WIDTH;
        atlasOffsetY[index] = ATLAS_MAP_HEIGHT / 2.0 - landmark.y() * ATLAS_RENDER_HEIGHT;
        clampAtlasOffset();
    }

    private void clampAtlasOffset() {
        int index = atlasPage.ordinal();
        atlasOffsetX[index] = Math.max(ATLAS_MAP_WIDTH - ATLAS_RENDER_WIDTH,
                Math.min(0.0, atlasOffsetX[index]));
        atlasOffsetY[index] = Math.max(ATLAS_MAP_HEIGHT - ATLAS_RENDER_HEIGHT,
                Math.min(0.0, atlasOffsetY[index]));
    }

    private double currentAtlasOffsetX() {
        return atlasOffsetX[atlasPage.ordinal()];
    }

    private double currentAtlasOffsetY() {
        return atlasOffsetY[atlasPage.ordinal()];
    }

    private int atlasNodeAt(int mouseX, int mouseY, int left, int top, List<AtlasNode> nodes) {
        int mapX = left + ATLAS_MAP_X;
        int mapY = top + ATLAS_MAP_Y;
        if (!within(mouseX, mouseY, mapX, mapY, ATLAS_MAP_WIDTH, ATLAS_MAP_HEIGHT)) {
            return -1;
        }
        int drawX = mapX + (int) Math.round(currentAtlasOffsetX());
        int drawY = mapY + (int) Math.round(currentAtlasOffsetY());
        int markerSize = atlasPage == AtlasPage.CHARTERS ? CHARTER_MARKER_SIZE : ATLAS_MARKER_SIZE;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Landmark landmark = nodes.get(i).landmark();
            int centerX = drawX + Math.round(landmark.x() * ATLAS_RENDER_WIDTH);
            int centerY = drawY + Math.round(landmark.y() * ATLAS_RENDER_HEIGHT);
            if (within(mouseX, mouseY, centerX - markerSize / 2,
                    centerY - markerSize / 2, markerSize, markerSize)) {
                return i;
            }
        }
        return -1;
    }

    private static void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
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

    private int contentHeight(List<JournalCard> cards, int width) {
        int result = 0;
        for (JournalCard card : cards) {
            result += cardHeight(card, width) + CARD_GAP;
        }
        return Math.max(0, result - CARD_GAP);
    }

    private int cardHeight(JournalCard card, int width) {
        if (!card.id().equals(expandedCardId)) {
            return CARD_COLLAPSED_HEIGHT;
        }
        int extra = reputationTrack(card) == null ? 6 : REPUTATION_PROGRESS_HEIGHT + 4;
        return CARD_COLLAPSED_HEIGHT + extra + compactDetails(card, width).size() * CARD_DETAIL_STEP;
    }

    private List<String> compactDetails(JournalCard card, int cardWidth) {
        int renderedWidth = Math.max(12, cardWidth - CARD_TEXT_INSET - 18);
        int unscaledWidth = Math.max(1, (int) Math.floor(renderedWidth / CARD_BODY_SCALE));
        return wrappedDetails(card, unscaledWidth);
    }

    private String compact(String text, int renderedWidth, float scale) {
        return VillageUiTheme.ellipsize(font, text,
                Math.max(1, (int) Math.floor(renderedWidth / scale)));
    }

    private List<String> wrappedDetails(JournalCard card, int width) {
        List<String> lines = new ArrayList<>();
        for (Component detail : card.details()) {
            lines.addAll(wrapText(detail.getString(), width));
        }
        return lines;
    }

    private void ensureExpandedCard() {
        List<JournalCard> cards = cardsForSection();
        if (cards.stream().noneMatch(card -> card.id().equals(expandedCardId))) {
            expandedCardId = cards.isEmpty() ? "" : cards.getFirst().id();
        }
    }

    private void drawScrollBar(GuiGraphics graphics, int x, int y, int height, int contentHeight) {
        VillageUiTheme.drawScrollBar(graphics, x - 2, y, height,
                height, contentHeight, scrollOffset, scrollMax);
    }

    private void drawFrame(GuiGraphics graphics, int x, int y, int frameWidth, int frameHeight, int fill) {
        graphics.fill(x, y, x + frameWidth, y + frameHeight, FRAME_DARK);
        graphics.fill(x + 1, y + 1, x + frameWidth - 1, y + frameHeight - 1, FRAME_LIGHT);
        graphics.fill(x + 2, y + 2, x + frameWidth - 2, y + frameHeight - 2, fill);
    }

    private void openRouteMap() {
        if (minecraft == null || minecraft.player == null) return;
        navigating = true;
        minecraft.player.connection.sendCommand("vq routes");
        super.onClose();
    }

    private boolean canOpenProsperity() {
        return data.hasApiaryCharterProject
                || data.hasForgeCharterProject
                || data.hasMarketCharterProject
                || data.hasPastureCharterProject
                || data.hasWatchBellProject
                || data.hasCaravanYardProject
                || data.hasCaravanLedger;
    }

    private void openProsperity() {
        if (minecraft == null || minecraft.player == null) return;
        navigating = true;
        minecraft.player.connection.sendCommand("vq prosperity");
        super.onClose();
    }

    private void summonQuestMasterFromJournal() {
        if (minecraft == null || minecraft.player == null) return;
        InventoryJournalTutorialState.markQuestMasterButtonHintSeen();
        navigating = true;
        minecraft.player.connection.sendCommand("vq questmaster");
        super.onClose();
    }

    private void sendJournalClose() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand("vq journal close");
        }
    }

    private boolean shouldShowQuestMasterTutorial() {
        return section == Section.OVERVIEW && InventoryJournalTutorialState.shouldShowQuestMasterButtonHint();
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void playPageTurn() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, scrollMax)));
    }

    private int reputationTotal() {
        return data.farmingReputation + data.craftingReputation + data.animalReputation
                + data.tradeReputation + data.monsterReputation;
    }

    private int completedProjectCount() {
        int count = 0;
        if (data.hasVillageLedgerProject) count++;
        if (data.hasApiaryCharterProject) count++;
        if (data.hasForgeCharterProject) count++;
        if (data.hasMarketCharterProject) count++;
        if (data.hasPastureCharterProject) count++;
        if (data.hasWatchBellProject) count++;
        if (data.hasCaravanYardProject) count++;
        if (data.hasWayshrineNetworkProject) count++;
        return count;
    }

    private String storyAwareNextUnlockLine(ReputationService.ReputationTrack track, int value) {
        RelicQuestProgressionService.RelicUnlockPath path = RelicQuestProgressionService.pathFor(track);
        ReputationService.ReputationUnlock nextUnlock = ReputationService.nextReputationUnlock(track, value);
        boolean storyMet = hasStoryProjectForTrack(track);
        if (path != null && !storyMet) {
            String storyTitle = Component.translatable("quest.village-quest.story."
                    + path.requiredStoryArc().id() + ".title").getString();
            if (nextUnlock != null && nextUnlock.requiredReputation() >= path.requiredReputation()) {
                return Component.translatable("screen.village-quest.journal.reputation.next_unlock_story",
                        Component.translatable(nextUnlock.titleKey()).getString(), storyTitle,
                        path.requiredReputation()).getString();
            }
            if (nextUnlock == null) {
                return Component.translatable("screen.village-quest.journal.reputation.story_required",
                        Component.translatable(path.titleKey()).getString(), storyTitle).getString();
            }
        }
        if (nextUnlock == null) {
            int mastery = ReputationService.masteryLevel(value);
            if (mastery < ReputationService.MAX_MASTERY) {
                int nextMastery = ReputationService.MASTERY_START
                        + (mastery + 1) * ReputationService.MASTERY_STEP;
                return Component.translatable("text.village-quest.reputation.next_mastery",
                        mastery + 1, nextMastery).getString();
            }
            return Component.translatable("screen.village-quest.journal.reputation.all_unlocked").getString();
        }
        return Component.translatable("screen.village-quest.journal.reputation.next_unlock",
                Component.translatable(nextUnlock.titleKey()).getString(),
                nextUnlock.requiredReputation()).getString();
    }

    private ReputationService.ReputationTrack reputationTrack(JournalCard card) {
        if (card == null || !card.id().startsWith("rep_")) {
            return null;
        }
        try {
            return ReputationService.ReputationTrack.valueOf(card.id().substring(4));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ReputationProgress reputationProgress(ReputationService.ReputationTrack track) {
        int current = reputationValue(track);
        ReputationService.ReputationUnlock next = ReputationService.nextReputationUnlock(track, current);
        if (next != null) {
            int floor = 0;
            for (ReputationService.ReputationUnlock unlock : ReputationService.reputationUnlocksFor(track)) {
                if (unlock.requiredReputation() >= next.requiredReputation()) {
                    break;
                }
                floor = unlock.requiredReputation();
            }
            return new ReputationProgress(current, floor, next.requiredReputation(), false);
        }

        int mastery = ReputationService.masteryLevel(current);
        if (mastery < ReputationService.MAX_MASTERY) {
            int floor = ReputationService.MASTERY_START + mastery * ReputationService.MASTERY_STEP;
            int target = floor + ReputationService.MASTERY_STEP;
            return new ReputationProgress(current, floor, target, false);
        }
        int cap = ReputationService.MASTERY_START
                + ReputationService.MAX_MASTERY * ReputationService.MASTERY_STEP;
        return new ReputationProgress(Math.max(current, cap), cap - ReputationService.MASTERY_STEP, cap, true);
    }

    private int reputationValue(ReputationService.ReputationTrack track) {
        return switch (track) {
            case FARMING -> data.farmingReputation;
            case CRAFTING -> data.craftingReputation;
            case ANIMALS -> data.animalReputation;
            case TRADE -> data.tradeReputation;
            case MONSTER_HUNTING -> data.monsterReputation;
        };
    }

    private boolean hasStoryProjectForTrack(ReputationService.ReputationTrack track) {
        return switch (track) {
            case FARMING -> data.hasApiaryCharterProject;
            case CRAFTING -> data.hasForgeCharterProject;
            case ANIMALS -> data.hasPastureCharterProject;
            case TRADE -> data.hasMarketCharterProject;
            case MONSTER_HUNTING -> data.hasWatchBellProject;
        };
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private String ellipsize(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "…";
        String value = text;
        while (!value.isEmpty() && font.width(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private static boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
