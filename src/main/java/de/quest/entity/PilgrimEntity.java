package de.quest.entity;

import de.quest.pilgrim.PilgrimService;
import de.quest.quest.special.MerchantSealQuestService;
import de.quest.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PilgrimEntity extends PathAwareEntity {
    public static final int DEFAULT_DESPAWN_TICKS = 24000;
    public static final int DEFAULT_OFFER_COUNT = 5;
    private static final int DEFENSE_DURATION_TICKS = 240;
    private static final int MELEE_COOLDOWN_TICKS = 22;
    private static final int SPEECH_COOLDOWN_TICKS = 120;
    private static final float DEFENSE_TRIGGER_HEALTH_FRACTION = 0.40f;
    private static final double MAX_DEFENSE_DISTANCE_SQUARED = 324.0;
    private static final double DEFENSE_CHASE_SPEED = 1.0;
    private static final String[] WARNING_KEYS = new String[] {
            "message.village-quest.pilgrim.warning.1",
            "message.village-quest.pilgrim.warning.2",
            "message.village-quest.pilgrim.warning.3",
            "message.village-quest.pilgrim.warning.4"
    };
    private static final String[] RETALIATION_KEYS = new String[] {
            "message.village-quest.pilgrim.retaliation.1",
            "message.village-quest.pilgrim.retaliation.2",
            "message.village-quest.pilgrim.retaliation.3",
            "message.village-quest.pilgrim.retaliation.4"
    };

    private final List<String> offerIds = new ArrayList<>();
    private int despawnTicks = DEFAULT_DESPAWN_TICKS;
    private int defenseTicks;
    private int meleeCooldownTicks;
    private int speechCooldownTicks;
    private UUID customerUuid;
    private UUID lastAggressorUuid;

    public PilgrimEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, DEFENSE_CHASE_SPEED, false));
        this.goalSelector.add(2, new EscapeDangerGoal(this, 1.1));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 2.0)
                .add(EntityAttributes.FOLLOW_RANGE, 24.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.32);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getEntityWorld().isClient()) {
            return;
        }
        if (this.speechCooldownTicks > 0) {
            this.speechCooldownTicks--;
        }
        if (this.meleeCooldownTicks > 0) {
            this.meleeCooldownTicks--;
        }
        tickDefenseState();

        ServerPlayerEntity customer = getCustomer();
        if (customer != null) {
            if (!canKeepCustomer(customer)) {
                PilgrimService.closeTrade(customer);
                clearCustomer();
            } else {
                this.getNavigation().stop();
                this.getLookControl().lookAt(customer.getX(), customer.getEyeY(), customer.getZ());
            }
        }

        if (this.despawnTicks > 0) {
            this.despawnTicks--;
        }
        if (this.despawnTicks == 0) {
            PilgrimService.beginNaturalSpawnCooldown((ServerWorld) this.getEntityWorld());
            if (customer != null) {
                PilgrimService.closeTrade(customer);
                clearCustomer();
            }
            this.discard();
        }
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (hasCustomer()) {
            this.getNavigation().stop();
            this.setVelocity(0.0, this.getVelocity().y, 0.0);
            this.sidewaysSpeed = 0.0f;
            this.forwardSpeed = 0.0f;
            this.limbAnimator.updateLimbs(0.05f, 0.15f, 0.0f);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        boolean damaged = super.damage(world, source, amount);
        if (!damaged) {
            return false;
        }

        ServerPlayerEntity attacker = resolvePlayerAttacker(source);
        if (attacker == null || !this.isAlive()) {
            return damaged;
        }

        this.lastAggressorUuid = attacker.getUuid();
        if (isDefending()) {
            refreshDefense(attacker);
            return true;
        }

        if (this.getHealth() <= this.getMaxHealth() * DEFENSE_TRIGGER_HEALTH_FRACTION) {
            enterDefense(attacker);
        } else {
            speak(attacker, WARNING_KEYS, Formatting.YELLOW);
        }
        return true;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (isDefending()) {
            serverPlayer.sendMessage(Text.translatable("message.village-quest.pilgrim.defending").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }
        if (player.isSneaking() && ModItems.MERCHANT_SEAL != null) {
            ActionResult sealResult = MerchantSealQuestService.tryUseOnPilgrim(
                    (ServerWorld) serverPlayer.getEntityWorld(),
                    serverPlayer,
                    this,
                    serverPlayer.getStackInHand(hand)
            );
            if (sealResult != ActionResult.PASS) {
                return sealResult;
            }
        }
        if (hasCustomer() && !isCustomer(serverPlayer)) {
            serverPlayer.sendMessage(net.minecraft.text.Text.translatable("message.village-quest.pilgrim.busy").formatted(net.minecraft.util.Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        this.getLookControl().lookAt(serverPlayer.getX(), serverPlayer.getEyeY(), serverPlayer.getZ());
        PilgrimService.openTrade((ServerWorld) serverPlayer.getEntityWorld(), serverPlayer, this);
        return ActionResult.SUCCESS;
    }

    @Override
    protected void writeCustomData(WriteView data) {
        super.writeCustomData(data);
        data.putString("Offers", String.join(",", this.offerIds));
        data.putInt("DespawnTicks", this.despawnTicks);
    }

    @Override
    protected void readCustomData(ReadView data) {
        super.readCustomData(data);
        this.offerIds.clear();
        String encodedOffers = data.getString("Offers", "");
        if (!encodedOffers.isBlank()) {
            for (String offerId : encodedOffers.split(",")) {
                if (!offerId.isBlank()) {
                    this.offerIds.add(offerId);
                }
            }
        }
        this.despawnTicks = Math.max(0, data.getInt("DespawnTicks", DEFAULT_DESPAWN_TICKS));
        if (this.offerIds.isEmpty() && !this.getEntityWorld().isClient()) {
            this.offerIds.addAll(PilgrimService.rollOfferIds(this.getEntityWorld().random, DEFAULT_OFFER_COUNT));
        }
    }

    public List<String> getOfferIds() {
        return List.copyOf(this.offerIds);
    }

    public boolean hasOffer(String offerId) {
        return offerId != null && this.offerIds.contains(offerId);
    }

    public void setOfferIds(Collection<String> offerIds) {
        this.offerIds.clear();
        if (offerIds != null) {
            for (String offerId : offerIds) {
                if (offerId != null && !offerId.isBlank()) {
                    this.offerIds.add(offerId);
                }
            }
        }
    }

    public int getDespawnTicks() {
        return this.despawnTicks;
    }

    public void setDespawnTicks(int despawnTicks) {
        this.despawnTicks = Math.max(0, despawnTicks);
    }

    public boolean hasCustomer() {
        return this.customerUuid != null;
    }

    public void setCustomer(ServerPlayerEntity customer) {
        this.customerUuid = customer == null ? null : customer.getUuid();
    }

    public boolean isCustomer(ServerPlayerEntity player) {
        return player != null && this.customerUuid != null && this.customerUuid.equals(player.getUuid());
    }

    public void clearCustomer() {
        this.customerUuid = null;
    }

    public ServerPlayerEntity getCustomer() {
        if (this.customerUuid == null || !(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        return serverWorld.getServer().getPlayerManager().getPlayer(this.customerUuid);
    }

    private boolean canKeepCustomer(ServerPlayerEntity customer) {
        return customer != null
                && customer.isAlive()
                && !customer.isRemoved()
                && customer.getEntityWorld() == this.getEntityWorld()
                && customer.squaredDistanceTo(this) <= PilgrimService.getMaxInteractDistanceSquared();
    }

    private void tickDefenseState() {
        if (!isDefending()) {
            return;
        }

        ServerPlayerEntity target = getAggressor();
        if (!canKeepDefenseTarget(target)) {
            calmDown();
            return;
        }

        this.setTarget(target);
        this.getNavigation().startMovingTo(target, DEFENSE_CHASE_SPEED);
        this.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ());
        attemptDefenseAttack(target);
        if (this.defenseTicks > 0) {
            this.defenseTicks--;
        }
        if (this.defenseTicks <= 0) {
            calmDown();
        }
    }

    private void enterDefense(ServerPlayerEntity attacker) {
        ServerPlayerEntity customer = getCustomer();
        if (customer != null) {
            PilgrimService.closeTrade(customer);
        }
        clearCustomer();
        refreshDefense(attacker);
        speak(attacker, RETALIATION_KEYS, Formatting.RED);
        if (this.getEntityWorld() instanceof ServerWorld world) {
            world.playSound(null, this.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PILLAGER_AMBIENT, net.minecraft.sound.SoundCategory.NEUTRAL, 0.7f, 1.15f);
        }
    }

    private void refreshDefense(ServerPlayerEntity attacker) {
        this.lastAggressorUuid = attacker.getUuid();
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
        equipSword(false);
    }

    private boolean isDefending() {
        return this.defenseTicks > 0 || !this.getMainHandStack().isEmpty();
    }

    private void equipSword(boolean enabled) {
        this.setStackInHand(Hand.MAIN_HAND, enabled ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY);
    }

    private ServerPlayerEntity getAggressor() {
        if (this.lastAggressorUuid == null || !(this.getEntityWorld() instanceof ServerWorld world)) {
            return null;
        }
        return world.getServer().getPlayerManager().getPlayer(this.lastAggressorUuid);
    }

    private boolean canKeepDefenseTarget(ServerPlayerEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && target.getEntityWorld() == this.getEntityWorld()
                && target.squaredDistanceTo(this) <= MAX_DEFENSE_DISTANCE_SQUARED;
    }

    private ServerPlayerEntity resolvePlayerAttacker(DamageSource source) {
        return source.getAttacker() instanceof ServerPlayerEntity player ? player : null;
    }

    private void attemptDefenseAttack(ServerPlayerEntity target) {
        if (target == null || this.meleeCooldownTicks > 0 || !(this.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!isInDefenseStrikeRange(target)) {
            return;
        }
        this.getNavigation().stop();
        this.swingHand(Hand.MAIN_HAND);
        net.minecraft.entity.damage.DamageSource damageSource = this.getDamageSources().mobAttack(this);
        boolean hit = this.tryAttack(world, target);
        if (!hit) {
            float damage = (float) this.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
            hit = target.damage(world, damageSource, damage);
            if (!hit && !target.isCreative() && !target.isSpectator()) {
                hit = applyGuaranteedDefenseDamage(world, target, damageSource, damage);
            }
            if (hit) {
                target.takeKnockback(0.35, this.getX() - target.getX(), this.getZ() - target.getZ());
            }
        }
        if (hit) {
            this.meleeCooldownTicks = MELEE_COOLDOWN_TICKS;
        }
    }

    private boolean isInDefenseStrikeRange(ServerPlayerEntity target) {
        if (target == null) {
            return false;
        }
        if (this.getBoundingBox().expand(0.45).intersects(target.getBoundingBox())) {
            return true;
        }
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        return (dx * dx) + (dz * dz) <= 4.0;
    }

    private boolean applyGuaranteedDefenseDamage(ServerWorld world, ServerPlayerEntity target, net.minecraft.entity.damage.DamageSource damageSource, float damage) {
        float previousHealth = target.getHealth();
        float nextHealth = Math.max(0.0f, previousHealth - damage);
        if (nextHealth >= previousHealth) {
            return false;
        }
        target.serverDamage(damageSource, damage);
        target.setHealth(nextHealth);
        if (nextHealth <= 0.0f) {
            target.kill(world);
        }
        return true;
    }

    private void speak(ServerPlayerEntity player, String[] keys, Formatting textColor) {
        if (player == null || keys == null || keys.length == 0 || this.speechCooldownTicks > 0) {
            return;
        }
        String key = keys[this.getRandom().nextInt(keys.length)];
        player.sendMessage(
                this.getDisplayName().copy().formatted(Formatting.GOLD)
                        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
                        .append(Text.translatable(key).formatted(textColor)),
                false
        );
        this.speechCooldownTicks = SPEECH_COOLDOWN_TICKS;
    }
}
