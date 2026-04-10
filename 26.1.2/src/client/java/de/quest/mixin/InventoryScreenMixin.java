package de.quest.mixin;

import de.quest.client.ui.InventoryJournalButtonLayout;
import de.quest.client.ui.InventoryJournalCompat;
import de.quest.client.ui.InventoryJournalTutorialState;
import de.quest.client.ui.TutorialHintRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import de.quest.VillageQuest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends Screen {
    @Unique
    private static final Identifier VILLAGE_QUEST$JOURNAL_BUTTON_TEXTURE =
            Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/journal_inventory_button.png");
    @Unique
    private static final int VILLAGE_QUEST$JOURNAL_ICON_SIZE = 14;
    @Unique
    private static final int VILLAGE_QUEST$JOURNAL_TEXTURE_SIZE = 14;
    @Unique
    private float villageQuest$journalRevealProgress = 0.0f;

    protected InventoryScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private InventoryJournalButtonLayout.Layout villageQuest$journalLayout() {
        return InventoryJournalButtonLayout.resolve((HandledScreenAccessor) (Object) this, villageQuest$useTopRightFallback());
    }

    @Unique
    private int villageQuest$inventoryRight() {
        HandledScreenAccessor accessor = (HandledScreenAccessor) (Object) this;
        return accessor.villageQuest$getX() + accessor.villageQuest$getBackgroundWidth();
    }

    @Unique
    private int villageQuest$inventoryTop() {
        return ((HandledScreenAccessor) (Object) this).villageQuest$getY();
    }

    @Unique
    private boolean villageQuest$useTopRightFallback() {
        return InventoryJournalCompat.shouldUseTopRightFallback(Minecraft.getInstance());
    }

    @Unique
    private boolean villageQuest$shouldShowJournalTutorial() {
        Minecraft client = Minecraft.getInstance();
        return client != null
                && client.player != null
                && InventoryJournalTutorialState.shouldShowInventoryHint();
    }

    @Unique
    private VisibleArea villageQuest$visibleArea(InventoryJournalButtonLayout.Layout layout, float revealProgress) {
        int renderX = layout.renderX(revealProgress);
        int renderY = layout.renderY(revealProgress);
        if (layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_RIGHT) {
            int x = Math.max(renderX, villageQuest$inventoryRight());
            int width = (renderX + layout.width()) - x;
            return new VisibleArea(x, renderY, width, layout.height());
        }
        if (layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_TOP_RIGHT) {
            int height = Math.min(layout.height(), villageQuest$inventoryTop() - renderY);
            return new VisibleArea(renderX, renderY, layout.width(), height);
        }
        return new VisibleArea(renderX, renderY, layout.width(), layout.height());
    }

    @Unique
    private boolean villageQuest$isHoveringJournalButton(double mouseX, double mouseY, float revealProgress) {
        InventoryJournalButtonLayout.Layout layout = villageQuest$journalLayout();
        VisibleArea visibleArea = villageQuest$visibleArea(layout, revealProgress);
        if (visibleArea.width() <= 0 || visibleArea.height() <= 0) {
            return false;
        }
        return mouseX >= visibleArea.x()
                && mouseX < visibleArea.x() + visibleArea.width()
                && mouseY >= visibleArea.y()
                && mouseY < visibleArea.y() + visibleArea.height();
    }

    @Unique
    private boolean villageQuest$isHoveringJournalButton(double mouseX, double mouseY) {
        return villageQuest$isHoveringJournalButton(mouseX, mouseY, villageQuest$journalRevealProgress);
    }

    @Inject(
            method = "extractBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void villageQuest$renderJournalButtonBehindInventory(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryJournalButtonLayout.Layout layout = villageQuest$journalLayout();
        boolean hovered = villageQuest$isHoveringJournalButton(mouseX, mouseY);
        boolean tutorialVisible = villageQuest$shouldShowJournalTutorial();
        villageQuest$journalRevealProgress = Mth.lerp(0.35f, villageQuest$journalRevealProgress, hovered || tutorialVisible ? 1.0f : 0.0f);
        if (villageQuest$journalRevealProgress < 0.02f) {
            villageQuest$journalRevealProgress = 0.0f;
        } else if (villageQuest$journalRevealProgress > 0.98f) {
            villageQuest$journalRevealProgress = 1.0f;
        }

        int x = layout.renderX(villageQuest$journalRevealProgress);
        int y = layout.renderY(villageQuest$journalRevealProgress);
        int width = layout.width();
        int height = layout.height();
        int border = hovered ? 0xFFF0D7A7 : 0xFF6F4A2C;
        int inner = hovered ? 0xCC3A2617 : 0xCC24170E;
        int accent = hovered ? 0x99F5E1B7 : 0x664A3420;

        if (layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_RIGHT) {
            int overlap = layout.overlap();
            int contentWidth = layout.contentWidth();
            int contentX = x + overlap;

            context.fill(x, y, x + width, y + height, border);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, inner);
            context.fill(contentX + 1, y + 3, x + width - 2, y + 4, accent);
            context.fill(contentX + 1, y + height - 5, x + width - 2, y + height - 4, accent);
            context.fill(x + 2, y + 5, x + overlap + 1, y + 7, hovered ? 0xFFF0D7A7 : 0xFFB88A46);
            context.fill(x + 2, y + height - 8, x + overlap + 1, y + height - 6, hovered ? 0xFFF0D7A7 : 0xFFB88A46);
        } else if (layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_TOP_RIGHT) {
            int overlap = layout.overlap();
            int contentHeight = layout.contentHeight();
            int contentY = y;

            context.fill(x, y, x + width, y + height, border);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, inner);
            context.fill(x + 3, contentY + 1, x + 4, y + contentHeight - 1, accent);
            context.fill(x + width - 5, contentY + 1, x + width - 4, y + contentHeight - 1, accent);
            context.fill(x + 5, y + height - overlap - 1, x + width - 5, y + height - overlap + 1, hovered ? 0xFFF0D7A7 : 0xFFB88A46);
        } else {
            context.fill(x, y, x + width, y + height, border);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, inner);
        }

        int iconAreaWidth = layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_RIGHT
                ? layout.contentWidth()
                : width;
        int iconBaseX = layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_RIGHT
                ? x + layout.overlap()
                : x;
        int iconAreaHeight = layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_TOP_RIGHT
                ? layout.contentHeight()
                : height;
        int iconBaseY = layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_TOP_RIGHT
                ? y
                : y;
        int iconX = iconBaseX + Math.max(1, (iconAreaWidth - VILLAGE_QUEST$JOURNAL_ICON_SIZE) / 2);
        int iconY = iconBaseY + Math.max(1, (iconAreaHeight - VILLAGE_QUEST$JOURNAL_ICON_SIZE) / 2);
        context.blit(
                RenderPipelines.GUI_TEXTURED,
                VILLAGE_QUEST$JOURNAL_BUTTON_TEXTURE,
                iconX,
                iconY,
                0.0f,
                0.0f,
                VILLAGE_QUEST$JOURNAL_ICON_SIZE,
                VILLAGE_QUEST$JOURNAL_ICON_SIZE,
                VILLAGE_QUEST$JOURNAL_TEXTURE_SIZE,
                VILLAGE_QUEST$JOURNAL_TEXTURE_SIZE
        );

        if (tutorialVisible) {
            TutorialHintRenderer.drawHint(
                    new GuiGraphics(context),
                    Minecraft.getInstance().font,
                    Component.translatable("screen.village-quest.inventory.journal_button_tutorial"),
                    this.width,
                    this.height,
                    x,
                    y,
                    width,
                    height,
                    TutorialHintRenderer.Placement.RIGHT,
                    false,
                    (int) Math.round(Math.sin(System.currentTimeMillis() / 180.0d) * 2.0d)
            );
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void villageQuest$renderJournalTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean hovered = villageQuest$isHoveringJournalButton(mouseX, mouseY);
        if (hovered) {
            context.setTooltipForNextFrame(Minecraft.getInstance().font, Component.translatable("screen.village-quest.inventory.journal_button"), mouseX, mouseY);
        }
    }

    @Unique
    private record VisibleArea(int x, int y, int width, int height) {}
}
