package de.quest.ambient;

import de.quest.entity.AmbientAdventurerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.entity.EntityTypeTest;
import java.util.*;

/** Positive native allowlist and actual causal ownership; never substitutes a pet's owner. */
public final class AmbientAdventurerCombat {
    private AmbientAdventurerCombat() {}
    public static boolean ordinary(EntityType<?> type) {
        return type==EntityTypes.ZOMBIE || type==EntityTypes.HUSK || type==EntityTypes.SKELETON
                || type==EntityTypes.STRAY || type==EntityTypes.BOGGED || type==EntityTypes.SPIDER;
    }
    public static boolean protectedEntity(Entity entity) {
        return entity.entityTags().stream().anyMatch(t -> t.startsWith("vq_") || t.startsWith("village-quest"));
    }
    public static boolean eligible(Mob mob,boolean voluntary) {
        if(mob==null || !mob.isAlive() || !ordinary(mob.getType()) || mob.hasCustomName() || mob.isPersistenceRequired()
                || mob.isPassenger() || mob.isVehicle() || protectedEntity(mob)) return false;
        if(mob.getType()==EntityTypes.SPIDER && mob.getTarget()==null) return false;
        return !voluntary || !(mob.getTarget() instanceof Player) && mob.getLastHurtByPlayerMemoryTime()<=0
                && !(mob.getLastHurtByMob() instanceof TamableAnimal && mob.tickCount-mob.getLastHurtByMobTimestamp()<200);
    }
    public static List<Mob> threats(ServerLevel world,LivingEntity npc) {
        var mobs=new ArrayList<Mob>();
        if(!de.quest.kingdom.NativeNpcPathPolicy.loaded(npc.blockPosition(),0,8,
                (x,z) -> world.hasChunkAt(new net.minecraft.core.BlockPos(x*16,npc.blockPosition().getY(),z*16))
                        && world.isPositionEntityTicking(new net.minecraft.core.BlockPos(x*16,npc.blockPosition().getY(),z*16)))) return mobs;
        world.getEntities(EntityTypeTest.forClass(Mob.class),npc.getBoundingBox().inflate(8),
                m -> perceivedThreat(npc,m),mobs,8);
        return mobs;
    }
    public static boolean perceivedThreat(LivingEntity npc,Mob mob) {
        return mob!=npc && mob instanceof Monster && mob.isAlive() && !mob.isRemoved() && !mob.isNoAi()
                && (mob.getTarget()!=null || !(mob instanceof NeutralMob || mob instanceof Spider || mob instanceof AbstractPiglin))
                && npc.distanceToSqr(mob)<=64 && npc.hasLineOfSight(mob);
    }
    public static ServerPlayer player(DamageSource source) { return owner(source.getEntity()!=null?source.getEntity():source.getDirectEntity(),0); }
    private static ServerPlayer owner(Entity entity,int depth) {
        if(depth>4 || entity instanceof TamableAnimal) return null;
        if(entity instanceof ServerPlayer p) return p;
        if(entity instanceof Projectile p) return owner(p.getOwner(),depth+1);
        if(entity instanceof net.minecraft.world.entity.item.PrimedTnt t) return owner(t.getOwner(),depth+1);
        return null;
    }
    public static boolean suppressLoot(LivingEntity victim,DamageSource source) {
        return victim instanceof Mob && ordinary(victim.getType()) && source.getEntity() instanceof AmbientAdventurerEntity npc
                && npc.isNightrunner() && source.getDirectEntity()==npc;
    }
}
