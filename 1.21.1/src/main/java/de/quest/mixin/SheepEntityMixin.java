package de.quest.mixin;

import de.quest.quest.QuestDropTracker;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SheepEntity.class)
public abstract class SheepEntityMixin {
    @Redirect(
            method = "sheared",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/passive/SheepEntity;dropItem(Lnet/minecraft/item/ItemConvertible;I)Lnet/minecraft/entity/ItemEntity;"
            )
    )
    private ItemEntity villageQuest$trackShearedWool(SheepEntity sheep, ItemConvertible item, int count) {
        ItemEntity dropped = sheep.dropItem(item, count);
        if (dropped != null && sheep.getWorld() instanceof ServerWorld world) {
            QuestDropTracker.onShearedDrop(world, sheep, dropped.getStack().copy());
        }
        return dropped;
    }

    @Inject(method = "sheared", at = @At("TAIL"))
    private void villageQuest$shearedFinished(SoundCategory category, CallbackInfo ci) {
        SheepEntity sheep = (SheepEntity) (Object) this;
        if (sheep.getWorld() instanceof ServerWorld world) {
            QuestDropTracker.onShearedFinished(world, sheep);
        }
    }
}
