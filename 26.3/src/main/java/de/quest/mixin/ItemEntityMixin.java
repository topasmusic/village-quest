package de.quest.mixin;

import de.quest.quest.QuestDropTracker;
import de.quest.quest.TrackedQuestDrop;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements TrackedQuestDrop {
    @Shadow private EntityReference<Entity> thrower;

    @Override
    public boolean villageQuest$hasThrower() {
        return thrower != null;
    }

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
        TrackedQuestDrop otherDrop = (TrackedQuestDrop) other;
        // Before attribution, a vanilla merge can erase the thrower of the smaller stack.
        Item otherItem = other.getItem().getItem();
        if ((otherItem == Items.INK_SAC || otherItem == Items.FEATHER)
                && villageQuest$hasThrower() != otherDrop.villageQuest$hasThrower()) {
            ci.cancel();
            return;
        }
        UUID otherTrackedPlayerId = otherDrop.villageQuest$getTrackedPlayerId();
        if (villageQuest$trackedPlayerId == null && otherTrackedPlayerId == null) {
            return;
        }
        if (!Objects.equals(villageQuest$trackedPlayerId, otherTrackedPlayerId)) {
            ci.cancel();
        }
    }

    // Resolve pending natural drops while their stack still has its item/count.
    @Inject(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private void villageQuest$trackBeforeInventoryMutation(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            QuestDropTracker.onEntityLoad((ItemEntity) (Object) this, (ServerLevel) serverPlayer.level());
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
            // Vanilla's local count is the ORIGINAL count, and stack is already mutated.
            int pickedUp = count - stack.getCount();
            QuestDropTracker.onTrackedItemPickup(serverPlayer, (ItemEntity) (Object) this,
                    new ItemStack(item, pickedUp), pickedUp);
        }
    }
}
