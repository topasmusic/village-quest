package de.quest.entity;

import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterService;
import de.quest.questmaster.QuestMasterUiService;
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

import java.util.UUID;

public final class QuestMasterEntity extends PathAwareEntity {
    public static final int DEFAULT_DESPAWN_TICKS = 600;
    private static final int CUSTOMER_HOLD_TICKS = 60;

    private int despawnTicks = DEFAULT_DESPAWN_TICKS;
    private int customerHoldTicks;
    private UUID customerUuid;

    public QuestMasterEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
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
        boolean uiSessionOpen = customer != null && QuestMasterUiService.isOpen(customer.getUuid());
        if (customer != null) {
            if (!canKeepCustomer(customer)) {
                QuestMasterUiService.close(customer);
                clearCustomer();
                uiSessionOpen = false;
            } else {
                this.getNavigation().stop();
                this.getLookControl().lookAt(customer.getX(), customer.getEyeY(), customer.getZ());
                if (uiSessionOpen
                        || DailyQuestService.hasPendingQuestMasterOffer((ServerWorld) this.getEntityWorld(), customer.getUuid())) {
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
            this.despawnTicks = Math.max(this.despawnTicks, 40);
            return;
        }

        if (this.despawnTicks > 0) {
            this.despawnTicks--;
        }
        if (this.despawnTicks == 0) {
            if (customer != null) {
                QuestMasterUiService.close(customer);
            }
            clearCustomer();
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
        if (hasCustomer() && !isCustomer(serverPlayer)) {
            serverPlayer.sendMessage(net.minecraft.text.Text.translatable("message.village-quest.questmaster.busy").formatted(net.minecraft.util.Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        QuestMasterService.interact((ServerWorld) serverPlayer.getEntityWorld(), serverPlayer, this);
        return ActionResult.SUCCESS;
    }

    @Override
    protected void writeCustomData(WriteView data) {
        super.writeCustomData(data);
        data.putInt("DespawnTicks", this.despawnTicks);
        data.putInt("CustomerHoldTicks", this.customerHoldTicks);
    }

    @Override
    protected void readCustomData(ReadView data) {
        super.readCustomData(data);
        this.despawnTicks = Math.max(0, data.getInt("DespawnTicks", DEFAULT_DESPAWN_TICKS));
        this.customerHoldTicks = Math.max(0, data.getInt("CustomerHoldTicks", 0));
        this.customerUuid = null;
    }

    public void beginInteraction(ServerPlayerEntity customer) {
        this.customerUuid = customer == null ? null : customer.getUuid();
        this.customerHoldTicks = CUSTOMER_HOLD_TICKS;
    }

    public boolean hasCustomer() {
        return this.customerUuid != null;
    }

    public boolean isCustomer(ServerPlayerEntity player) {
        return player != null && this.customerUuid != null && this.customerUuid.equals(player.getUuid());
    }

    public void clearCustomer() {
        this.customerUuid = null;
        this.customerHoldTicks = 0;
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
                && customer.squaredDistanceTo(this) <= QuestMasterService.getMaxInteractDistanceSquared();
    }
}
