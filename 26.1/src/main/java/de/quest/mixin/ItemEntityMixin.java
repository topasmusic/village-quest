package de.quest.mixin;

import de.quest.quest.QuestDropTracker;
import de.quest.quest.TrackedQuestDrop;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements TrackedQuestDrop {
    @Unique
    private UUID villageQuest$trackedPlayerId;

    @Override
    public UUID villageQuest$getTrackedPlayerId() {
        return villageQuest$trackedPlayerId;
    }

    @Override
    public void villageQuest$setTrackedPlayerId(UUID playerId) {
        this.villageQuest$trackedPlayerId = playerId;
    }

    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void villageQuest$preventTrackedDropMerges(ItemEntity other, CallbackInfo ci) {
        UUID otherTrackedPlayerId = ((TrackedQuestDrop) other).villageQuest$getTrackedPlayerId();
        if (villageQuest$trackedPlayerId == null && otherTrackedPlayerId == null) {
            return;
        }
        if (!Objects.equals(villageQuest$trackedPlayerId, otherTrackedPlayerId)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "playerTouch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void villageQuest$onTrackedItemPickup(Player player, CallbackInfo ci, ItemStack stack, Item item, int count) {
        if (player instanceof ServerPlayer serverPlayer) {
            QuestDropTracker.onTrackedItemPickup(serverPlayer, (ItemEntity) (Object) this, count);
        }
    }
}
