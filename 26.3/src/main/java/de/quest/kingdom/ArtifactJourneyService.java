package de.quest.kingdom;

import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.caravan.KingdomRouteConnections;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Trusted success adapters only. No rewards, terrain construction, chunk tickets or new freight settlement. */
public final class ArtifactJourneyService {
    public static net.minecraft.network.chat.Component shortObjective(PlayerQuestData data) {
        String prefix="quest.village-quest.tracker.artifact.";
        if(ArtifactJourneyProgress.target(data)==null)
            return net.minecraft.network.chat.Component.translatable(prefix+"target");
        int chapter=data.getStoryChapterProgress(ArtifactJourneyProgress.ARC);
        for(var deed:ArtifactJourneyProgress.required(chapter)) if(!ArtifactJourneyProgress.has(data,deed))
            return net.minecraft.network.chat.Component.translatable(prefix+deed.name().toLowerCase(java.util.Locale.ROOT));
        return net.minecraft.network.chat.Component.translatable(prefix+(chapter==4 && ArtifactJourneyProgress.choice(data)==0?"choice":"claim"));
    }
    private static final String TEXT="quest.village-quest.story.artifact_journey.";
    private record Pending(MinecraftServer server,String dimension,UUID epoch,UUID entity,BlockPos position,long tick) {}
    private static final Map<UUID,Pending> FLOCKS=new HashMap<>();
    private ArtifactJourneyService() {}
    private static PlayerQuestData data(ServerLevel world,UUID player) { return QuestState.get(world.getServer()).getPlayerData(player); }
    public static Component dialogue(ServerLevel world,ServerPlayer player,KingdomPersonRegistry.View person) {
        var data=data(world,player.getUUID());
        if(!person.village().equals(ArtifactJourneyProgress.target(data)) || person.role()!=KingdomPersonRegistry.Role.LEADER
                && person.role()!=KingdomPersonRegistry.Role.STOREKEEPER) return null;
        String role=person.role()==KingdomPersonRegistry.Role.LEADER?"leader":"storekeeper";
        if(data.hasMilestoneFlag(ArtifactJourneyProgress.PREFIX+"finale"))
            return Component.translatable(TEXT+"dialogue.final."+(ArtifactJourneyProgress.choice(data)==1?"reserve":"road")+"."+role);
        return ArtifactJourneyProgress.active(data)?Component.translatable(TEXT+"dialogue."+data.getStoryChapterProgress(ArtifactJourneyProgress.ARC)+"."+role):null;
    }
    public static boolean unlocked(ServerLevel world,UUID id) {
        var player=world.getServer().getPlayerList().getPlayer(id); if(player==null) return false;
        var earned=EnumSet.noneOf(ArchiveItem.class);
        for(var type:ArchiveItem.values()) if(GuildArchiveService.eligible(world,player,type)) earned.add(type);
        return ArtifactJourneyProgress.unlocked(data(world,id),earned);
    }
    public static Optional<LivingKingdomsState.VillageView> target(ServerLevel world,PlayerQuestData data) {
        var id=ArtifactJourneyProgress.target(data);
        return LivingKingdomsState.get(world.getServer()).village(id==null?KingdomStoryProgress.target(data):id);
    }
    public static boolean local(ServerLevel world,PlayerQuestData data,BlockPos pos) {
        return target(world,data).filter(v -> v.legacyKey().dimension().equals(world.dimension().identifier().toString())
                && v.arrival().distSqr(pos)<=96L*96).isPresent();
    }
    public static void accepted(ServerLevel world,ServerPlayer player) {
        var data=data(world,player.getUUID());
        target(world,data).ifPresent(v -> ArtifactJourneyProgress.begin(data,v.id()));
        FLOCKS.remove(player.getUUID()); QuestState.get(world.getServer()).setDirty();
    }
    /** Explicit target changes and charter choices require a current local authorized council. */
    public static boolean council(ServerPlayer player,String action) {
        if(!(player.level() instanceof ServerLevel world) || !world.getServer().isSameThread() || !player.isAlive() || player.isSpectator()) return false;
        var state=LivingKingdomsState.get(world.getServer()); var data=data(world,player.getUUID());
        var candidates=state.anchors().near(world.dimension().identifier().toString(),player.blockPosition(),8,8).stream()
                .filter(a -> a.role()==BuildingScanner.Role.COUNCIL && state.anchors().maySpawn(a.id(),world.getGameTime())
                    && state.canManageVillage(player.getUUID(),a.villageId())).toList();
        boolean changed=false;
        if(candidates.size()==1) {
            var village=candidates.getFirst().villageId();
            if(action.equals("target") && (ArtifactJourneyProgress.active(data) || data.getActiveStoryArc()==null && unlocked(world,player.getUUID()))) {
                changed=ArtifactJourneyProgress.select(data,village); FLOCKS.remove(player.getUUID());
            } else if(village.equals(ArtifactJourneyProgress.target(data))) {
                int choice=action.equals("reserve")?1:action.equals("road")?2:0;
                changed=ArtifactJourneyProgress.choose(data,choice);
                if(choice>0 && data.hasMilestoneFlag(ArtifactJourneyProgress.PREFIX+"finale")) {
                    data.setTradeRouteInt(ArtifactJourneyProgress.PREFIX+"choice",choice); changed=true;
                }
            }
        }
        if(changed) {
            QuestState.get(world.getServer()).setDirty();
            player.sendSystemMessage(Component.translatable(TEXT+(action.equals("target")?"target_changed":"choice_"+action)),false);
            StoryQuestService.completeIfEligible(world,player);
        } else player.sendSystemMessage(Component.translatable(TEXT+"council_required"),false);
        return changed;
    }
    private static ArchiveItem tool(ArtifactJourneyProgress.Deed deed) {
        return switch(deed) {
            case NAVIGATE -> ArchiveItem.SURVEYORS_COMPASS; case LEDGER -> ArchiveItem.CARAVAN_LEDGER;
            case SIGIL -> ArchiveItem.WAYFARERS_SIGIL; case FLOCK -> ArchiveItem.SHEPHERD_FLUTE;
            case HIVE -> ArchiveItem.APIARISTS_SMOKER; case REACH -> ArchiveItem.STARREACH_RING;
            case CHECK -> ArchiveItem.VILLAGE_CHECK; case SEAL -> ArchiveItem.MERCHANT_SEAL;
            case SATCHEL -> ArchiveItem.GUILD_COURIERS_SATCHEL; case HORN -> ArchiveItem.ROADWARDEN_HORN;
            default -> null;
        };
    }
    public static boolean confirmed(ServerLevel world,ServerPlayer player,ArtifactJourneyProgress.Deed deed,BlockPos effect) {
        if(world==null || player==null || !world.getServer().isSameThread() || !player.isAlive() || player.isSpectator()) return false;
        var data=data(world,player.getUUID()); var item=tool(deed);
        if(!ArtifactJourneyProgress.active(data) || item==null || !GuildArchiveService.eligible(world,player,item)
                || !GuildArchiveService.hasValidInInventory(world,player,item)
                || deed!=ArtifactJourneyProgress.Deed.SEAL && deed!=ArtifactJourneyProgress.Deed.SATCHEL
                    && (effect==null || !local(world,data,effect))) return false;
        return save(world,player,data,deed,ArtifactJourneyProgress.epoch(data),UUID.randomUUID().toString());
    }
    private static boolean save(ServerLevel world,ServerPlayer player,PlayerQuestData data,ArtifactJourneyProgress.Deed deed,UUID epoch,String receipt) {
        boolean changed=ArtifactJourneyProgress.record(data,ArtifactJourneyProgress.target(data),epoch,deed,receipt);
        if(changed) {
            QuestState.get(world.getServer()).setDirty();
            if(player!=null) {
                player.sendSystemMessage(Component.translatable(TEXT+"evidence",Component.translatable(TEXT+"deed."+deed.name().toLowerCase(java.util.Locale.ROOT))),false);
                StoryQuestService.completeIfEligible(world,player);
                de.quest.questmaster.QuestMasterUiService.refreshIfOpen(world,player);
            }
        }
        return changed;
    }
    /** New purposeful compass target: saved village position only; no structure search or chunk load. */
    public static boolean orient(ServerLevel world,ServerPlayer player,ItemStack stack) {
        var data=data(world,player.getUUID());
        if(!ArtifactJourneyProgress.active(data) || data.getStoryChapterProgress(ArtifactJourneyProgress.ARC)!=0 || player.isShiftKeyDown()) return false;
        var village=target(world,data).filter(v -> v.legacyKey().dimension().equals(world.dimension().identifier().toString()));
        if(village.isEmpty()) return false;
        var pos=village.get().arrival();
        stack.set(DataComponents.LODESTONE_TRACKER,new LodestoneTracker(Optional.of(GlobalPos.of(world.dimension(),pos)),false));
        player.sendSystemMessage(Component.translatable(TEXT+"oriented",village.get().name(),pos.getX(),pos.getZ()),false);
        confirmed(world,player,ArtifactJourneyProgress.Deed.NAVIGATE,pos); return true;
    }
    /** Successful real navigation request; only subsequent observed arrival proves animal guidance. */
    public static void following(ServerLevel world,ServerPlayer player,Animal animal) {
        var data=data(world,player.getUUID());
        if(!ArtifactJourneyProgress.active(data) || data.getStoryChapterProgress(ArtifactJourneyProgress.ARC)!=1
                || !local(world,data,animal.blockPosition()) || animal.distanceToSqr(player)<36 || FLOCKS.size()>=128 && !FLOCKS.containsKey(player.getUUID())) return;
        FLOCKS.put(player.getUUID(),new Pending(world.getServer(),world.dimension().identifier().toString(),ArtifactJourneyProgress.epoch(data),animal.getUUID(),
                animal.blockPosition(),world.getGameTime()));
    }
    /** Vanilla already performed the action. Canceled/claimed interactions and attempts cannot qualify. */
    public static void leverChanged(ServerLevel world,ServerPlayer player,BlockPos pos,BlockState before) {
        var data=data(world,player.getUUID());
        if(!ArtifactJourneyProgress.active(data) || data.getStoryChapterProgress(ArtifactJourneyProgress.ARC)!=2 || !(before.getBlock() instanceof LeverBlock)
                || !local(world,data,pos) || player.isCreative() || player.isSpectator()
                || !GuildArchiveService.isValidOwnedStack(world,player,player.getOffhandItem(),ArchiveItem.STARREACH_RING)) return;
        var reach=player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("village-quest","starreach_ring_reach");
        if(reach==null || !reach.hasModifier(id)) return;
        double distance=new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5).distanceToSqr(player.getEyePosition());
        double normal=Math.max(0,reach.getValue()-2);
        if(!leverReach(distance,normal,reach.getValue()) || !world.hasChunkAt(pos)) return;
        var after=world.getBlockState(pos);
        if(after.getBlock()==before.getBlock() && after.hasProperty(LeverBlock.POWERED)
                && after.getValue(LeverBlock.POWERED)!=before.getValue(LeverBlock.POWERED))
            confirmed(world,player,ArtifactJourneyProgress.Deed.REACH,pos);
    }
    static boolean leverReach(double distanceSquared,double normal,double extended) {
        return Double.isFinite(distanceSquared) && Double.isFinite(normal) && Double.isFinite(extended) && normal>=0 && extended>normal
                && distanceSquared>(normal+.5)*(normal+.5) && distanceSquared<=(extended+.5)*(extended+.5);
    }
    private static boolean current(Pending p,ServerLevel world,PlayerQuestData data) {
        return p.server==world.getServer() && p.dimension.equals(world.dimension().identifier().toString())
                && p.epoch!=null && p.epoch.equals(ArtifactJourneyProgress.epoch(data)) && ArtifactJourneyProgress.active(data)
                && world.getGameTime()>=p.tick && world.getGameTime()-p.tick<=600;
    }
    public static void tick(ServerLevel world,ServerPlayer player) {
        var data=data(world,player.getUUID());
        if(player.level()!=world) { FLOCKS.remove(player.getUUID()); return; }
        if(world.getGameTime()%10!=0) return;
        var follow=FLOCKS.get(player.getUUID());
        if(follow!=null) {
            if(!current(follow,world,data)) FLOCKS.remove(player.getUUID());
            else if(world.getEntity(follow.entity) instanceof Animal animal && animal.isAlive() && !animal.isRemoved()
                    && animal.distanceToSqr(player)<=9 && animal.blockPosition().distSqr(follow.position)>=16
                    && world.hasChunkAt(animal.blockPosition()) && world.getBlockState(animal.blockPosition().below()).isSolid()
                    && animal.onGround() && !animal.isInWater() && !animal.isOnFire()) {
                confirmed(world,player,ArtifactJourneyProgress.Deed.FLOCK,animal.blockPosition()); FLOCKS.remove(player.getUUID());
            }
        }
    }
    public static void packed(ServerLevel world,ServerPlayer player,int sequence,int route,UUID contract) {
        var data=data(world,player.getUUID());
        var connection=KingdomRouteConnections.route(LivingKingdomsState.get(world.getServer()),de.quest.village.VillageLifeState.get(world.getServer()),player.getUUID(),data,route);
        if(connection.filter(c -> c.active() && c.destination().equals(ArtifactJourneyProgress.target(data))).isEmpty()) return;
        if(confirmed(world,player,ArtifactJourneyProgress.Deed.SATCHEL,player.blockPosition()) || ArtifactJourneyProgress.active(data)
                && data.getStoryChapterProgress(ArtifactJourneyProgress.ARC)==3 && ArtifactJourneyProgress.has(data,ArtifactJourneyProgress.Deed.SATCHEL)
                && GuildArchiveService.hasValidInInventory(world,player,ArchiveItem.GUILD_COURIERS_SATCHEL)) {
            ArtifactJourneyProgress.pack(data,connection.get().id(),sequence,contract);
            QuestState.get(world.getServer()).setDirty();
        }
    }
    public static void delivered(ServerLevel world,UUID owner,int route,int sequence,UUID contract) {
        var data=data(world,owner); if(!ArtifactJourneyProgress.active(data)) return;
        var connection=KingdomRouteConnections.route(LivingKingdomsState.get(world.getServer()),de.quest.village.VillageLifeState.get(world.getServer()),owner,data,route);
        if(connection.filter(c -> c.active() && c.destination().equals(ArtifactJourneyProgress.target(data))
                && ArtifactJourneyProgress.matchesCargo(data,c.id(),sequence,contract)).isPresent())
            save(world,world.getServer().getPlayerList().getPlayer(owner),data,ArtifactJourneyProgress.Deed.DELIVERY,ArtifactJourneyProgress.epoch(data),connection.get().id()+":"+sequence);
    }
    public static void arrived(ServerLevel world,UUID owner,int route) {
        var data=data(world,owner); if(!ArtifactJourneyProgress.active(data)) return;
        var connection=KingdomRouteConnections.route(LivingKingdomsState.get(world.getServer()),de.quest.village.VillageLifeState.get(world.getServer()),owner,data,route);
        if(connection.filter(c -> c.active() && c.destination().equals(ArtifactJourneyProgress.target(data))).isPresent())
            save(world,world.getServer().getPlayerList().getPlayer(owner),data,ArtifactJourneyProgress.Deed.ARRIVAL,ArtifactJourneyProgress.epoch(data),connection.get().id()+":"+world.getGameTime());
    }
    public static void disconnected(UUID owner) { FLOCKS.remove(owner); }
    public static void reset() { FLOCKS.clear(); }
}
