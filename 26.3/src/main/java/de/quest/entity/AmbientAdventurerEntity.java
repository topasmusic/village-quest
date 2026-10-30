package de.quest.entity;

import de.quest.ambient.*;
import de.quest.caravan.NpcNames;
import de.quest.kingdom.NpcSpeechService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

/** Temporary traveler with one synchronized appearance and one finite encounter budget. */
public final class AmbientAdventurerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> PROFILE=SynchedEntityData.defineId(AmbientAdventurerEntity.class,EntityDataSerializers.INT);
    private AmbientAdventurerPolicy policy;
    private boolean initialized, wasMoving, wasRetreating;
    private long pauseUntil, retreatUntil, moveSince;
    private BlockPos danger, fightAnchor, progressPosition;
    private double direction;
    private List<Mob> threats=List.of();
    private final Map<UUID,BlockPos> fightAnchors=new HashMap<>();

    public AmbientAdventurerEntity(EntityType<? extends PathfinderMob> type,Level level) {
        super(type,level);
        setCanPickUpLoot(false);
        for(EquipmentSlot slot:EquipmentSlot.values()) setDropChance(slot,0);
        for(PathType path:List.of(PathType.WATER,PathType.LAVA,PathType.LEAVES,PathType.POWDER_SNOW,PathType.FIRE_IN_NEIGHBOR,PathType.FIRE,
                PathType.DAMAGING,PathType.DOOR_WOOD_CLOSED)) setPathfindingMalus(path,-1);
        policy=new AmbientAdventurerPolicy(false,4800+random.nextInt(4801)); direction=random.nextDouble()*Math.PI*2;
    }
    @Override protected PathNavigation createNavigation(Level level) { return new AmbientAdventurerNavigation(this,level); }
    public AmbientAdventurerNavigation travel() { return (AmbientAdventurerNavigation)getNavigation(); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new LookAtPlayerGoal(this,Player.class,6));
        goalSelector.addGoal(2,new RandomLookAroundGoal(this));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(PROFILE,0); }
    public int profileIndex() { return Math.clamp(entityData.get(PROFILE),0,5); }
    public boolean isNightrunner() { return profileIndex()>=3; }
    public NpcNames.AmbientProfile profile() { return NpcNames.ambientProfiles().get(profileIndex()); }
    public void initialize(boolean hunter,int appearance) {
        if(initialized) return; initialized=true;
        entityData.set(PROFILE,(hunter?3:0)+Math.clamp(appearance,0,2));
        policy=new AmbientAdventurerPolicy(hunter,4800+random.nextInt(4801));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hunter?24:20); setHealth(getMaxHealth());
        getAttribute(Attributes.ARMOR).setBaseValue(hunter?2:0);
        setCustomName(profile().displayName()); updateEquipment();
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.25)
                .add(Attributes.ATTACK_DAMAGE,4).add(Attributes.FOLLOW_RANGE,24).add(Attributes.ARMOR,0);
    }
    private void updateEquipment() {
        Item desired=isNightrunner()?Items.IRON_SWORD:AmbientAdventurerPolicy.night(level().getOverworldClockTime())?Items.TORCH:Items.AIR;
        if(getMainHandItem().getItem()==desired) return;
        setItemSlot(EquipmentSlot.MAINHAND,appearanceItem(isNightrunner(),AmbientAdventurerPolicy.night(level().getOverworldClockTime())));
    }
    public static ItemStack appearanceItem(boolean hunter,boolean night) {
        Item desired=hunter?Items.IRON_SWORD:night?Items.TORCH:Items.AIR;
        ItemStack stack=desired==Items.AIR?ItemStack.EMPTY:new ItemStack(desired);
        if(!stack.isEmpty()) stack.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
        return stack;
    }
    @Override public void tick() {
        super.tick();
        if(!(level() instanceof ServerLevel world) || !isAlive()) return;
        if(!initialized) initialize(false,random.nextInt(3));
        if(policy.tick(world.players().stream().anyMatch(p -> p.isAlive() && !p.isSpectator() && distanceToSqr(p)<=96*96))) { discard(); return; }
        long now=world.getGameTime();
        if((tickCount+getId())%40==0) { threats=AmbientAdventurerCombat.threats(world,this); updateEquipment(); }
        if(getHealth()<=8 && isNightrunner()) policy.flee(now);
        if(policy.defending()!=null) {
            travel().cancel();
            var player=world.getServer().getPlayerList().getPlayer(policy.defending());
            if(!policy.defenseActive(now) || player==null || player.level()!=world || !player.isAlive() || player.isCreative() || player.isSpectator()
                    || !world.isPvpAllowed() || getHealth()<=8) { policy.endDefense(now); retreatUntil=now+160; }
            else {
                getLookControl().setLookAt(player,30,30);
                if(hasLineOfSight(player) && policy.counter(player.getUUID(),now,isWithinMeleeAttackRange(player),true,true)) strike(world,player);
                return;
            }
        }
        if(policy.fighting()!=null) {
            var target=world.getEntity(policy.fighting());
            boolean valid=target instanceof Mob m && AmbientAdventurerCombat.eligible(m,false) && world.getDifficulty()!=Difficulty.PEACEFUL;
            boolean conflict=AmbientAdventurerService.conflict(world,blockPosition());
            int crowd=(int)threats.stream().filter(m -> AmbientAdventurerCombat.eligible(m,false)).count();
            if(!policy.continueFight(now,fightAnchor==null?Double.POSITIVE_INFINITY:distanceToSqr(fightAnchor.getX()+.5,fightAnchor.getY(),fightAnchor.getZ()+.5),
                    target!=null && loadedSight(world,target),valid && !conflict,getHealth(),crowd)
                    || target!=null && fightAnchor.distSqr(target.blockPosition())>144) {
                policy.abort(now); travel().cancel(); retreatUntil=now+160;
            } else if(target instanceof Mob mob) {
                getLookControl().setLookAt(mob,30,30);
                if(isWithinMeleeAttackRange(mob) && loadedSight(world,mob)) { travel().cancel(); if(policy.mobStrike(mob.getUUID(),now)) strike(world,mob); }
                else if((tickCount+getId())%20==0 && !travel().busy()) travel().request(mob.blockPosition(),1.1);
                // Flight/stuck checks below are shared with ordinary travel.
            }
        }
        if((tickCount+getId())%20!=0) return;
        boolean moving=travel().busy();
        if(moving && !wasMoving) { moveSince=now; progressPosition=blockPosition(); }
        if(moving && progressPosition!=null && !blockPosition().equals(progressPosition)) { moveSince=now; progressPosition=blockPosition(); policy.navigationProgress(); }
        if(travel().takeFailure() || moving && now-moveSince>=160) {
            travel().cancel();
            if(policy.fighting()!=null) { policy.abort(now); retreatUntil=now+160; }
            pauseUntil=now+policy.navigationFailed(); direction=random.nextDouble()*Math.PI*2;
        }
        if(wasMoving && !travel().busy()) pauseUntil=Math.max(pauseUntil,now+60+random.nextInt(101));
        wasMoving=travel().busy();
        if(AmbientAdventurerService.conflict(world,blockPosition()) || threats.stream().anyMatch(m -> !AmbientAdventurerCombat.ordinary(m.getType()))) {
            if(policy.fighting()!=null) policy.abort(now);
            retreatUntil=Math.max(retreatUntil,now+100);
        }
        if(!threats.isEmpty()) {
            Mob closest=threats.stream().filter(Entity::isAlive).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if(closest!=null) {
                danger=closest.blockPosition();
                if(!isNightrunner() || policy.fleeing() || threats.stream().filter(m -> AmbientAdventurerCombat.eligible(m,false)).count()>=3) {
                    if(isNightrunner()) policy.flee(now); retreatUntil=Math.max(retreatUntil,now+160);
                } else if(now>=retreatUntil && now>=pauseUntil && policy.canHunt(now) && AmbientAdventurerPolicy.night(world.getOverworldClockTime())
                        && world.getDifficulty()!=Difficulty.PEACEFUL && !AmbientAdventurerService.conflict(world,blockPosition())) {
                    Mob target=threats.stream().filter(m -> AmbientAdventurerCombat.eligible(m,true) && distanceToSqr(m)<=64 && loadedSight(world,m))
                            .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                    if(target!=null && policy.commit(target.getUUID(),now,false)) { fightAnchor=fightAnchors.computeIfAbsent(target.getUUID(),id -> blockPosition()); travel().cancel(); pauseUntil=0; }
                }
            }
        }
        boolean retreat=now<retreatUntil || policy.fleeing();
        if(retreat && !wasRetreating) { travel().cancel(); pauseUntil=0; }
        wasRetreating=retreat;
        if(policy.fighting()!=null || travel().busy() || now<pauseUntil) return;
        if(retreat && danger!=null) direction=policy.retreatHeading(Math.atan2(getZ()-danger.getZ(),getX()-danger.getX()));
        else direction+=(random.nextDouble()-.5)*.5;
        int length=policy.travelLength(random.nextInt(6));
        BlockPos destination=blockPosition().offset((int)Math.round(Math.cos(direction)*length),0,(int)Math.round(Math.sin(direction)*length));
        if(!travel().request(destination,retreat?1.25:.8)) pauseUntil=now+40;
    }
    private boolean loadedSight(ServerLevel world,Entity target) {
        return de.quest.kingdom.NativeNpcPathPolicy.loaded(blockPosition(),0,24,
                (x,z) -> world.hasChunkAt(new BlockPos(x*16,blockPosition().getY(),z*16)) && world.isPositionEntityTicking(new BlockPos(x*16,blockPosition().getY(),z*16))) && hasLineOfSight(target);
    }
    private void strike(ServerLevel world,LivingEntity target) { swing(InteractionHand.MAIN_HAND,SwingAnimation.DEFAULT); super.doHurtTarget(world,target); }
    /** No external vanilla target/goal can start an unbudgeted attack. */
    @Override public boolean doHurtTarget(ServerLevel world,Entity target) { return false; }
    @Override public boolean hurtServer(ServerLevel world,DamageSource source,float amount) {
        float health=getHealth(), absorption=getAbsorptionAmount();
        boolean accepted=super.hurtServer(world,source,amount);
        if(policy!=null && isAlive() && AmbientAdventurerPolicy.acceptedDamage(health,absorption,getHealth(),getAbsorptionAmount(),accepted)) {
            long now=world.getGameTime(); travel().cancel(); pauseUntil=0; retreatUntil=now+160;
            var player=AmbientAdventurerCombat.player(source);
            boolean mobDefense=player==null && isNightrunner() && source.getEntity() instanceof Mob mob && mob.getTarget()==this
                    && AmbientAdventurerCombat.eligible(mob,false) && world.getDifficulty()!=Difficulty.PEACEFUL
                    && distanceToSqr(mob)<=64 && loadedSight(world,mob) && !AmbientAdventurerService.conflict(world,blockPosition());
            var reaction=mobDefense?AmbientAdventurerPolicy.Assault.RETREAT:policy.assaulted(player==null?null:player.getUUID(),now,world.isPvpAllowed(),getHealth());
            if(source.getEntity()!=null) danger=source.getEntity().blockPosition();
            if(player!=null && reaction==AmbientAdventurerPolicy.Assault.WARN) AmbientAdventurerDialogue.warning(player,this);
            // Ordinary mob self-defense still spends the same commitment and strike quotas.
            if(mobDefense && source.getEntity() instanceof Mob mob) {
                if(mob.getUUID().equals(policy.fighting())) retreatUntil=0;
                else { policy.abort(now); if(policy.commit(mob.getUUID(),now,true)) { fightAnchor=fightAnchors.computeIfAbsent(mob.getUUID(),id -> blockPosition()); retreatUntil=0; } }
            }
        }
        return accepted;
    }
    public boolean encounterCalm(Player player) {
        long now=level().getGameTime();
        return isAlive() && !policy.refuses(player.getUUID()) && policy.fighting()==null && policy.defending()==null
                && now>=retreatUntil && !policy.fleeing() && threats.stream().noneMatch(Entity::isAlive);
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if(player instanceof ServerPlayer serverPlayer && distanceToSqr(player)<=16 && !player.isSpectator()) {
            long now=level().getGameTime();
            if(hasLineOfSight(player)) {
                boolean danger=policy.fighting()!=null || policy.defending()!=null || now<retreatUntil;
                boolean spoken=encounterCalm(player) && EncounterQuestService.interact(serverPlayer,this)
                        || AmbientAdventurerDialogue.greet(serverPlayer,this,policy.refuses(player.getUUID()),danger);
                if(spoken && !policy.refuses(player.getUUID()) && !danger) {
                    travel().cancel(); pauseUntil=now+60+random.nextInt(41); getLookControl().setLookAt(player,30,30);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void dropAllDeathLoot(ServerLevel world,DamageSource source) { /* no inventory/equipment/XP */ }
    @Override public boolean canPickUpLoot() { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean startRiding(Entity entity,boolean force,boolean sendEvent) { return false; }
    @Override protected boolean canAddPassenger(Entity entity) { return false; }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
