package de.quest.mixin;

import de.quest.client.ui.InventoryJournalButtonLayout;
import de.quest.client.ui.InventoryJournalCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow
    protected int x;

    @Shadow
    protected int y;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private boolean villageQuest$isPlayerInventoryScreen() {
        return (Object) this instanceof InventoryScreen;
    }

    @Unique
    private InventoryJournalButtonLayout.Layout villageQuest$journalLayout() {
        return InventoryJournalButtonLayout.resolve((HandledScreenAccessor) (Object) this, villageQuest$useTopRightFallback());
    }

    @Unique
    private boolean villageQuest$useTopRightFallback() {
        return InventoryJournalCompat.shouldUseTopRightFallback(MinecraftClient.getInstance());
    }

    @Unique
    private boolean villageQuest$isHoveringJournalButton(double mouseX, double mouseY) {
        InventoryJournalButtonLayout.Layout layout = villageQuest$journalLayout();
        int inventoryTop = this.y;
        int inventoryRight = this.x + ((HandledScreenAccessor) (Object) this).villageQuest$getBackgroundWidth();
        int x = layout.expandedX();
        int y = layout.expandedY();
        int visibleWidth = layout.width();
        int visibleHeight = layout.height();
        if (layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_RIGHT) {
            x = Math.max(layout.expandedX(), inventoryRight);
            visibleWidth = (layout.expandedX() + layout.width()) - x;
        } else if (layout.mode() == InventoryJournalButtonLayout.LayoutMode.BOOKMARK_TAB_TOP_RIGHT) {
            visibleHeight = Math.min(layout.height(), inventoryTop - y);
        }
        if (visibleWidth <= 0 || visibleHeight <= 0) {
            return false;
        }
        return mouseX >= x
                && mouseX < x + visibleWidth
                && mouseY >= y
                && mouseY < y + visibleHeight;
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), cancellable = true)
    private void villageQuest$clickJournalButton(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!villageQuest$isPlayerInventoryScreen()
                || click.button() != 0
                || !villageQuest$isHoveringJournalButton(click.x(), click.y())) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand("vq journal");
            cir.setReturnValue(true);
        }
    }
}
