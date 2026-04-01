package de.quest.entity;

import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterService;
import de.quest.questmaster.QuestMasterUiService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class QuestMasterEntity extends PathfinderMob {
    public static final int DEFAULT_DESPAWN_TICKS = 600;
    private static final int CUSTOMER_HOLD_TICKS = 60;
    private static final int ARRIVAL_TRACK_TICKS = 20 * 5;
    private static final double ARRIVAL_TRACK_SPEED = 0.95;
    private static final double ARRIVAL_STOP_DISTANCE_SQUARED = 9.0;
    private static final int DEFENSE_DURATION_TICKS = 200;
    private static final int MELEE_COOLDOWN_TICKS = 24;
    private static final int SPEECH_COOLDOWN_TICKS = 160;
    private static final float DEFENSE_TRIGGER_HEALTH_FRACTION = 0.20f;
    private static final double MAX_DEFENSE_DISTANCE_SQUARED = 324.0;
    private static final double DEFENSE_CHASE_SPEED = 0.95;
    private static final String[] WARNING_KEYS = new String[] {
            "message.village-quest.questmaster.warning.1",
            "message.village-quest.questmaster.warning.2",
            "message.village-quest.questmaster.warning.3",
            "message.village-quest.questmaster.warning.4"
    };
    private static final String[] RETALIATION_KEYS = new String[] {
            "message.village-quest.questmaster.retaliation.1",
            "message.village-quest.questmaster.retaliation.2",
            "message.village-quest.questmaster.retaliation.3",
            "message.village-quest.questmaster.retaliation.4"
    };

    private int despawnTicks = DEFAULT_DESPAWN_TICKS;
    private int customerHoldTicks;
    private int defenseTicks;
    private int meleeCooldownTicks;
    private int speechCooldownTicks;
    private UUID customerUuid;
    private UUID lastAggressorUuid;
    private UUID arrivalTargetUuid;
    private int arrivalTrackTicks;

    public QuestMasterEntity(EntityType<? extends PathfinderMob> entityType, Level world) {
        super(entityType, world);
        this.setPersistenceRequired();
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, DEFENSE_CHASE_SPEED, false));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.1));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.speechCooldownTicks > 0) {
            this.speechCooldownTicks--;
        }
        if (this.meleeCooldownTicks > 0) {
            this.meleeCooldownTicks--;
        }
        tickDefenseState();
        tickArrivalTracking();

        ServerPlayer customer = getCustomer();
        boolean uiSessionOpen = customer != null && QuestMasterUiService.isOpen(customer.getUUID());
        if (customer != null) {
            if (!canKeepCustomer(customer)) {
                QuestMasterUiService.close(customer);
                clearCustomer();
                uiSessionOpen = false;
            } else {
                this.getNavigation().stop();
                this.getLookControl().setLookAt(customer.getX(), customer.getEyeY(), customer.getZ());
                if (uiSessionOpen
                        || DailyQuestService.hasPendingQuestMasterOffer((ServerLevel) this.level(), customer.getUUID())) {
                    this.customerHoldTicks = CUSTOMER_HOLD_TICKS;
                } else if (this.customerHoldTicks > 0) {
                    this.customerHoldTicks--;
                } else {
                    QuestMasterUiService.close(customer);
                    clearCustomer();
                }
            }
        }

        if (uiSessionOpen) {
            updateHeldItemState();
            this.despawnTicks = Math.max(this.despawnTicks, 40);
            return;
        }

        if (this.despawnTicks > 0) {
            this.despawnTicks--;
        }
        updateHeldItemState();
        if (this.despawnTicks == 0) {
            if (customer != null) {
                QuestMasterUiService.close(customer);
            }
            clearCustomer();
            this.discard();
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (hasCustomer()) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            this.xxa = 0.0f;
            this.zza = 0.0f;
            this.walkAnimation.update(0.05f, 0.15f, 0.0f);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        boolean damaged = super.hurtServer(world, source, amount);
        if (!damaged) {
            return damaged;
        }

        ServerPlayer attacker = resolvePlayerAttacker(source);
        if (attacker == null) {
            return damaged;
        }

        this.lastAggressorUuid = attacker.getUUID();
        if (!this.isAlive()) {
            return true;
        }

        if (isDefending()) {
            refreshDefense(attacker);
            return true;
        }

        if (this.getHealth() <= this.getMaxHealth() * DEFENSE_TRIGGER_HEALTH_FRACTION) {
            enterDefense(world, attacker);
        } else {
            speak(attacker, WARNING_KEYS, ChatFormatting.YELLOW);
        }
        return true;
    }

    @Override
    public void die(DamageSource source) {
        ServerPlayer customer = getCustomer();
        if (customer != null) {
            QuestMasterUiService.close(customer);
        }
        clearCustomer();
        super.die(source);

        if (!(this.level() instanceof ServerLevel world)) {
            return;
        }
        ServerPlayer attacker = resolvePlayerAttacker(source);
        if (attacker == null) {
            return;
        }
        QuestMasterService.applyPlayerSummonCooldown(world, attacker.getUUID());
        attacker.sendSystemMessage(Component.translatable(
                "message.village-quest.questmaster.death_cooldown",
                QuestMasterService.formatDuration(QuestMasterService.getPlayerSummonCooldownTicks())
        ).withStyle(ChatFormatting.RED), false);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (isDefending()) {
            serverPlayer.sendSystemMessage(Component.translatable("message.village-quest.questmaster.defending").withStyle(ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }
        if (hasCustomer() && !isCustomer(serverPlayer)) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.village-quest.questmaster.busy").withStyle(net.minecraft.ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }

        QuestMasterService.interact((ServerLevel) serverPlayer.level(), serverPlayer, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput data) {
        super.addAdditionalSaveData(data);
        data.putInt("DespawnTicks", this.despawnTicks);
        data.putInt("CustomerHoldTicks", this.customerHoldTicks);
        data.putInt("ArrivalTrackTicks", this.arrivalTrackTicks);
        data.putString("ArrivalTarget", this.arrivalTargetUuid == null ? "" : this.arrivalTargetUuid.toString());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput data) {
        super.readAdditionalSaveData(data);
        this.despawnTicks = Math.max(0, data.getIntOr("DespawnTicks", DEFAULT_DESPAWN_TICKS));
        this.customerHoldTicks = Math.max(0, data.getIntOr("CustomerHoldTicks", 0));
        this.customerUuid = null;
        this.arrivalTrackTicks = Math.max(0, data.getIntOr("ArrivalTrackTicks", 0));
        String encodedArrivalTarget = data.getStringOr("ArrivalTarget", "");
        if (encodedArrivalTarget.isBlank()) {
            this.arrivalTargetUuid = null;
        } else {
            try {
                this.arrivalTargetUuid = UUID.fromString(encodedArrivalTarget);
            } catch (IllegalArgumentException ignored) {
                this.arrivalTargetUuid = null;
            }
        }
    }

    public void beginInteraction(ServerPlayer customer) {
        this.customerUuid = customer == null ? null : customer.getUUID();
        this.customerHoldTicks = CUSTOMER_HOLD_TICKS;
        this.despawnTicks = DEFAULT_DESPAWN_TICKS;
    }

    public void beginArrivalTracking(ServerPlayer player) {
        if (player == null) {
            this.arrivalTargetUuid = null;
            this.arrivalTrackTicks = 0;
            return;
        }
        this.arrivalTargetUuid = player.getUUID();
        this.arrivalTrackTicks = ARRIVAL_TRACK_TICKS;
    }

    public boolean hasCustomer() {
        return this.customerUuid != null;
    }

    public boolean isCustomer(ServerPlayer player) {
        return player != null && this.customerUuid != null && this.customerUuid.equals(player.getUUID());
    }

    public void clearCustomer() {
        this.customerUuid = null;
        this.customerHoldTicks = 0;
    }

    public ServerPlayer getCustomer() {
        if (this.customerUuid == null || !(this.level() instanceof ServerLevel serverWorld)) {
            return null;
        }
        return serverWorld.getServer().getPlayerList().getPlayer(this.customerUuid);
    }

    private boolean canKeepCustomer(ServerPlayer customer) {
        return customer != null
                && customer.isAlive()
                && !customer.isRemoved()
                && customer.level() == this.level()
                && customer.distanceToSqr(this) <= QuestMasterService.getMaxInteractDistanceSquared();
    }

    private void tickArrivalTracking() {
        if (this.arrivalTrackTicks <= 0) {
            return;
        }
        this.arrivalTrackTicks--;
        if (hasCustomer() || isDefending()) {
            if (this.arrivalTrackTicks <= 0) {
                clearArrivalTracking();
            }
            return;
        }

        ServerPlayer target = getArrivalTarget();
        if (!QuestMasterService.isEligibleSpawnTarget(target) || target.level() != this.level()) {
            clearArrivalTracking();
            return;
        }

        if (this.distanceToSqr(target) > ARRIVAL_STOP_DISTANCE_SQUARED) {
            this.getNavigation().moveTo(target, ARRIVAL_TRACK_SPEED);
        } else {
            this.getNavigation().stop();
        }
        this.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());

        if (this.arrivalTrackTicks <= 0) {
            clearArrivalTracking();
        }
    }

    private void tickDefenseState() {
        if (!isDefending()) {
            return;
        }

        ServerPlayer target = getAggressor();
        if (!canKeepDefenseTarget(target)) {
            calmDown();
            return;
        }

        this.setTarget(target);
        this.getNavigation().moveTo(target, DEFENSE_CHASE_SPEED);
        this.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        attemptDefenseAttack(target);
        if (this.defenseTicks > 0) {
            this.defenseTicks--;
        }
        if (this.defenseTicks <= 0) {
            calmDown();
        }
    }

    private void enterDefense(ServerLevel world, ServerPlayer attacker) {
        ServerPlayer customer = getCustomer();
        if (customer != null) {
            QuestMasterUiService.close(customer);
        }
        clearCustomer();
        refreshDefense(attacker);
        speak(attacker, RETALIATION_KEYS, ChatFormatting.RED);
        world.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.VINDICATOR_AMBIENT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7f, 0.9f);
    }

    private void refreshDefense(ServerPlayer attacker) {
        this.lastAggressorUuid = attacker.getUUID();
        this.defenseTicks = DEFENSE_DURATION_TICKS;
        this.setTarget(attacker);
        equipSword(true);
    }

    private void calmDown() {
        this.defenseTicks = 0;
        this.meleeCooldownTicks = 0;
        this.lastAggressorUuid = null;
        this.setTarget(null);
        this.getNavigation().stop();
        updateHeldItemState();
    }

    private boolean isDefending() {
        return this.defenseTicks > 0 || this.getMainHandItem().is(Items.IRON_SWORD);
    }

    private void equipSword(boolean enabled) {
        if (enabled) {
            setHeldItem(Items.IRON_SWORD);
        } else {
            updateHeldItemState();
        }
    }

    private ServerPlayer getAggressor() {
        if (this.lastAggressorUuid == null || !(this.level() instanceof ServerLevel world)) {
            return null;
        }
        return world.getServer().getPlayerList().getPlayer(this.lastAggressorUuid);
    }

    private boolean canKeepDefenseTarget(ServerPlayer target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && target.level() == this.level()
                && target.distanceToSqr(this) <= MAX_DEFENSE_DISTANCE_SQUARED;
    }

    private ServerPlayer getArrivalTarget() {
        if (this.arrivalTargetUuid == null || !(this.level() instanceof ServerLevel world)) {
            return null;
        }
        return world.getServer().getPlayerList().getPlayer(this.arrivalTargetUuid);
    }

    private void clearArrivalTracking() {
        this.arrivalTrackTicks = 0;
        this.arrivalTargetUuid = null;
        this.getNavigation().stop();
    }

    private ServerPlayer resolvePlayerAttacker(DamageSource source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private void attemptDefenseAttack(ServerPlayer target) {
        if (target == null || this.meleeCooldownTicks > 0 || !(this.level() instanceof ServerLevel world)) {
            return;
        }
        if (!isInDefenseStrikeRange(target)) {
            return;
        }
        this.getNavigation().stop();
        this.swing(InteractionHand.MAIN_HAND);
        net.minecraft.world.damagesource.DamageSource damageSource = this.damageSources().mobAttack(this);
        boolean hit = this.doHurtTarget(world, target);
        if (!hit) {
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            hit = target.hurtServer(world, damageSource, damage);
            if (!hit && !target.isCreative() && !target.isSpectator()) {
                hit = applyGuaranteedDefenseDamage(world, target, damageSource, damage);
            }
            if (hit) {
                target.knockback(0.35, this.getX() - target.getX(), this.getZ() - target.getZ());
            }
        }
        if (hit) {
            this.meleeCooldownTicks = MELEE_COOLDOWN_TICKS;
        }
    }

    private boolean isInDefenseStrikeRange(ServerPlayer target) {
        if (target == null) {
            return false;
        }
        if (this.getBoundingBox().inflate(0.45).intersects(target.getBoundingBox())) {
            return true;
        }
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        return (dx * dx) + (dz * dz) <= 4.0;
    }

    private boolean applyGuaranteedDefenseDamage(ServerLevel world, ServerPlayer target, net.minecraft.world.damagesource.DamageSource damageSource, float damage) {
        float previousHealth = target.getHealth();
        float nextHealth = Math.max(0.0f, previousHealth - damage);
        if (nextHealth >= previousHealth) {
            return false;
        }
        target.hurt(damageSource, damage);
        target.setHealth(nextHealth);
        if (nextHealth <= 0.0f) {
            target.kill(world);
        }
        return true;
    }

    private void speak(ServerPlayer player, String[] keys, ChatFormatting textColor) {
        if (player == null || keys == null || keys.length == 0 || this.speechCooldownTicks > 0) {
            return;
        }
        String key = keys[this.getRandom().nextInt(keys.length)];
        player.sendSystemMessage(
                this.getDisplayName().copy().withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable(key).withStyle(textColor)),
                false
        );
        this.speechCooldownTicks = SPEECH_COOLDOWN_TICKS;
    }

    private void updateHeldItemState() {
        if (this.defenseTicks > 0) {
            setHeldItem(Items.IRON_SWORD);
            return;
        }
        if (shouldCarryTorch()) {
            setHeldItem(Items.TORCH);
            return;
        }
        setHeldItem(null);
    }

    private boolean shouldCarryTorch() {
        return this.isAlive() && this.level() != null && this.level().isDarkOutside();
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
}
