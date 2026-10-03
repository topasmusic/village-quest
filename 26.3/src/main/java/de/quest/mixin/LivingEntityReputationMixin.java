package de.quest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.quest.reputation.ReputationDamageAdapter;
import de.quest.reputation.ReputationDamageAdapter.DamageFrame;
import java.util.ArrayDeque;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;

/** hurtServer's return value and post-call health, including lethal hits missed by Fabric AFTER_DAMAGE. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityReputationMixin {
    @Shadow protected boolean dead;
    @Unique private final ArrayDeque<DamageFrame> villageQuest$damageFrames = new ArrayDeque<>();

    @WrapMethod(method = "hurtServer")
    private boolean villageQuest$acceptedDamage(ServerLevel world, DamageSource source, float requested, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        DamageFrame frame = ReputationDamageAdapter.begin(world, self, source);
        if (frame == null) return original.call(world, source, requested);
        villageQuest$damageFrames.push(frame);
        try {
            boolean accepted = original.call(world, source, requested);
            // actuallyHurt captures loss before totems/after-damage healing can restore health.
            float damage = accepted ? Math.max(0, frame.actualDamage - frame.childDamage) : 0;
            ReputationDamageAdapter.finish(world, self, source, frame, damage);
            return accepted;
        } finally {
            villageQuest$damageFrames.pop();
            if (!villageQuest$damageFrames.isEmpty() && villageQuest$damageFrames.peek().measuringDamage)
                villageQuest$damageFrames.peek().childDamage += frame.actualDamage;
        }
    }
    @WrapMethod(method = "actuallyHurt")
    private void villageQuest$measureLoss(ServerLevel world, DamageSource source, float requested, Operation<Void> original) {
        DamageFrame frame = villageQuest$damageFrames.peek();
        if (frame == null) { original.call(world, source, requested); return; }
        LivingEntity self = (LivingEntity) (Object) this;
        float health = self.getHealth(), absorption = self.getAbsorptionAmount(); frame.measuringDamage = true;
        try {
            original.call(world, source, requested);
            frame.actualDamage += ReputationDamageAdapter.acceptedDamage(health, absorption, self.getHealth(), self.getAbsorptionAmount(), true);
        } finally { frame.measuringDamage = false; }
    }
    @WrapMethod(method = "die")
    private void villageQuest$confirmedDeath(DamageSource source, Operation<Void> original) {
        boolean previouslyDead = dead;
        original.call(source);
        DamageFrame frame = villageQuest$damageFrames.peek();
        if (frame != null && !previouslyDead && dead) frame.fatalConfirmed = true;
    }
}
