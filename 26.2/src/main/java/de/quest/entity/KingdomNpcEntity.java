package de.quest.entity;

import de.quest.kingdom.KingdomNpcIdentity;
import de.quest.kingdom.KingdomNpcService;
import de.quest.kingdom.KingdomPersonRegistry;
import de.quest.kingdom.NativeNpcRest;
import de.quest.kingdom.KingdomNpcNavigation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A disposable physical generation of a persistent canonical person; no Vanilla villager Brain is changed. */
public final class KingdomNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN=SynchedEntityData.defineId(KingdomNpcEntity.class,EntityDataSerializers.INT);
    private KingdomNpcIdentity identity=KingdomNpcIdentity.fromNbt(null);
    private final NativeNpcRest rest=new NativeNpcRest();
    private long lastActivity=-1,lastBedAttempt=-1,lastWarning=-1,lastPatrol=-1;
    private long lastSmallTalk=-1;
    private Component lastAutomaticName;
    private int previewSkin=-1;
    private String previewOwner="";
    public String previewOwner() { return previewOwner; }
    public void previewOwner(java.util.UUID owner) { previewOwner=owner.toString(); }
    @Override public boolean hurtServer(ServerLevel world,DamageSource source,float amount) { return !isPreview() && super.hurtServer(world,source,amount); }
    public boolean isPreview() { return previewSkin>=0; }
    public void preview(int skin,Component name) {
        if(skin<0 || skin>=de.quest.kingdom.NpcSkinCatalog.FILES.size()) throw new IllegalArgumentException("Unknown skin");
        previewSkin=skin; entityData.set(SKIN,skin); setCustomName(name); setCustomNameVisible(true); setNoAi(true); setNoGravity(true);
    }
    public boolean beginSmallTalk(long now) {
        if(lastSmallTalk>=0 && now>=lastSmallTalk && now-lastSmallTalk<6000) return false;
        lastSmallTalk=now; return true;
    }
    public boolean beginWarning(long now) {
        if(lastWarning>=0 && now>=lastWarning && now-lastWarning<400) return false;
        lastWarning=now; return true;
    }
    public boolean beginPatrol(long now) {
        if(lastPatrol>=0 && now>=lastPatrol && now-lastPatrol<160) return false;
        lastPatrol=now; return true;
    }
    public boolean beginActivity(long now) {
        if(lastActivity>=0 && now>=lastActivity && now-lastActivity<20) return false;
        lastActivity=now; return true;
    }
    public boolean beginBedAttempt(long now) {
        if(lastBedAttempt>=0 && now>=lastBedAttempt && now-lastBedAttempt<100) return false;
        lastBedAttempt=now; return true;
    }
    public KingdomNpcEntity(EntityType<? extends PathfinderMob> type,Level level) {
        super(type,level); setPersistenceRequired();
        rest.attach(getBrain());
        setCanPickUpLoot(false); setPathfindingMalus(PathType.LEAVES,-1); setPathfindingMalus(PathType.POWDER_SNOW,-1);
        if (getNavigation() instanceof GroundPathNavigation ground) ground.setCanOpenDoors(true);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this)); goalSelector.addGoal(1,new OpenDoorGoal(this,true));
        goalSelector.addGoal(4,new LookAtPlayerGoal(this,Player.class,8)); goalSelector.addGoal(5,new RandomLookAroundGoal(this));
    }
    @Override protected PathNavigation createNavigation(Level level) { return new KingdomNpcNavigation(this,level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(SKIN,0); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.MOVEMENT_SPEED,0.25).add(Attributes.FOLLOW_RANGE,16);
    }
    public KingdomNpcIdentity identity() { return identity; }
    public NativeNpcRest rest() { return rest; }
    @Override protected Brain<? extends LivingEntity> makeBrain(Brain.Packed saved) { return NativeNpcRest.makeBrain(saved); }
    @Override public void stopSleeping() {
        boolean sleeping=isSleeping();
        super.stopSleeping();
        if(sleeping && level() instanceof ServerLevel world) getBrain().setMemory(MemoryModuleType.LAST_WOKEN,world.getGameTime());
    }
    public void bind(KingdomPersonRegistry.Ticket ticket,KingdomPersonRegistry.View person) {
        if (!ticket.body().equals(getUUID()) || identity.ticket(getUUID()).isPresent()) throw new IllegalStateException("Body already bound");
        identity=KingdomNpcIdentity.bound(ticket); refreshAppearance(person);
    }
    public int skinIndex() { return Math.max(0,Math.min(de.quest.kingdom.NpcSkinCatalog.FILES.size()-1,entityData.get(SKIN))); }
    public void refreshAppearance(KingdomPersonRegistry.View person) {
        int skin=switch(person.role()) {
            case LEADER -> (person.mayor()?2:0)+(person.female()?1:0);
            case INNKEEPER -> 4+(person.female()?1:0);
            case STOREKEEPER -> 6+(person.female()?1:0);
            case MARKET_TRADER -> 8+(person.female()?3:0)+person.variant();
        };
        entityData.set(SKIN,person.guest()>=0?14+person.guest():skin);
        if(getCustomName()==null || getCustomName().equals(lastAutomaticName)) {
            lastAutomaticName=person.displayName(); setCustomName(lastAutomaticName);
        }
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if (isPreview()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer actor && level() instanceof ServerLevel world) KingdomNpcService.interact(world,actor,this);
        return InteractionResult.SUCCESS;
    }
    @Override public void die(DamageSource source) {
        if (level() instanceof ServerLevel world) KingdomNpcService.died(world,this);
        super.die(source);
    }
    @Override protected void dropAllDeathLoot(ServerLevel world,DamageSource source) { }
    @Override public boolean shouldDropExperience() { return false; }
    @Override public boolean isPushable() { return !isPreview() && skinIndex()<14 && super.isPushable(); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if(previewSkin>=0) { output.putInt("LivingKingdomsPreviewSkin",previewSkin); output.putString("LivingKingdomsPreviewOwner",previewOwner); }
        if(lastAutomaticName!=null) output.store("LivingKingdomsAutomaticName",net.minecraft.network.chat.ComponentSerialization.CODEC,lastAutomaticName);
        output.store("LivingKingdomsPerson",KingdomNpcIdentity.PAYLOAD_CODEC,identity.payload());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); identity=KingdomNpcIdentity.fromNbt(input.read("LivingKingdomsPerson",KingdomNpcIdentity.PAYLOAD_CODEC).orElse(null));
        previewOwner=input.getStringOr("LivingKingdomsPreviewOwner","");
        int savedPreview=input.getIntOr("LivingKingdomsPreviewSkin",-1);
        if(savedPreview>=0 && savedPreview<de.quest.kingdom.NpcSkinCatalog.FILES.size()) preview(savedPreview,getCustomName()==null?Component.literal("NPC"):getCustomName());
        lastAutomaticName=input.read("LivingKingdomsAutomaticName",net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
        rest.attach(getBrain());
    }
}
