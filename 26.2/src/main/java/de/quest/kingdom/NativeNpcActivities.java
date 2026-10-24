package de.quest.kingdom;

import de.quest.entity.KingdomNpcEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Loaded local reactions only: flee, warn and work. Never attacks, rings a bell or creates enemies. */
final class NativeNpcActivities {
    private NativeNpcActivities() {}
    static boolean hostile(Monster enemy) {
        return enemy.isAlive() && !enemy.isRemoved() && !enemy.isNoAi()
                && (enemy.getTarget()!=null || !(enemy instanceof NeutralMob || enemy instanceof Spider || enemy instanceof AbstractPiglin));
    }
    static boolean tick(ServerLevel world,KingdomNpcEntity npc,KingdomPersonRegistry.View person,
                        KingdomAnchorRegistry.AnchorView anchor,BuildingScanner.Report report,long now) {
        boolean torch=StaffRoutinePolicy.torch(person.role(),world.getOverworldClockTime());
        if(torch && !npc.getMainHandItem().is(Items.TORCH)) npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.TORCH));
        else if(!torch && npc.getMainHandItem().is(Items.TORCH)) npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        var navigation=(KingdomNpcNavigation)npc.getNavigation();
        if(report==null) return false;
        if(world.getDifficulty()!=Difficulty.PEACEFUL && NativeNpcPathPolicy.loaded(npc.blockPosition(),0,8,
                (x,z) -> world.hasChunkAt(new BlockPos(x*16,npc.blockPosition().getY(),z*16)))) {
            var nearby=new ArrayList<Monster>();
            world.getEntities(EntityTypeTest.forClass(Monster.class),new AABB(npc.blockPosition()).inflate(8),NativeNpcActivities::hostile,nearby,16);
            var threat=nearby.stream().filter(enemy -> npc.getSensing().hasLineOfSight(enemy))
                    .min(java.util.Comparator.comparingDouble(npc::distanceToSqr));
            if(threat.isPresent()) {
                var state=LivingKingdomsState.get(world.getServer());
                state.people().beds().lease(person.id()).ifPresent(lease -> NativeInnBeds.wake(world,npc,lease));
                var floor=new HashSet<>(report.walkable()); var covered=new HashSet<BlockPos>();
                if(anchor.role()==BuildingScanner.Role.INN || anchor.role()==BuildingScanner.Role.COUNCIL) covered.addAll(report.walkable());
                for(var role:java.util.List.of(BuildingScanner.Role.INN,BuildingScanner.Role.COUNCIL)) {
                    var safe=state.anchors().official(person.visit()==null?person.village():person.visit(),role);
                    if(safe.isEmpty() || !state.anchors().maySpawn(safe.get().id(),now)
                            || safe.get().position().distSqr(npc.blockPosition())>24*24
                            || !NativeNpcPathPolicy.loaded(safe.get().position(),0,safe.get().range().horizontal,
                                (x,z) -> world.hasChunkAt(new BlockPos(x*16,safe.get().position().getY(),z*16)))) continue;
                    state.anchors().report(safe.get().id()).ifPresent(room -> { floor.addAll(room.walkable()); covered.addAll(room.walkable()); });
                }
                navigation.certifyFloors(floor,now);
                var destination=StaffRoutinePolicy.shelter(covered,floor,npc.blockPosition(),threat.get().blockPosition());
                if(destination.isPresent() && !npc.isSleeping()) {
                    var target=destination.get(); var current=navigation.getPath();
                    if(navigation.isDone() || current==null || !current.getTarget().equals(target))
                        navigation.moveTo(target.getX()+0.5,target.getY(),target.getZ()+0.5,1.25);
                } else navigation.stop();
                if(npc.beginWarning(now)) say(world,npc,Component.translatable("message.village-quest.kingdom.warning.hostile",person.name()));
                return true;
            }
        }
        if(person.role()==KingdomPersonRegistry.Role.LEADER && person.visit()==null && !npc.isSleeping()) {
            var state=LivingKingdomsState.get(world.getServer());
            state.anchors().official(person.village(),BuildingScanner.Role.INN).filter(a -> state.anchors().maySpawn(a.id(),now)).ifPresent(a ->
                state.people().ensure(person.village(),KingdomPersonRegistry.Role.INNKEEPER,0).filter(p -> p.body()!=null).ifPresent(p -> {
                    if(world.getEntity(p.body()) instanceof KingdomNpcEntity partner && KingdomNpcService.available(world,partner).isPresent()
                            && !partner.isSleeping() && npc.distanceToSqr(partner)<=36 && npc.hasLineOfSight(partner) && npc.beginSmallTalk(now)) {
                        int variant=person.personality()%2;
                        NpcSpeechService.exchange(world,npc,partner,Component.translatable("message.village-quest.kingdom.smalltalk.question."+variant),
                                Component.translatable("message.village-quest.kingdom.smalltalk.answer."+variant));
                    }
                }));
        }
        if(person.role()==KingdomPersonRegistry.Role.STOREKEEPER && !npc.isSleeping() && navigation.isDone() && npc.beginPatrol(now))
            StaffRoutinePolicy.patrol(report.walkable(),anchor.position(),person.appearanceSeed(),now)
                    .filter(target -> !target.equals(npc.blockPosition()))
                    .ifPresent(target -> navigation.moveTo(target.getX()+0.5,target.getY(),target.getZ()+0.5,0.6));
        boolean attemptedWork=false,movingToWork=false;
        if(person.role()!=KingdomPersonRegistry.Role.STOREKEEPER && person.role()!=KingdomPersonRegistry.Role.INNKEEPER
                && !npc.isSleeping() && navigation.isDone()) {
            var work=anchor.position().relative(anchor.facing());
            if(person.role()==KingdomPersonRegistry.Role.MARKET_TRADER) {
                var place=LivingKingdomsState.get(world.getServer()).marketDays().appearancePlace(anchor.villageId(),person.id(),now);
                if(place.isEmpty()) return false; work=place.get().position();
            }
            if(report.walkable().contains(work) && npc.blockPosition().distSqr(work)>4) {
                attemptedWork=true;
                movingToWork=navigation.moveTo(work.getX()+0.5,work.getY(),work.getZ()+0.5,0.65);
            }
        }
        if(NativeNpcPathPolicy.workplaceWarning(attemptedWork,movingToWork,navigation.failure()) && npc.beginWarning(now))
            say(world,npc,Component.translatable("message.village-quest.kingdom.navigation.wait",person.name()));
        return false;
    }
    static void say(ServerLevel world,KingdomNpcEntity npc,Component message) {
        NpcSpeechService.nearby(world,npc,message);
    }
}
