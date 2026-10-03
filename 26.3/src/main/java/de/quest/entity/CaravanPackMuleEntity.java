package de.quest.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

/** A route controlled visual mule, with no wandering, riding, chest, or breeding interaction. */
public final class CaravanPackMuleEntity extends Mule {
    public CaravanPackMuleEntity(EntityType<? extends Mule> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setPermanentlyInvulnerable(!de.quest.caravan.CaravanCrewLifecycle.mortalityEnabled());
        setPathfindingMalus(PathType.LEAVES, -1.0f);
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0f);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.CONSUME;
    }
}
