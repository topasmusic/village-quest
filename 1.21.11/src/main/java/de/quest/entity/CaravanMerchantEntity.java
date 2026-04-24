package de.quest.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class CaravanMerchantEntity extends PathAwareEntity {
    public static final int DEFAULT_DESPAWN_TICKS = 20 * 60 * 8;
    private static final int DEFENSE_COOLDOWN_TICKS = 60;

    private int despawnTicks = DEFAULT_DESPAWN_TICKS;
    private int encounterControlTicks;
    private int defenseCooldownTicks;
    private boolean courier;
    private boolean encounterGuard;

    public CaravanMerchantEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 90.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 1.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.FOLLOW_RANGE, 24.0)
                .add(EntityAttributes.ARMOR, 2.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getEntityWorld().isClient()) {
            return;
        }
        if (this.encounterControlTicks > 0) {
            this.encounterControlTicks--;
        } else {
            this.encounterGuard = false;
            this.getNavigation().stop();
        }
        if (this.defenseCooldownTicks > 0) {
            this.defenseCooldownTicks--;
        }
        updateHeldItemState();
        if (this.despawnTicks > 0) {
            this.despawnTicks--;
        }
        if (this.despawnTicks == 0) {
            this.discard();
        }
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            this.getLookControl().lookAt(serverPlayer.getX(), serverPlayer.getEyeY(), serverPlayer.getZ());
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void writeCustomData(WriteView data) {
        super.writeCustomData(data);
        data.putInt("DespawnTicks", this.despawnTicks);
        data.putBoolean("Courier", this.courier);
    }

    @Override
    protected void readCustomData(ReadView data) {
        super.readCustomData(data);
        this.despawnTicks = Math.max(0, data.getInt("DespawnTicks", DEFAULT_DESPAWN_TICKS));
        this.courier = data.getBoolean("Courier", false);
    }

    public void setCourier(boolean courier) {
        this.courier = courier;
        updateHeldItemState();
    }

    public boolean isCourier() {
        return this.courier;
    }

    public void setDespawnTicks(int despawnTicks) {
        this.despawnTicks = Math.max(0, despawnTicks);
    }

    public void refreshEncounterControl(boolean guard) {
        this.encounterControlTicks = 20 * 3;
        this.encounterGuard = guard;
        updateHeldItemState();
    }

    public void clearEncounterControl() {
        this.encounterControlTicks = 0;
        this.encounterGuard = false;
        this.getNavigation().stop();
        updateHeldItemState();
    }

    public boolean tryDefendAgainst(ServerWorld world, LivingEntity target) {
        if (!isEncounterGuardActive() || world == null || target == null || this.defenseCooldownTicks > 0) {
            return false;
        }
        if (!target.isAlive() || target.isRemoved() || this.squaredDistanceTo(target) > 2.7D * 2.7D) {
            return false;
        }
        this.defenseCooldownTicks = DEFENSE_COOLDOWN_TICKS;
        this.swingHand(Hand.MAIN_HAND);
        return this.tryAttack(world, target);
    }

    private boolean isEncounterGuardActive() {
        return this.encounterGuard && this.encounterControlTicks > 0 && !this.courier;
    }

    private void updateHeldItemState() {
        if (this.courier) {
            setHeldItem(Items.PAPER);
            return;
        }
        if (isEncounterGuardActive()) {
            setHeldItem(Items.WOODEN_SWORD);
            return;
        }
        if (this.getEntityWorld() != null && this.getEntityWorld().isNight()) {
            setHeldItem(Items.TORCH);
            return;
        }
        setHeldItem(null);
    }

    private void setHeldItem(Item item) {
        ItemStack current = this.getMainHandStack();
        if (item == null) {
            if (!current.isEmpty()) {
                this.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            }
            return;
        }
        if (current.isOf(item) && current.getCount() == 1) {
            return;
        }
        this.setStackInHand(Hand.MAIN_HAND, new ItemStack(item));
    }
}
