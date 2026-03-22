package de.quest.entity;

import de.quest.pilgrim.PilgrimService;
import de.quest.quest.special.MerchantSealQuestService;
import de.quest.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PilgrimEntity extends PathAwareEntity {
    public static final int DEFAULT_DESPAWN_TICKS = 24000;
    public static final int DEFAULT_OFFER_COUNT = 5;

    private final List<String> offerIds = new ArrayList<>();
    private int despawnTicks = DEFAULT_DESPAWN_TICKS;
    private UUID customerUuid;

    public PilgrimEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.1));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.32);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getEntityWorld().isClient()) {
            return;
        }

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
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
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
}
