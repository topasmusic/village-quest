package de.quest.mixin;

import de.quest.VillageQuest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends Screen {
    @Unique
    private static final Identifier VILLAGE_QUEST$JOURNAL_BUTTON_TEXTURE =
            Identifier.of(VillageQuest.MOD_ID, "textures/gui/journal_inventory_button.png");
    @Unique
    private static final int VILLAGE_QUEST$JOURNAL_BUTTON_SIZE = 18;
    @Unique
    private static final int VILLAGE_QUEST$JOURNAL_ICON_SIZE = 14;
    @Unique
    private static final int VILLAGE_QUEST$JOURNAL_TEXTURE_SIZE = 14;

    protected InventoryScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private int villageQuest$journalButtonX() {
        return ((HandledScreenAccessor) (Object) this).villageQuest$getX() + 132;
    }

    @Unique
    private int villageQuest$journalButtonY() {
        return ((HandledScreenAccessor) (Object) this).villageQuest$getY() + 61;
    }

    @Unique
    private boolean villageQuest$isHoveringJournalButton(double mouseX, double mouseY) {
        int x = villageQuest$journalButtonX();
        int y = villageQuest$journalButtonY();
        return mouseX >= x
                && mouseX < x + VILLAGE_QUEST$JOURNAL_BUTTON_SIZE
                && mouseY >= y
                && mouseY < y + VILLAGE_QUEST$JOURNAL_BUTTON_SIZE;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void villageQuest$renderJournalButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = villageQuest$journalButtonX();
        int y = villageQuest$journalButtonY();
        boolean hovered = villageQuest$isHoveringJournalButton(mouseX, mouseY);
        int border = hovered ? 0xFFF0D7A7 : 0xFF6F4A2C;
        int inner = hovered ? 0xCC3A2617 : 0xCC24170E;

        context.fill(x, y, x + VILLAGE_QUEST$JOURNAL_BUTTON_SIZE, y + VILLAGE_QUEST$JOURNAL_BUTTON_SIZE, border);
        context.fill(x + 1, y + 1, x + VILLAGE_QUEST$JOURNAL_BUTTON_SIZE - 1, y + VILLAGE_QUEST$JOURNAL_BUTTON_SIZE - 1, inner);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                VILLAGE_QUEST$JOURNAL_BUTTON_TEXTURE,
                x + 2,
                y + 2,
                0.0f,
                0.0f,
                VILLAGE_QUEST$JOURNAL_ICON_SIZE,
                VILLAGE_QUEST$JOURNAL_ICON_SIZE,
                VILLAGE_QUEST$JOURNAL_TEXTURE_SIZE,
                VILLAGE_QUEST$JOURNAL_TEXTURE_SIZE
        );

        if (hovered) {
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.translatable("screen.village-quest.inventory.journal_button"), mouseX, mouseY);
        }
    }
}
