package de.quest.mixin;

import de.quest.quest.QuestDropTracker;
import de.quest.quest.TrackedQuestDrop;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
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

    @Inject(method = "tryMerge(Lnet/minecraft/entity/ItemEntity;)V", at = @At("HEAD"), cancellable = true)
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
            method = "onPlayerCollision",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;sendPickup(Lnet/minecraft/entity/Entity;I)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void villageQuest$onTrackedItemPickup(PlayerEntity player, CallbackInfo ci, ItemStack stack, Item item, int count) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            QuestDropTracker.onTrackedItemPickup(serverPlayer, (ItemEntity) (Object) this, count);
        }
    }
}
