package de.quest.kingdom;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.behavior.WakeUp;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;

/** Uses the actual villager rest behaviors on our own NPC Brain, after loaded-bed ownership checks. */
public final class NativeNpcRest {
    private final SleepInBed sleep=new SleepInBed();
    private final BehaviorControl<LivingEntity> wake=WakeUp.create();

    public static Brain<LivingEntity> makeBrain(Brain.Packed saved) { return new RestBrain(saved); }
    private static final class RestBrain extends Brain<LivingEntity> {
        private RestBrain(Brain.Packed saved) {
            super(List.of(MemoryModuleType.HOME,MemoryModuleType.LAST_WOKEN,MemoryModuleType.LAST_SLEPT,
                    MemoryModuleType.WALK_TARGET,MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    List.of(),List.of(),saved.memories(),RandomSource.create(0));
        }
    }

    public void attach(Brain<?> brain) {
        // addActivity registers the native behavior's required memories without replacing saved values.
        brain.addActivity(Activity.REST,ImmutableList.of(Pair.of(0,sleep)),Set.of(),Set.of());
        brain.addActivity(Activity.IDLE,ImmutableList.of(),Set.of(),Set.of());
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
    }
    void prepare(Brain<?> brain,ResourceKey<Level> dimension,BlockPos head) {
        brain.setMemory(MemoryModuleType.HOME,GlobalPos.of(dimension,head));
        brain.setActiveActivityIfPossible(Activity.REST);
    }
    void prepareWake(Brain<?> brain) { brain.setActiveActivityIfPossible(Activity.IDLE); }
    public boolean sleep(ServerLevel world,LivingEntity npc,BlockPos head) {
        prepare(npc.getBrain(),world.dimension(),head);
        long now=world.getGameTime();
        if(sleep.getStatus()==Behavior.Status.RUNNING) sleep.tickOrStop(world,npc,now);
        else sleep.tryStart(world,npc,now);
        return npc.isSleeping() && npc.getSleepingPos().filter(head::equals).isPresent();
    }
    public void wake(ServerLevel world,LivingEntity npc) {
        prepareWake(npc.getBrain());
        if(sleep.getStatus()==Behavior.Status.RUNNING) sleep.doStop(world,npc,world.getGameTime());
        // Also handles a saved sleeping pose whose runtime behavior was recreated after loading.
        wake.tryStart(world,npc,world.getGameTime());
    }
}
