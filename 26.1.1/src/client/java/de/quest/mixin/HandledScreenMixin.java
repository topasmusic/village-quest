package de.quest.mixin;

import de.quest.client.ui.InventoryJournalButtonLayout;
import de.quest.client.ui.InventoryJournalCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    protected HandledScreenMixin(Component title) {
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
        return InventoryJournalCompat.shouldUseTopRightFallback(Minecraft.getInstance());
    }

    @Unique
    private boolean villageQuest$isHoveringJournalButton(double mouseX, double mouseY) {
        InventoryJournalButtonLayout.Layout layout = villageQuest$journalLayout();
        int inventoryTop = this.topPos;
        int inventoryRight = this.leftPos + ((HandledScreenAccessor) (Object) this).villageQuest$getBackgroundWidth();
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

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void villageQuest$clickJournalButton(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!villageQuest$isPlayerInventoryScreen()
                || click.button() != 0
                || !villageQuest$isHoveringJournalButton(click.x(), click.y())) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.connection != null) {
            client.player.connection.sendCommand("journal");
            cir.setReturnValue(true);
        }
    }
}
