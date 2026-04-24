package de.quest.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class CaravanMerchantEntity extends PathfinderMob {
    public static final int DEFAULT_DESPAWN_TICKS = 20 * 60 * 8;
    private static final int DEFENSE_COOLDOWN_TICKS = 60;

    private int despawnTicks = DEFAULT_DESPAWN_TICKS;
    private int encounterControlTicks;
    private int defenseCooldownTicks;
    private boolean courier;
    private boolean encounterGuard;

    public CaravanMerchantEntity(EntityType<? extends PathfinderMob> entityType, Level world) {
        super(entityType, world);
        this.setPersistenceRequired();
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 90.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
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
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            this.getLookControl().setLookAt(serverPlayer.getX(), serverPlayer.getEyeY(), serverPlayer.getZ());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput data) {
        super.addAdditionalSaveData(data);
        data.putInt("DespawnTicks", this.despawnTicks);
        data.putBoolean("Courier", this.courier);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput data) {
        super.readAdditionalSaveData(data);
        this.despawnTicks = Math.max(0, data.getIntOr("DespawnTicks", DEFAULT_DESPAWN_TICKS));
        this.courier = data.getBooleanOr("Courier", false);
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

    public boolean tryDefendAgainst(ServerLevel world, LivingEntity target) {
        if (!isEncounterGuardActive() || world == null || target == null || this.defenseCooldownTicks > 0) {
            return false;
        }
        if (!target.isAlive() || target.isRemoved() || this.distanceToSqr(target) > 2.7D * 2.7D) {
            return false;
        }
        this.defenseCooldownTicks = DEFENSE_COOLDOWN_TICKS;
        this.swing(InteractionHand.MAIN_HAND);
        return this.doHurtTarget(world, target);
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
        if (isNight()) {
            setHeldItem(Items.TORCH);
            return;
        }
        setHeldItem(null);
    }

    private void setHeldItem(Item item) {
        ItemStack current = this.getMainHandItem();
        if (item == null) {
            if (!current.isEmpty()) {
                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            return;
        }
        if (current.is(item) && current.getCount() == 1) {
            return;
        }
        this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    private boolean isNight() {
        if (this.level() == null) {
            return false;
        }
        long dayTime = Math.floorMod(this.level().getOverworldClockTime(), 24000L);
        return dayTime >= 13000L && dayTime <= 23000L;
    }
}
