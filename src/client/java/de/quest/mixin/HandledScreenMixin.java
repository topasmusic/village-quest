package de.quest.mixin;

import de.quest.VillageQuest;
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
    @Unique
    private static final int VILLAGE_QUEST$JOURNAL_BUTTON_SIZE = 20;

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
    private int villageQuest$journalButtonX() {
        return this.x + 132;
    }

    @Unique
    private int villageQuest$journalButtonY() {
        return this.y + 61;
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

    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), cancellable = true)
    private void villageQuest$clickJournalButton(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!villageQuest$isPlayerInventoryScreen()
                || click.button() != 0
                || !villageQuest$isHoveringJournalButton(click.x(), click.y())) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand("journal");
            cir.setReturnValue(true);
        }
    }
}
