package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.VillageQuest;
import de.quest.client.ui.InventoryJournalTutorialState;
import de.quest.client.ui.QuestArtwork;
import de.quest.client.ui.TutorialHintRenderer;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.network.Payloads;
import de.quest.network.Payloads.JournalActionPayload;
import de.quest.quest.special.RelicQuestProgressionService;
import de.quest.registry.ModItems;
import de.quest.reputation.ReputationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
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
    private int pendingCancelAction = -1;
    private static final Identifier ATLAS_PATH_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_path_map.png");
    private static final Identifier ATLAS_CHARTERS_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_charters_map.png");
    private static final Identifier ATLAS_TRUST_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_trust_roster.png");
    private static final int WINDOW_WIDTH = 520;
    private static final int WINDOW_HEIGHT = 294;
    private static final int SETTINGS_X = 4, SETTINGS_Y = 4, SETTINGS_SIZE = 20;
    private static final Identifier SETTINGS_GEAR = Identifier.fromNamespaceAndPath(
            "village-quest", "textures/gui/ui/v3/settings_gear.png");
    private static final int TAB_X = 25;
    private static final int TAB_Y = 42;
    private static final int TAB_WIDTH = 76;
    private static final int TAB_HEIGHT = 29;
    private static final int TAB_GAP = 3;
    private static final int CONTENT_X = 104;
    private static final int CONTENT_Y = 40;
    private static final int CONTENT_WIDTH = 388;
    private static final int CONTENT_TOP = 66;
    private static final int CONTENT_BOTTOM = 224;
    private static final int CARD_GAP = 5;
    private static final int CARD_COLLAPSED_HEIGHT = 31;
    private static final int CARD_TEXT_INSET = 20;
    private static final int CARD_IMAGE_TEXT_INSET = 84;
    private static final int ILLUSTRATED_CARD_HEIGHT = 34;
    private static final float CARD_TITLE_SCALE = 0.84f;
    private static final float CARD_BODY_SCALE = 0.72f;
    private static final int CARD_DETAIL_STEP = 8;
    private static final int REPUTATION_PROGRESS_HEIGHT = 19;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_Y = 240;
    private static final int DONE_BUTTON_WIDTH = 78;
    private static final int FOOTER_RIGHT_INSET = 32;
    private static final int HEADER_WALLET_RIGHT_INSET = 40;
    private static final int HEADER_WALLET_TOP = 10;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF69513D;
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
    private static final int ATLAS_MAP_X = 104;
    private static final int ATLAS_MAP_Y = 64;
    private static final int ATLAS_MAP_WIDTH = 388;
    private static final int ATLAS_MAP_HEIGHT = 168;
    private static final int ATLAS_RENDER_WIDTH = 780;
    private static final int ATLAS_RENDER_HEIGHT = 390;
    private static final int ATLAS_TEXTURE_WIDTH = 1184;
    private static final int ATLAS_TEXTURE_HEIGHT = 592;
    private static final int ATLAS_MARKER_SIZE = 25;
    private static final int ATLAS_DETAIL_WIDTH = 180;
    private static final int ATLAS_DETAIL_HEIGHT = 88;
    private static final int ATLAS_INTRO_X = 104;
    private static final int ATLAS_INTRO_Y = 65;
    private static final int ATLAS_INTRO_WIDTH = 224;
    private static final int ATLAS_INTRO_HEIGHT = 100;
    private static final long ATLAS_HINT_DURATION_MS = 4200L;
    private static final int TRUST_LIST_X = 110;
    private static final int TRUST_LIST_Y = 70;
    private static final int TRUST_LIST_WIDTH = 376;
    private static final int TRUST_ROW_HEIGHT = 30;
    private static final int TRUST_ROW_STEP = 32;
    private static final int TRUST_ICON_X = 114;
    private static final int TRUST_ICON_SIZE = 24;
    private static final int TRUST_ICON_Y_OFFSET = -1;
    private static final int TRUST_TEXT_X = 146;
    private static final int TRUST_PROGRESS_X = 397;
    private static final int TRUST_PROGRESS_WIDTH = 80;
    private static final int CHARTER_MARKER_SIZE = 22;
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
        OVERVIEW("screen.village-quest.journal.v2.tab.overview", "home"),
        QUESTS("screen.village-quest.journal.v2.tab.quests", "quests"),
        NETWORK("screen.village-quest.journal.v2.tab.network", "social"),
        ATLAS("screen.village-quest.journal.v2.tab.atlas", "guide"),
        GUIDE("screen.village-quest.journal.v2.tab.guide", "story");

        private final String key;
        private final String icon;

        Section(String key, String icon) {
            this.key = key;
            this.icon = icon;
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

    private record ReputationProgress(int current, int floor, int target, boolean complete) {}

    private JournalScreenData data;
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
    public JournalScreen(JournalScreenData data) {
        super(Component.translatable("screen.village-quest.journal.title"));
        this.data = data;
        if (data!=null) de.quest.client.config.QuestTrackerFocus.current().reconcile(data.activeTitles());
    }
    void selectTourSection(String state) {
        pendingCancelAction=-1;
        section=state.startsWith("atlas_")?Section.ATLAS:state.equals("quests_reset")?Section.QUESTS:Section.valueOf(state.toUpperCase(java.util.Locale.ROOT));
        if(state.startsWith("atlas_")) atlasPage=AtlasPage.valueOf(state.substring(6).toUpperCase(java.util.Locale.ROOT));
    }

    private int networkPage;
    private de.quest.reputation.ReputationViewService.View reputation;
    public void updateReputation(de.quest.reputation.ReputationViewService.View view) {
        if (reputation != null && view.revision() < reputation.revision()) return;
        reputation = view; clampScroll();
    }
    private int contentTop() { return section == Section.NETWORK ? 68 : CONTENT_TOP; }
    private int cardViewportWidth() { return CONTENT_WIDTH - 10; }
    private int cardWidth() { return cardViewportWidth() - 12; }

    enum HomeDestination { QUESTS, VILLAGES, QUEST_MASTER }

    static HomeDestination homeDestination(JournalScreenData view) {
        if(!view.activeQuests.isEmpty()) return HomeDestination.QUESTS;
        if (view.storyActive || view.specialActive || view.weeklyActive || view.dailyActive || view.pilgrimActive)
            return HomeDestination.QUESTS;
        return view.networkNextAction.getString().isBlank() ? HomeDestination.QUEST_MASTER : HomeDestination.VILLAGES;
    }

    private void drawNetworkTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String[] keys = {"villages", "title", "history"};
        for (int i = 0; i < 3; i++) {
            int x = left + 109 + i * 92;
            VillageUiTheme.drawNavigationTab(graphics, font, x, top + 44, 89, 16,
                    compact(ReputationPanel.text(keys[i]).getString(), 81, 0.7f), true,
                    within(mouseX, mouseY, x, top + 44, 89, 16), networkPage == i);
        }
        if (networkPage == 1 && reputation != null && reputation.villageTotal() > 8) {
            VillageUiTheme.drawButton(graphics, font, left + 422, top + 44, 24, 16, "<",
                    reputation.villagePage() > 0, within(mouseX, mouseY, left + 422, top + 44, 24, 16), false);
            VillageUiTheme.drawButton(graphics, font, left + 451, top + 44, 24, 16, ">",
                    (reputation.villagePage() + 1) * 8 < reputation.villageTotal(), within(mouseX, mouseY, left + 451, top + 44, 24, 16), false);
        }
    }
    private boolean handleNetworkTab(int mouseX, int mouseY, int left, int top) {
        for (int i = 0; i < 3; i++) if (within(mouseX, mouseY, left + 109 + i * 92, top + 44, 89, 16)) {
            networkPage = i; scrollOffset = 0; expandedCardId = ""; ensureExpandedCard(); playClick(); return true;
        }
        if (networkPage == 1 && reputation != null) {
            int page = reputation.villagePage();
            if (page > 0 && within(mouseX, mouseY, left + 422, top + 44, 24, 16)) page--;
            if ((page + 1) * 8 < reputation.villageTotal() && within(mouseX, mouseY, left + 451, top + 44, 24, 16)) page++;
            if (page != reputation.villagePage()) {
                UiTestTour.send(new de.quest.network.ReputationPayloads.PagePayload(page)); scrollOffset = 0; return true;
            }
        }
        return false;
    }

    public void updateData(JournalScreenData data) {
        this.data = data;
        if (data!=null) de.quest.client.config.QuestTrackerFocus.current().reconcile(data.activeTitles());
        pendingCancelAction = -1;
        clampScroll();
    }

    @Override
    protected void init() {
        this.closeNotified = false;
        pendingCancelAction = -1;
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
        if (section == Section.NETWORK && (key.key() == InputConstants.KEY_LEFT || key.key() == InputConstants.KEY_RIGHT)) {
            networkPage = Math.floorMod(networkPage + (key.key() == InputConstants.KEY_RIGHT ? 1 : -1), 3);
            scrollOffset = 0; expandedCardId = ""; ensureExpandedCard(); return true;
        }
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
                if (section == Section.NETWORK) drawNetworkTabs(graphics, left, top, uiMouseX, uiMouseY);
                drawCards(graphics, left, top, uiMouseX, uiMouseY);
                drawFooterButtons(graphics, left, top, uiMouseX, uiMouseY);
            }
            super.render(graphics, uiMouseX, uiMouseY, delta);
            drawSettingsGear(graphics, left, top, uiMouseX, uiMouseY);

            if (shouldShowQuestMasterTutorial()) {
                var target = homeQuestMasterTarget(left, top);
                TutorialHintRenderer.drawHint(
                        graphics, font,
                        Component.translatable("screen.village-quest.journal.active.questmaster_button_tutorial"),
                        width, height, target.x(), target.y(), target.width(), target.height(),
                        TutorialHintRenderer.Placement.ABOVE, true,
                        (int) Math.round(Math.sin(System.currentTimeMillis() / 180.0d) * 2.0d)
                );
            }
        } finally {
            endResponsivePanel(graphics, panelScale);
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        JournalV3Visuals.title(graphics,font,title.getString(),left+174,top+3,172);
        String sectionText = Component.translatable(section.key).getString();
        if (section != Section.NETWORK && section != Section.OVERVIEW)
            JournalV3Visuals.heading(graphics,font,sectionText,left+CONTENT_X+7,top+CONTENT_Y+8,240,1.08f,INK);
        VillageUiTheme.drawWalletStrip(graphics, font, left, top, WINDOW_WIDTH, data.currencyBalance,
                HEADER_WALLET_RIGHT_INSET, HEADER_WALLET_TOP);
    }

    private static final Section[] NAV_SECTIONS = {Section.OVERVIEW, Section.QUESTS, Section.NETWORK,
            Section.NETWORK, Section.ATLAS, Section.GUIDE};
    private String navigationLabel(int index) {
        return switch(index) {
            case 2 -> ReputationPanel.text("villages").getString();
            case 3 -> Component.translatable("screen.village-quest.journal.v2.tab.reputation").getString();
            default -> Component.translatable(NAV_SECTIONS[index].key).getString();
        };
    }
    private void drawTabs(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        String[] icons={"compass","scroll","village","handshake","atlas","guide"};
        for(int i=0;i<NAV_SECTIONS.length;i++) {
            int x=left+TAB_X,y=top+TAB_Y+i*(TAB_HEIGHT+TAB_GAP);
            boolean selected=NAV_SECTIONS[i]==section && (section!=Section.NETWORK || (i==3?networkPage==1:networkPage!=1));
            boolean hover=within(mouseX,mouseY,x,y,TAB_WIDTH,TAB_HEIGHT);
            VillageUiTheme.drawTab(g,x,y,TAB_WIDTH,TAB_HEIGHT,selected,hover);
            JournalV3Visuals.sprite(g,icons[i],x+4,y+4,21,21);
            String label=navigationLabel(i);
            VillageUiTheme.drawWrappedScaled(g,font,label,x+28,y+9,TAB_WIDTH-32,
                    VillageUiTheme.LIGHT_TEXT,.8f,2);
            if(hover)g.setTooltipForNextFrame(font,Component.literal(label),mouseX,mouseY);
        }
    }

    private void drawAtlas(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        graphics.fill(left+12,top+22,left+WINDOW_WIDTH-12,top+WINDOW_HEIGHT-14,VillageUiTheme.WOOD);
        JournalV3Visuals.paper(graphics,left+101,top+38,401,222);
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
            JournalV3Visuals.title(graphics,font,atlasTitle,left+174,top+3,172);
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
                previousDetail.x(), previousDetail.y(), ATLAS_DETAIL_WIDTH, atlasDetailHeight(nodes.get(atlasHoveredNode)))) {
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
        JournalV3Visuals.title(graphics,font,atlasTitle,left+174,top+3,172);
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
        int x = left + 320;
        AtlasPage[] pages = AtlasPage.values();
        for (int i = 0; i < pages.length; i++) {
            AtlasPage page = pages[i];
            boolean hovered = within(mouseX, mouseY, x, top + 44, widths[i], 15);
            VillageUiTheme.drawNavigationTab(graphics, font, x, top + 44, widths[i], 15,
                    compact(Component.translatable(page.key).getString(), widths[i] - 8, 0.58f),
                    true, hovered, page == atlasPage);
            x += widths[i] + 2;
        }
    }

    private void drawAtlasCurrentButton(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + 110;
        int y = top + 44;
        boolean hovered = within(mouseX, mouseY, x, y, 49, 15);
        VillageUiTheme.drawButton(graphics, font, x, y, 49, 15,
                Component.translatable("screen.village-quest.guild_atlas.current").getString(),
                true, hovered, false);
        var nodes=atlasNodes();
        AtlasNode context=atlasHoveredNode>=0 && atlasHoveredNode<nodes.size()?nodes.get(atlasHoveredNode):null;
        if(context==null)context=nodes.stream().filter(n->n.status()==1).findFirst().orElse(null);
        if(context==null && !nodes.isEmpty())context=nodes.getFirst();
        if(context!=null) {
            // The map owns this inset; the entire navigation row remains unobstructed.
            int contextY=top+ATLAS_MAP_Y+3;
            JournalV3Visuals.paper(graphics,x,contextY,ATLAS_MAP_WIDTH-12,16);
            VillageUiTheme.drawStringScaled(graphics,font,compact(Component.translatable(atlasHoveredNode>=0?"screen.village-quest.ui_review.selected":"screen.village-quest.ui_review.current",context.title()).getString(),ATLAS_MAP_WIDTH-28,.82f),x+6,contextY+4,INK,.82f);
            if(within(mouseX,mouseY,x,contextY,ATLAS_MAP_WIDTH-12,16)) graphics.setTooltipForNextFrame(font,List.of(context.title(),context.ability(),context.requirement()),mouseX,mouseY);
        }
    }

    private void drawTrustRoster(GuiGraphics graphics, int left, int top, int mouseX, int mouseY,
                                 List<AtlasNode> nodes) {
        graphics.fill(left + ATLAS_MAP_X, top + ATLAS_MAP_Y,
                left + ATLAS_MAP_X + ATLAS_MAP_WIDTH, top + ATLAS_MAP_Y + ATLAS_MAP_HEIGHT,
                0xFFF0D39B);

        int hovered = trustRosterNodeAt(mouseX, mouseY, left, top, nodes.size());
        atlasHoveredNode = hovered;
        for (int i = 0; i < nodes.size(); i++) {
            AtlasNode node = nodes.get(i);
            int rowY = top + TRUST_LIST_Y + i * TRUST_ROW_STEP;
            JournalV3Visuals.paper(graphics,left+TRUST_LIST_X,rowY,TRUST_LIST_WIDTH,TRUST_ROW_HEIGHT);
            if(i==hovered) graphics.fill(left+TRUST_LIST_X+2,rowY+2,
                    left+TRUST_LIST_X+TRUST_LIST_WIDTH-2,rowY+TRUST_ROW_HEIGHT-2,0x22365E59);

            drawAtlasEmblem(graphics, node.emblem(),
                    left + TRUST_ICON_X + TRUST_ICON_SIZE / 2,
                    rowY + TRUST_ICON_SIZE / 2 + TRUST_ICON_Y_OFFSET);

            int accent = trustAccent(node.id());
            ReputationService.ReputationTrack track = trustTrack(node.id());
            ReputationProgress progress = reputationProgress(track);
            JournalV3Visuals.heading(graphics,font,node.title().getString(),
                    left+TRUST_TEXT_X,rowY+4,121,.84f,INK);
            VillageUiTheme.drawStringScaled(graphics,font,compact(Component.translatable(
                    ReputationService.rankFor(progress.current()).translationKey()).getString(),123,.78f),
                    left+269,rowY+5,BODY,.78f);
            VillageUiTheme.drawStringScaled(graphics,font,compact(node.requirement().getString(),239,.70f),
                    left+TRUST_TEXT_X,rowY+18,MUTED,.70f);

            String progressLabel = progress.complete()
                    ? Component.translatable("screen.village-quest.guild_path.node.status.complete").getString()
                    : progress.current() + "/" + progress.target();
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(progressLabel, TRUST_PROGRESS_WIDTH, 0.72f),
                    left + TRUST_PROGRESS_X, rowY + 4,
                    progress.complete() ? TEAL : BODY, 0.72f);

            int barX = left + TRUST_PROGRESS_X;
            int barY = rowY + 19;
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
            graphics.fill(centerX - 7, centerY - 11, centerX + 7, centerY - 9,
                    (alpha << 24) | 0x00D7A34B);
        }
        drawCharterMarkerBacking(graphics, centerX, centerY, hovered);
        drawAtlasEmblem(graphics, node.emblem(), centerX, centerY, CHARTER_MARKER_SIZE);
        if (node.status() == 0) {
            graphics.fill(centerX + 2, centerY + 2, centerX + 10, centerY + 10, 0xC443342B);
            drawCharterLock(graphics, centerX + 4, centerY + 3);
        } else if (node.status() == 2) {
            drawCharterCompletionSeal(graphics, centerX + 5, centerY + 5);
        }
        if (hovered) {
            drawAtlasMarkerLabel(graphics, node.title().getString(), centerX,
                    centerY - CHARTER_MARKER_SIZE / 2 - 4);
        }
    }

    private static void drawCharterMarkerBacking(GuiGraphics graphics, int centerX, int centerY,
                                                  boolean hovered) {
        int shadow = 0x66160D08;
        graphics.fill(centerX - 7, centerY - 12, centerX + 7, centerY + 12, shadow);
        graphics.fill(centerX - 10, centerY - 10, centerX + 10, centerY + 10, shadow);
        graphics.fill(centerX - 12, centerY - 7, centerX + 12, centerY + 7, shadow);

        int outline = hovered ? 0xFFD7A23E : 0xFF2B190F;
        graphics.fill(centerX - 6, centerY - 11, centerX + 6, centerY + 11, outline);
        graphics.fill(centerX - 9, centerY - 9, centerX + 9, centerY + 9, outline);
        graphics.fill(centerX - 11, centerY - 6, centerX + 11, centerY + 6, outline);
    }

    private static void drawCharterLock(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 3, x + 5, y + 7, 0xFFD9A83B);
        graphics.fill(x + 1, y + 1, x + 2, y + 4, 0xFFD9A83B);
        graphics.fill(x + 3, y + 1, x + 4, y + 4, 0xFFD9A83B);
        graphics.fill(x + 2, y, x + 3, y + 1, 0xFFD9A83B);
        graphics.fill(x + 2, y + 4, x + 3, y + 6, 0xFF513118);
    }

    private static void drawCharterCompletionSeal(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 1, x + 6, y + 5, 0xFF4A2B19);
        graphics.fill(x + 1, y, x + 5, y + 6, 0xFF4A2B19);
        graphics.fill(x + 1, y + 1, x + 5, y + 5, 0xFF236B68);
        graphics.fill(x + 1, y + 3, x + 2, y + 4, 0xFFF6D37A);
        graphics.fill(x + 2, y + 4, x + 3, y + 5, 0xFFF6D37A);
        graphics.fill(x + 3, y + 2, x + 4, y + 4, 0xFFF6D37A);
        graphics.fill(x + 4, y + 1, x + 5, y + 3, 0xFFF6D37A);
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
        int detailHeight = atlasDetailHeight(nodes.get(nodeIndex));
        int maxY = top + WINDOW_HEIGHT - 32 - detailHeight;
        int y = Math.max(minY, Math.min(maxY, centerY - detailHeight / 2));
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
        int detailHeight = atlasDetailHeight(node);
        drawAtlasTranslucentCard(graphics, x, y, ATLAS_DETAIL_WIDTH, detailHeight);
        drawScaledItem(graphics, node.previewStack(), x + 8, y + 6, 0.68f);
        VillageUiTheme.drawWrappedScaled(graphics, font, node.title().getString(),
                x + 27, y + 6, ATLAS_DETAIL_WIDTH - 35, INK, 0.84f, 2);
        Component status = Component.translatable("screen.village-quest.guild_path.node.status." + switch (node.status()) {
            case 2 -> "complete";
            case 1 -> "current";
            default -> "locked";
        });
        VillageUiTheme.drawStringScaled(graphics, font, status.getString(), x + 8, y + 26,
                node.status() == 2 ? TEAL : node.status() == 1 ? GOLD : MUTED, 0.72f);
        graphics.fill(x + 8, y + 36, x + ATLAS_DETAIL_WIDTH - 8, y + 37, 0xCCB89A70);
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
        VillageUiTheme.drawStringScaled(graphics, font, abilityLabel, x + 8, y + 40, GOLD, 0.76f);
        int abilityLines = VillageUiTheme.drawWrappedScaled(graphics, font, abilityText,
                x + 8, y + 50, ATLAS_DETAIL_WIDTH - 16, BODY, 0.72f, Integer.MAX_VALUE);
        int requirementLabelY = y + 50 + Math.max(1, abilityLines) * 8 + 3;
        VillageUiTheme.drawStringScaled(graphics, font, requirementLabel, x + 8, requirementLabelY, GOLD, 0.76f);
        int requirementY = requirementLabelY + 8;
        VillageUiTheme.drawWrappedScaled(graphics, font, node.requirement().getString(),
                x + 8, requirementY, ATLAS_DETAIL_WIDTH - 16, MUTED, 0.72f, Integer.MAX_VALUE);
    }

    private int atlasDetailHeight(AtlasNode node) {
        int abilityLines = VillageUiTheme.wrapScaledLines(font, node.ability().getString(), ATLAS_DETAIL_WIDTH - 16, 0.72f).size();
        int chronicleLines = VillageUiTheme.wrapScaledLines(font, node.requirement().getString(), ATLAS_DETAIL_WIDTH - 16, 0.72f).size();
        return Math.max(ATLAS_DETAIL_HEIGHT, 70 + (Math.max(1, abilityLines) + Math.max(1, chronicleLines)) * 8);
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
        JournalV3Visuals.paper(graphics,left+104,top+236,388,24);
        long hintRemaining = atlasPage == AtlasPage.TRUST
                ? 0L : atlasHintUntilMs - System.currentTimeMillis();
        if (hintRemaining > 0L) {
            int alpha = hintRemaining < 700L ? (int) Math.max(0L, 170L * hintRemaining / 700L) : 170;
            graphics.fill(left + 110, top + 240, left + 300, top + 254, alpha << 24 | 0x002B1A10);
            VillageUiTheme.drawStringScaled(graphics, font,
                    Component.translatable("screen.village-quest.guild_atlas.hint").getString(),
                    left + 114, top + 245, (Math.min(255, alpha + 70) << 24) | 0x00F1D29A, 0.56f);
        }
        boolean closeHover = within(mouseX, mouseY, left + 420, top + 239, 68, 17);
        VillageUiTheme.drawButton(graphics, font, left + 420, top + 239, 68, 17,
                Component.translatable("screen.village-quest.guild_path.close").getString(),
                true, closeHover, false);
    }

    private void drawSharedOuterFrame(GuiGraphics graphics, int left, int top) {
        VillageUiTheme.drawFrame(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
        JournalV3Visuals.ornaments(graphics,left,top,WINDOW_WIDTH,WINDOW_HEIGHT);
        VillageUiTheme.drawButton(graphics,font,left+WINDOW_WIDTH-25,top+3,20,20,"×",true,false,false);
    }

    private void drawSettingsGear(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + SETTINGS_X, y = top + SETTINGS_Y;
        boolean hovered = within(mouseX, mouseY, x, y, SETTINGS_SIZE, SETTINGS_SIZE);
        // Keep the frame's golden hardware; cover only its old decorative jewel.
        graphics.fill(x + 4, y + 4, x + 16, y + 16, hovered ? 0xFF346D65 : 0xFF173C38);
        VillageUiTheme.blitScaled(graphics, SETTINGS_GEAR, x + 3, y + 3, 14, 14, 32, 32);
        if (hovered) graphics.setTooltipForNextFrame(font,
                Component.translatable("screen.village-quest.setup.settings"), mouseX, mouseY);
    }

    private void drawJournalBackground(GuiGraphics graphics, int left, int top) {
        graphics.fill(left+12,top+22,left+WINDOW_WIDTH-12,top+WINDOW_HEIGHT-14,VillageUiTheme.WOOD);
        JournalV3Visuals.paper(graphics,left+101,top+38,401,222);
        drawSharedOuterFrame(graphics, left, top);

    }

    private void startAtlasHintIfNeeded() {
        if (atlasHintChecked) {
            return;
        }
        atlasHintChecked = true;
        if (InventoryJournalTutorialState.shouldShowAtlasDragHint()) {
            atlasHintUntilMs = System.currentTimeMillis() + ATLAS_HINT_DURATION_MS;
            if (!UiTestTour.active()) InventoryJournalTutorialState.markAtlasDragHintSeen();
        }
    }

    private void startAtlasIntroIfNeeded() {
        if (atlasIntroChecked) {
            return;
        }
        atlasIntroChecked = true;
        atlasIntroVisible = !UiTestTour.active() && InventoryJournalTutorialState.shouldShowAtlasIntro();
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
        if (section == Section.OVERVIEW) { drawHome(graphics, left, top, mouseX, mouseY); return; }
        List<JournalCard> cards = cardsForSection();
        int viewportX = left + CONTENT_X + 5;
        int viewportY = top + contentTop();
        int viewportWidth = cardViewportWidth();
        int cardWidth = cardWidth();
        int viewportHeight = CONTENT_BOTTOM - contentTop();
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

    /** V3 composition: landscape + four discoveries, next step, real tasks and Chronicle. */
    private void drawHome(GuiGraphics g,int left,int top,int mouseX,int mouseY) {
        int x=left+104,y=top+44;
        JournalV3Visuals.paper(g,x,y,258,136);
        VillageUiTheme.blitScaled(g,VillageUiTheme.JOURNAL_HERO,x+2,y+2,254,93,288,96);
        JournalV3Visuals.textWash(g,x+2,y+2,178,85);
        String title=Component.translatable("screen.village-quest.journal.home.title").getString();
        JournalV3Visuals.heading(g,font,title,x+12,y+15,157,1.65f,INK);
        VillageUiTheme.drawWrappedScaled(g,font,Component.translatable("screen.village-quest.journal.home.subtitle").getString(),
                x+12,y+39,156,INK,.88f,3);
        String[] keys={Section.QUESTS.key,"screen.village-quest.journal.home.discoveries",
                "screen.village-quest.reputation.villages","screen.village-quest.journal.v2.tab.reputation"};
        String[] icons={"scroll","compass","village","handshake"};
        for(int i=0;i<4;i++) {
            int bx=x+i*64;boolean hover=within(mouseX,mouseY,bx,top+141,64,37);
            if(i>0)g.fill(bx,top+146,bx+1,top+173,0xFFC69A50);
            if(hover)g.fill(bx+3,top+142,bx+62,top+177,0x22FFFFFF);
            JournalV3Visuals.sprite(g,icons[i],bx+21,top+142,24,24);
            String label=Component.translatable(keys[i]).getString();
            VillageUiTheme.drawStringScaled(g,font,compact(label,60,.82f),bx+(64-Math.min(60,font.width(label)*.82f))/2,
                    top+168,INK,.82f);
            if(hover)g.setTooltipForNextFrame(font,Component.translatable(keys[i]),mouseX,mouseY);
        }
        JournalCard next=new JournalContentBuilder(data).overviewCards().get(2);
        int nx=left+368;
        JournalV3Visuals.paper(g,nx,y,132,136);
        JournalV3Visuals.section(g,font,Component.translatable("screen.village-quest.journal.home.next").getString(),nx+3,y+3,126,.84f,"scroll");
        var instruction=homeInstruction(next);
        Optional<QuestArtwork.Motif> motif=artworkFor(next);
        if (instruction.isEmpty()) {
            motif.ifPresent(m->JournalV3Visuals.illustration(g,m.largeTexture(),nx+8,top+68,116,44));
            if(motif.isEmpty())JournalV3Visuals.sprite(g,"scroll",nx+50,top+72,32,32);
        }
        var nextTitle=VillageUiTheme.wrapScaled(font,next.subtitle().getString(),116,1.04f);
        for(int row=0;row<Math.min(2,nextTitle.size());row++)JournalV3Visuals.centeredHeading(g,font,nextTitle.get(row),nx+8,top+(instruction.isPresent()?69:117)+row*10,116,1.04f,INK);
        if (instruction.isPresent()) VillageUiTheme.drawWrappedScaled(g,font,instruction.get().getString(),nx+8,top+92,116,BODY,.76f,8);
        else if(!next.details().isEmpty()) {
            var objective=de.quest.client.ui.QuestObjectiveRows.rows(next.details().getFirst());
            if(!objective.isEmpty()) {
                VillageUiTheme.drawStringScaled(g,font,compact(Component.translatable("screen.village-quest.ui_review.continue_objectives").getString(),116,.74f),nx+8,top+137,INK,.74f);
                for(int i=0;i<Math.min(2,objective.size());i++) {
                    var row=objective.get(i);int ox=nx+8+i*58;
                    VillageUiTheme.drawScaledItem(g,new net.minecraft.world.item.ItemStack(row.item()),ox,top+146,.6f);
                    String count=row.current()+"/"+row.target();
                    float scale=Math.min(.72f,42f/Math.max(1,font.width(count)));
                    VillageUiTheme.drawStringScaled(g,font,count,ox+12,top+148,BODY,scale);
                }
            } else VillageUiTheme.drawWrappedScaled(g,font,next.details().getFirst().getString(),nx+8,top+137,116,BODY,.8f,2);
        }
        JournalV3Visuals.action(g,font,Component.translatable("screen.village-quest.journal.home.open").getString(),
                nx+8,top+158,116,17,within(mouseX,mouseY,nx+8,top+158,116,17));
        if(within(mouseX,mouseY,nx+5,y+21,122,90)) {
            var lines=new ArrayList<Component>();lines.add(next.subtitle());lines.addAll(next.details());
            g.setTooltipForNextFrame(font,lines,mouseX,mouseY);
        }
        drawHomeTasks(g,left,top,mouseX,mouseY);
        int cx=left+322,cy=top+180;
        JournalV3Visuals.paper(g,cx,cy,178,76);
        JournalV3Visuals.heading(g,font,Component.translatable("screen.village-quest.journal.home.chronicle").getString(),cx+10,cy+7,160,1.1f,INK);
        JournalV3Visuals.sprite(g,"banner",cx+8,cy+24,35,47);
        VillageUiTheme.drawWrappedScaled(g,font,Component.translatable("screen.village-quest.journal.home.chronicle_body").getString(),
                cx+49,cy+24,119,BODY,.84f,3);
        JournalV3Visuals.action(g,font,Component.translatable("screen.village-quest.journal.home.chronicle_open").getString(),
                cx+49,cy+56,119,15,within(mouseX,mouseY,cx+49,cy+56,119,15));
    }
    static Optional<Component> homeInstruction(JournalCard card) {
        if (!card.details().isEmpty() && card.details().getFirst().getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents content
                && content.getKey().startsWith("quest.village-quest.story.living_kingdoms.next."))
            return Optional.of(Component.translatable(content.getKey(),content.getArgs()));
        return Optional.empty();
    }
    private List<JournalCard> homeQuests() {
        return new JournalContentBuilder(data).activeQuestCards().stream()
                .sorted(java.util.Comparator.comparingInt(c -> de.quest.client.config.QuestTrackerFocus.current()
                        .matches(c.id(),c.title()) ? -1 : questPriority(c.id()))).toList();
    }
    record QuestMasterTarget(int x, int y, int width, int height) {}

    static QuestMasterTarget homeQuestMasterTarget(int left, int top) {
        return new QuestMasterTarget(left + 239, top + 184, 70, 15);
    }

    private void drawHomeTasks(GuiGraphics g,int left,int top,int mx,int my) {
        int x=left+104,y=top+180;
        JournalV3Visuals.paper(g,x,y,212,76);
        JournalV3Visuals.sprite(g,"scroll",x+4,y+3,19,19);
        JournalV3Visuals.heading(g,font,Component.translatable("screen.village-quest.journal.home.tasks").getString(),x+25,y+7,110,1.1f,INK);
        var masterTarget = homeQuestMasterTarget(left, top);
        String master=Component.translatable("screen.village-quest.journal.active.questmaster_button").getString();
        JournalV3Visuals.tealButton(g,font,master,masterTarget.x(),masterTarget.y(),
                masterTarget.width(),masterTarget.height(),true,
                within(mx,my,masterTarget.x(),masterTarget.y(),masterTarget.width(),masterTarget.height()));
        if(within(mx,my,masterTarget.x(),masterTarget.y(),masterTarget.width(),masterTarget.height()))g.setTooltipForNextFrame(font,Component.literal(master),mx,my);
        var quests=homeQuests();scrollMax=Math.max(0,quests.size()*28-50);clampScroll();
        g.enableScissor(x+5,y+21,x+206,y+71);
        for(int i=0;i<quests.size();i++) {
            var card=quests.get(i);int rowY=y+21+i*28-scrollOffset;
            if(rowY+27<y+21 || rowY>y+71)continue;
            JournalV3Visuals.paper(g,x+5,rowY,199,27);
            if(within(mx,my,x+5,rowY,199,27))g.fill(x+6,rowY+1,x+203,rowY+26,0x22FFFFFF);
            var art=artworkFor(card);
            art.ifPresent(m->JournalV3Visuals.thumbnail(g,m.smallTexture(),x+7,rowY+2,64,24));
            if(art.isEmpty())JournalV3Visuals.sprite(g,"scroll",x+27,rowY+2,24,24);
            VillageUiTheme.drawStringScaled(g,font,compact(card.title().getString(),112,.9f),x+78,rowY+4,INK,.9f);
            VillageUiTheme.drawStringScaled(g,font,compact(card.subtitle().getString(),112,.72f),x+78,rowY+16,BODY,.72f);
            VillageUiTheme.drawStringScaled(g,font,"›",x+194,rowY+9,INK,.9f);
            if(within(mx,my,x+5,rowY,199,27) && my>=y+21 && my<y+71) {
                var lines=new ArrayList<Component>();lines.add(card.title());lines.addAll(card.details());g.setTooltipForNextFrame(font,lines,mx,my);
            }
        }
        g.disableScissor();VillageUiTheme.drawScrollBar(g,x+207,y+22,47,50,quests.size()*28,scrollOffset,scrollMax);
    }
    private boolean handleHomeClick(int mx,int my,int left,int top) {
        for(int i=0;i<4;i++)if(within(mx,my,left+104+i*64,top+141,64,37)) {
            section=i==0?Section.QUESTS:i==1?Section.ATLAS:Section.NETWORK;
            networkPage=i==3?1:0;scrollOffset=0;expandedCardId="";ensureExpandedCard();playClick();return true;
        }
        if(within(mx,my,left+376,top+158,116,17)) {
            switch(homeDestination(data)) {
                case QUEST_MASTER -> summonQuestMasterFromJournal();
                case QUESTS -> {section=Section.QUESTS;expandedCardId=homeQuests().getFirst().id();scrollOffset=0;}
                case VILLAGES -> {section=Section.NETWORK;networkPage=0;scrollOffset=0;expandedCardId="network_next";}
            }
            playClick();return true;
        }
        if(within(mx,my,left+371,top+236,119,15)) {
            section=Section.NETWORK;networkPage=2;scrollOffset=0;expandedCardId="";ensureExpandedCard();playClick();return true;
        }
        var masterTarget = homeQuestMasterTarget(left, top);
        if(within(mx,my,masterTarget.x(),masterTarget.y(),masterTarget.width(),masterTarget.height())) {summonQuestMasterFromJournal();return true;}
        if(within(mx,my,left+109,top+201,199,50)) {
            int i=(my-(top+201)+scrollOffset)/28;var quests=homeQuests();
            if(i<quests.size()) {section=Section.QUESTS;expandedCardId=quests.get(i).id();scrollOffset=0;playClick();return true;}
        }
        return false;
    }

    private void drawCard(GuiGraphics graphics, JournalCard card, int x, int y, int cardWidth,
                          int cardHeight, int mouseX, int mouseY) {
        boolean expanded = card.id().equals(expandedCardId);
        boolean hovered = within(mouseX, mouseY, x, y, cardWidth, cardHeight);
        JournalV3Visuals.paper(graphics,x,y,cardWidth,cardHeight);
        if(hovered)graphics.fill(x+3,y+3,x+cardWidth-3,y+cardHeight-3,0x12FFFFFF);
        graphics.fill(x + 6, y + 6, x + 8, y + cardHeight - 6, card.accent());
        int headingInset = cardHeadingInset(card);
        artworkFor(card).ifPresent(motif -> JournalV3Visuals.thumbnail(graphics,
                motif.smallTexture(), x + 12, y + 5, 64, 24));
        List<String> headings = cardHeadingLines(card,cardWidth);
        for(int i=0;i<headings.size();i++) JournalV3Visuals.heading(graphics,font,headings.get(i),
                x+headingInset,y+6+i*10,cardWidth-headingInset-headingControlsWidth(card),.92f,INK);
        List<String> subtitles = (card.id().equals("overview_next") || card.id().equals("network_next"))
                ? wrapText(card.subtitle().getString(), Math.round((cardWidth - headingInset - 28) / CARD_BODY_SCALE))
                : List.of(compact(card.subtitle().getString(), cardWidth - headingInset - 28, CARD_BODY_SCALE));
        for (int i = 0; i < subtitles.size(); i++) {
            VillageUiTheme.drawStringScaled(graphics, font, subtitles.get(i),
                    x + headingInset, y + 18 + (headings.size()-1)*10 + i * CARD_DETAIL_STEP, BODY, CARD_BODY_SCALE);
        }
        graphics.fill(x+cardWidth-23,y+4,x+cardWidth-5,y+22,TEAL);
        JournalV3Visuals.chevron(graphics,x+cardWidth-22,y+5,16,expanded);
        if (isActiveQuestCard(card)) {
            boolean pinned = de.quest.client.config.QuestTrackerFocus.current().matches(card.id(), card.title());
            int px = x + cardWidth - 55, py = y + 4;
            graphics.fill(px, py, px + 20, py + 18, pinned ? TEAL : 0xFFC9AF7F);
            int ink = pinned ? 0xFFF5E4BA : INK;
            graphics.fill(px+6,py+4,px+14,py+6,ink);
            graphics.fill(px+8,py+6,px+12,py+11,ink);
            graphics.fill(px+5,py+10,px+15,py+12,ink);
            graphics.fill(px+9,py+12,px+11,py+16,ink);
            if (within(mouseX,mouseY,px,py,20,18)) graphics.setTooltipForNextFrame(font,
                    Component.translatable("screen.village-quest.tracker."+(pinned?"unpin":"pin")), mouseX,mouseY);
        }
        if (card.cancelAction() >= 0) {
            int cx=x+cardWidth-78,cy=y+12;
            if(pendingCancelAction==card.cancelAction()) graphics.fill(cx-9,cy-9,cx+10,cy+10,0x44AA3027);
            if(within(mouseX,mouseY,cx-9,cy-9,18,19)) graphics.setTooltipForNextFrame(font,
                    Component.translatable("screen.village-quest.ui_review.cancel_confirm"),mouseX,mouseY);
            for(int i=-5;i<=5;i++) {
                graphics.fill(cx+i,cy+i,cx+i+2,cy+i+2,0xFFAA3027);
                graphics.fill(cx+i,cy-i,cx+i+2,cy-i+2,0xFFAA3027);
            }
        }

        boolean socialGuild = card.id().equals("social_guild") && reputation != null;
        if (socialGuild) {
            int barX = x + CARD_TEXT_INSET, barY = y + 30, barWidth = cardWidth - CARD_TEXT_INSET - 18;
            graphics.fill(barX, barY, barX + barWidth, barY + 4, FRAME_DARK);
            int trust = reputation.guildTrust();
            graphics.fill(barX, barY, barX + ReputationPanel.progressPixels(trust, barWidth), barY + 4, TEAL);
            VillageUiTheme.drawStringScaled(graphics, font, Integer.toString(ReputationPanel.progressFloor(trust)), barX, barY + 6, BODY, 0.6f);
            String target = Integer.toString(ReputationPanel.progressTarget(trust));
            VillageUiTheme.drawStringScaled(graphics, font, target, barX + barWidth - Math.round(font.width(target) * 0.6f), barY + 6, BODY, 0.6f);
        }
        if (!expanded) {
            return;
        }
        int lineY = y + collapsedCardHeight(card, cardWidth) + (socialGuild ? 17 : 0);
        if (card.id().equals("overview_progress")) {
            for (int i = 0; i < card.details().size(); i++) {
                VillageUiTheme.drawStringScaled(graphics, font,
                        compact(card.details().get(i).getString(), (cardWidth - CARD_TEXT_INSET - 18) / 2, CARD_BODY_SCALE),
                        x + CARD_TEXT_INSET + (i % 2) * (cardWidth - CARD_TEXT_INSET - 18) / 2,
                        lineY + (i / 2) * CARD_DETAIL_STEP, BODY, CARD_BODY_SCALE);
            }
            return;
        }
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
        for (var line : compactDetails(card, cardWidth)) {
            VillageUiTheme.drawStringScaled(graphics, font, line.text(),
                    x + CARD_TEXT_INSET, lineY, line.complete()?0xFF306C42:BODY, CARD_BODY_SCALE);
            lineY += CARD_DETAIL_STEP;
        }
    }

    private void drawFooterButtons(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        if(section==Section.OVERVIEW)return;
        if (section==Section.QUESTS) {
            var focus = de.quest.client.config.QuestTrackerFocus.current();
            int x=left+365;
            drawButton(graphics,x,top+44,120,Component.translatable(
                    "screen.village-quest.tracker."+(focus.onlyPinned()?"only":"all")).getString(),
                    within(mouseX,mouseY,x,top+44,120,BUTTON_HEIGHT));
            if(within(mouseX,mouseY,x,top+44,120,BUTTON_HEIGHT)) graphics.setTooltipForNextFrame(font,
                    Component.translatable("screen.village-quest.tracker.filter_hint"),mouseX,mouseY);
        }
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
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.handleMouseClicked(click, doubled);
        }
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WINDOW_WIDTH, WINDOW_HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WINDOW_WIDTH, WINDOW_HEIGHT);
        if(within(mouseX,mouseY,left+WINDOW_WIDTH-25,top+3,20,20)) {onClose();return true;}
        if (!UiTestTour.active() && within(mouseX,mouseY,left+SETTINGS_X,top+SETTINGS_Y,SETTINGS_SIZE,SETTINGS_SIZE)
                && ClientPlayNetworking.canSend(de.quest.network.QuestSetupPayloads.Open.ID)) {
            ClientPlayNetworking.send(new de.quest.network.QuestSetupPayloads.Open()); return true;
        }
        for(int i=0;i<NAV_SECTIONS.length;i++) {
            int y=top+TAB_Y+i*(TAB_HEIGHT+TAB_GAP);
            if(within(mouseX,mouseY,left+TAB_X,y,TAB_WIDTH,TAB_HEIGHT)) {
                pendingCancelAction=-1;section=NAV_SECTIONS[i];if(section==Section.NETWORK)networkPage=i==3?1:0;
                scrollOffset=0;expandedCardId="";ensureExpandedCard();playClick();return true;
            }
        }

        if (section == Section.ATLAS) {
            return handleAtlasMouseClicked(mouseX, mouseY, left, top)
                    || super.handleMouseClicked(click, doubled);
        }

        if (section == Section.NETWORK && handleNetworkTab(mouseX, mouseY, left, top)) return true;
        if (section == Section.OVERVIEW) return handleHomeClick(mouseX,mouseY,left,top);

        if (section==Section.QUESTS && within(mouseX,mouseY,left+365,top+44,120,BUTTON_HEIGHT)) {
            if (!UiTestTour.active()) de.quest.client.config.QuestTrackerFocus.current().toggleOnlyPinned();
            playClick(); return true;
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
        int cardWidth = cardWidth();
        int y = top + contentTop() - scrollOffset;
        for (JournalCard card : cards) {
            int height = cardHeight(card, cardWidth);
            if (mouseY >= top + contentTop() && mouseY < top + CONTENT_BOTTOM && within(mouseX, mouseY, cardX, y, cardWidth, height)) {
                if (isActiveQuestCard(card) && within(mouseX,mouseY,cardX+cardWidth-55,y+4,20,18)) {
                    pendingCancelAction=-1;
                    if (!UiTestTour.active()) de.quest.client.config.QuestTrackerFocus.current().toggle(card.id(),card.title());
                    playClick(); return true;
                }
                if (card.cancelAction() >= 0
                        && within(mouseX, mouseY, cardX + cardWidth - 87, y + 3, 18, 19)) {
                    if(pendingCancelAction==card.cancelAction()) {
                        UiTestTour.send(new JournalActionPayload(card.cancelAction()));
                        pendingCancelAction=-1;
                    } else pendingCancelAction=card.cancelAction();
                    playClick();
                    return true;
                }
                if (card.id().equals("social_recent")) { networkPage = 2; scrollOffset = 0; ensureExpandedCard(); playPageTurn(); return true; }
                pendingCancelAction=-1;
                expandedCardId = card.id().equals(expandedCardId) ? "" : card.id();
                clampScroll();
                playPageTurn();
                return true;
            }
            y += height + CARD_GAP;
        }
        return super.handleMouseClicked(click, doubled);
    }

    private boolean handleAtlasMouseClicked(int mouseX, int mouseY, int left, int top) {
        if (atlasIntroVisible) {
            int introX = left + ATLAS_INTRO_X;
            int introY = top + ATLAS_INTRO_Y;
            if (within(mouseX, mouseY, introX + ATLAS_INTRO_WIDTH - 73,
                    introY + ATLAS_INTRO_HEIGHT - 23, 61, 16)) {
                atlasIntroVisible = false;
                if (!UiTestTour.active()) {
                    InventoryJournalTutorialState.markAtlasIntroSeen();
                    InventoryJournalTutorialState.markAtlasDragHintSeen();
                }
                atlasHintChecked = true;
                atlasHintUntilMs = 0L;
                playClick();
            }
            return true;
        }
        int x = left + 320;
        int[] widths = {40, 65, 57};
        AtlasPage[] pages = AtlasPage.values();
        for (int i = 0; i < pages.length; i++) {
            if (within(mouseX, mouseY, x, top + 44, widths[i], 15)) {
                atlasPage = pages[i];
                atlasHoveredNode = -1;
                ensureAtlasInitialized();
                playPageTurn();
                return true;
            }
            x += widths[i] + 2;
        }
        if (atlasPage != AtlasPage.TRUST
                && within(mouseX, mouseY, left + 110, top + 44, 49, 15)) {
            centerAtlasOnCurrent();
            playClick();
            return true;
        }
        if (within(mouseX, mouseY, left + 420, top + 239, 68, 17)) {
            onClose();
            return true;
        }
        if (atlasPage == AtlasPage.TRUST) {
            return trustRosterNodeAt(mouseX, mouseY, left, top, atlasNodes().size()) >= 0;
        }
        AtlasDetailPlacement detail = atlasDetailPlacement(left, top, atlasNodes(), atlasHoveredNode);
        if (detail != null && within(mouseX, mouseY,
                detail.x(), detail.y(), ATLAS_DETAIL_WIDTH, atlasDetailHeight(atlasNodes().get(atlasHoveredNode)))) {
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
        if (section == Section.ATLAS && atlasDragging && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && atlasDragging) {
            atlasDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        pendingCancelAction=-1;
        if (section == Section.ATLAS) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if(section==Section.OVERVIEW) {
            if(scrollMax>0 && within(uiMouseX,uiMouseY,left+109,top+201,199,50)) {scrollOffset-=(int)Math.signum(verticalAmount)*28;clampScroll();return true;}
            return false;
        }
        if (scrollMax > 0 && within(uiMouseX, uiMouseY,
                left + CONTENT_X + 5, top + contentTop(), cardViewportWidth(), CONTENT_BOTTOM - contentTop())) {
            scrollOffset -= (int) Math.signum(verticalAmount) * 22;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private List<JournalCard> cardsForSection() {
        JournalContentBuilder content = new JournalContentBuilder(data);
        return switch (section) {
            case OVERVIEW -> content.overviewCards().subList(0, 2);
            case QUESTS -> content.activeQuestCards().stream().sorted(java.util.Comparator.comparingInt(
                    card -> questPriority(card.id()))).toList();
            case NETWORK -> networkPage == 0 ? content.networkCards() : networkPage == 1 ? ReputationPanel.trustCards(reputation) : ReputationPanel.historyCards(reputation);
            case ATLAS -> List.of();
            case GUIDE -> {var all=new ArrayList<>(content.guideCards());all.addAll(content.overviewCards().subList(0,2));yield List.copyOf(all);}
        };
    }

    private static int questPriority(String id) {
        if(id.contains(":")) id=id.substring(0,id.indexOf(':'));
        return switch (id) {
            case "story" -> 0;
            case "special" -> 1;
            case "weekly" -> 2;
            case "daily" -> 3;
            case "pilgrim" -> 4;
            default -> 5;
        };
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
        int socialExtra = card.id().equals("social_guild") ? 17 : 0;
        if (!card.id().equals(expandedCardId)) {
            return collapsedCardHeight(card, width) + socialExtra;
        }
        int extra = reputationTrack(card) == null ? 6 : REPUTATION_PROGRESS_HEIGHT + 4;
        int details = card.id().equals("overview_progress") ? (card.details().size() + 1) / 2 : compactDetails(card, width).size();
        return collapsedCardHeight(card, width) + socialExtra + extra + details * CARD_DETAIL_STEP;
    }

    private int collapsedCardHeight(JournalCard card, int width) {
        int baseHeight = (artworkFor(card).isPresent() ? ILLUSTRATED_CARD_HEIGHT : CARD_COLLAPSED_HEIGHT)
                + (cardHeadingLines(card,width).size()-1)*10;
        if (!card.id().equals("overview_next") && !card.id().equals("network_next")) return baseHeight;
        int lines = wrapText(card.subtitle().getString(), Math.round((width - cardHeadingInset(card) - 28) / CARD_BODY_SCALE)).size();
        return baseHeight + Math.max(0, lines - 1) * CARD_DETAIL_STEP;
    }

    private List<String> cardHeadingLines(JournalCard card,int width) {
        int available=Math.max(24,width-cardHeadingInset(card)-headingControlsWidth(card));
        var lines=VillageUiTheme.wrapScaledLines(font,card.title().getString(),available,.92f);
        return lines.isEmpty()?List.of(card.title().getString()):lines;
    }

    private int headingControlsWidth(JournalCard card) {
        return card.cancelAction()>=0 ? 98 : isActiveQuestCard(card) ? 65 : 28;
    }
    private boolean isActiveQuestCard(JournalCard card) { return data!=null && data.activeTitles().containsKey(card.id()); }

    static Optional<QuestArtwork.Motif> artworkFor(JournalCard card) {
        Optional<QuestArtwork.Motif> motif = QuestArtwork.forQuest(card.id(), card.title());
        if (motif.isEmpty()) motif = QuestArtwork.forComponent(card.subtitle());
        return motif.filter(QuestArtwork.Motif::hasIllustration);
    }

    static int cardHeadingInset(JournalCard card) {
        return artworkFor(card).isPresent() ? CARD_IMAGE_TEXT_INSET : CARD_TEXT_INSET;
    }

    private List<de.quest.client.ui.QuestObjectiveText.Line> compactDetails(JournalCard card, int cardWidth) {
        int renderedWidth = Math.max(12, cardWidth - CARD_TEXT_INSET - 18);
        int unscaledWidth = Math.max(1, (int) Math.floor(renderedWidth / CARD_BODY_SCALE));
        return wrappedDetails(card, unscaledWidth);
    }

    private String compact(String text, int renderedWidth, float scale) {
        return VillageUiTheme.ellipsize(font, text,
                Math.max(1, (int) Math.floor(renderedWidth / scale)));
    }

    private List<de.quest.client.ui.QuestObjectiveText.Line> wrappedDetails(JournalCard card, int width) {
        var lines = new ArrayList<de.quest.client.ui.QuestObjectiveText.Line>();
        for (Component detail : card.details()) {
            lines.addAll(de.quest.client.ui.QuestObjectiveText.wrap(detail, text -> wrapText(text, width)));
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
        UiTestTour.command("vq routes");
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
        UiTestTour.command("vq prosperity");
        super.onClose();
    }

    private void summonQuestMasterFromJournal() {
        if (minecraft == null || minecraft.player == null) return;
        if (!UiTestTour.active()) InventoryJournalTutorialState.markQuestMasterButtonHintSeen();
        navigating = true;
        UiTestTour.command("vq questmaster");
        super.onClose();
    }

    private void sendJournalClose() {
        if (minecraft != null && minecraft.player != null) {
            UiTestTour.command("vq journal close");
        }
    }

    private boolean shouldShowQuestMasterTutorial() {
        return !UiTestTour.active() && section == Section.OVERVIEW && InventoryJournalTutorialState.shouldShowQuestMasterButtonHint();
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
        String suffix = "\u2026";
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
