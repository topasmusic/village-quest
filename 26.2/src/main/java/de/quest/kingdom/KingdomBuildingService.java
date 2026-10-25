package de.quest.kingdom;

import de.quest.content.block.KingdomAnchorBlock;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.network.BuildingPayloads;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageContactService;
import de.quest.village.VillageLifeState.VillageKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Native loaded-only adapter for the shared building/identity policies. No terrain construction or resident spawning. */
public final class KingdomBuildingService {
    public static final String COUNCIL_UNLOCK = "kingdoms.council_recipe", BUILDINGS_UNLOCK = "kingdoms.buildings_recipes";
    private static final String TEXT = "screen.village-quest.building.";
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new WeakHashMap<>();
    private KingdomBuildingService() {}
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(KingdomBuildingService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNTIMES.remove(server));
    }
    public static void tick(MinecraftServer server) { runtime(server).tick(); }
    private static Runtime runtime(MinecraftServer server) { return RUNTIMES.computeIfAbsent(server, Runtime::new); }
    private static PlayerQuestData data(ServerPlayer player) {
        return QuestState.get(((ServerLevel) player.level()).getServer()).getPlayerData(player.getUUID());
    }
    private static boolean unlocked(ServerPlayer player, BuildingScanner.Role role) {
        return data(player).hasMilestoneFlag(role == BuildingScanner.Role.COUNCIL ? COUNCIL_UNLOCK : BUILDINGS_UNLOCK);
    }
    private static boolean unlocked(ServerPlayer player,BuildingScanner.Role role,BlockPos position) {
        return unlocked(player,role);
    }
    public static void open(ServerLevel world, ServerPlayer player, BlockPos pos, BuildingScanner.Role role, Direction facing) {
        Runtime runtime = runtime(world.getServer()); long now = runtime.now(); runtime.budget.begin(now);
        if (player.blockPosition().distSqr(pos) > 64 || !world.hasChunkAt(pos)
                || now - runtime.lastOpen.getOrDefault(player.getUUID(), -100L) < 10
                || runtime.desks.size() >= 128 && !runtime.desks.containsKey(player.getUUID())) return;
        runtime.lastOpen.put(player.getUUID(), now);
        Desk previous = runtime.desks.remove(player.getUUID()); if (previous != null) previous.policy.close();
        var marker = ShadowsTradeRoadEncounterService.currentVillage(world, pos);
        if (marker != null) VillageContactService.establish(world, player.getUUID(), marker);
        Desk desk = new Desk(new BuildingSessionPolicy(player.getUUID(), world.dimension().identifier().toString(), pos, now), role, facing);
        if (marker!=null) runtime.state.byLegacy(new VillageKey(desk.policy.dimension(),marker.centerX(),marker.centerZ()))
                .filter(v -> v.origin()==LivingKingdomsState.Origin.GENERATED).ifPresent(v -> desk.nativeContext=v.id());
        desk.range=BuildingScanner.Range.BASIC;
        runtime.state.anchors().at(desk.policy.dimension(), pos).filter(a -> a.role() == role).ifPresent(a -> desk.range = a.range());
        runtime.desks.put(player.getUUID(), desk); runtime.offer(desk);
        runtime.entry(desk, now); runtime.send(player, desk, true);
    }
    public static void handle(ServerPlayer player, BuildingPayloads.Action action) {
        Runtime runtime = runtime(((ServerLevel) player.level()).getServer()); Desk desk = runtime.desks.get(player.getUUID());
        if (desk == null || !desk.policy.id().equals(action.session())) return;
        if (action.kind() == BuildingPayloads.Kind.CLOSE) { desk.policy.close(); runtime.desks.remove(player.getUUID()); return; }
        long now = runtime.now(); runtime.budget.begin(now);
        var access = desk.policy.validate(player.getUUID(), player.level().dimension().identifier().toString(), player.blockPosition(), now, action.revision());
        if (access != BuildingSessionPolicy.Access.ALLOWED) {
            if (access == BuildingSessionPolicy.Access.STALE) { desk.feedback = TEXT + "stale"; runtime.send(player, desk, false); }
            else { desk.policy.close(); runtime.desks.remove(player.getUUID()); }
            return;
        }
        if (now - desk.lastAction < 2) return; desk.lastAction = now; desk.pendingFound = null; desk.pendingConfirm=null;
        BlockState physical = runtime.read((ServerLevel) player.level(), desk.policy.position());
        if (physical == null || !(physical.getBlock() instanceof KingdomAnchorBlock anchor) || anchor.role() != desk.role
                || physical.getValue(KingdomAnchorBlock.FACING) != desk.facing) { desk.feedback = TEXT + "unknown"; runtime.send(player, desk, false); return; }
        desk.feedback = "";
        switch (action.kind()) {
            case SELECT -> {
                runtime.offer(desk);
                if(desk.choices.size()<2 || action.target().equals(BuildingPayloads.NONE) || !desk.policy.select(action.target())) desk.feedback=TEXT+"stale";
                else desk.explicitSelection=true;
            }
            case RECHECK -> { Entry entry = runtime.entry(desk, now); if (entry != null) entry.requested = true; }
            case EXTEND -> { desk.evidence=null; desk.evidenceTick=-1; desk.range = desk.range == BuildingScanner.Range.BASIC ? BuildingScanner.Range.EXTENDED : BuildingScanner.Range.BASIC; runtime.offer(desk); desk.policy.changed(); runtime.entry(desk, now); }
            case FOUND -> {
                runtime.offer(desk);
                if (desk.role != BuildingScanner.Role.COUNCIL || !unlocked(player, desk.role, desk.policy.position()) || !desk.choices.isEmpty()) desk.feedback = TEXT + "locked";
                else { desk.pendingFound = action.name().isBlank() ? "Rivermead" : action.name(); desk.feedback = TEXT + "checking"; desk.policy.changed(); }
            }
            case CONFIRM -> {
                if(desk.role==BuildingScanner.Role.COUNCIL && runtime.state.anchors().official(desk.policy.selected(),desk.role).isEmpty()
                        && !LivingKingdomsState.validName(action.name())) desk.feedback=TEXT+"name_invalid";
                else { desk.confirmName=desk.role==BuildingScanner.Role.COUNCIL && runtime.state.anchors().official(desk.policy.selected(),desk.role).isEmpty()?action.name():null; runtime.confirm(player, desk, now); }
            }
            case WORKSITE_CHECK -> {
                var council=runtime.state.anchors().official(desk.policy.selected(),BuildingScanner.Role.COUNCIL);
                if(desk.role==BuildingScanner.Role.COUNCIL && action.target().equals(desk.policy.selected())
                        && runtime.state.canManageVillage(player.getUUID(),desk.policy.selected())
                        && council.filter(a -> a.position().equals(desk.policy.position()) && desk.policy.matchesAnchor(a.id(),a.revision())).isPresent()) {
                    desk.pendingWorksite=true; desk.feedback=TEXT+"checking"; desk.policy.changed();
                }
            }
            case DIPLOMACY_PAGE, DIPLOMACY_OFFER, DIPLOMACY_ACCEPT, DIPLOMACY_MEETING, CRISIS_CHOICE -> {
                var council=runtime.state.anchors().official(desk.policy.selected(),BuildingScanner.Role.COUNCIL);
                boolean readable=desk.role==BuildingScanner.Role.COUNCIL && KingdomDiplomacyService.canRead(runtime.state,data(player),player.getUUID(),desk.policy.selected())
                        && runtime.state.village(desk.policy.selected()).filter(v -> v.revision()==desk.policy.villageRevision()).isPresent()
                        && council.filter(a -> a.position().equals(desk.policy.position()) && desk.policy.matchesAnchor(a.id(),a.revision())).isPresent();
                boolean saved=false;
                boolean valid=readable && runtime.state.canManageVillage(player.getUUID(),desk.policy.selected());
                if(readable && action.kind()==BuildingPayloads.Kind.DIPLOMACY_PAGE) desk.diplomacyPage=Math.min(31,action.index());
                else if(valid && action.name().matches("-?[0-9]{1,19}")) {
                    var partner=desk.diplomacy.partners().stream().filter(p -> p.village().equals(action.target())).findFirst();
                    try { if(action.kind()==BuildingPayloads.Kind.CRISIS_CHOICE) {
                        saved=KingdomCrisisService.choose(player,desk.policy.selected(),desk.diplomacy.crisis(),action.target(),Long.parseLong(action.name()),action.index());
                    } else if(partner.isPresent() && action.kind()==BuildingPayloads.Kind.DIPLOMACY_MEETING) {
                        long revision=Long.parseLong(action.name());
                        if(partner.get().deals().get(2).revision()==revision && action.index()<=2
                                && KingdomDiplomacyService.contacts(runtime.state,data(player),desk.policy.selected()).stream()
                                    .anyMatch(v -> v.id().equals(partner.get().village()) && v.revision()==partner.get().revision()))
                            saved=NativeKingdomMeetings.act(player,desk.policy.selected(),partner.get().village(),revision,action.index());
                    } else if(partner.isPresent()) saved=KingdomDiplomacyService.actNative(player,desk.policy.selected(),
                            desk.policy.villageRevision(),partner.get(),Long.parseLong(action.name()),action.index(),
                            action.kind()==BuildingPayloads.Kind.DIPLOMACY_ACCEPT); }
                    catch(NumberFormatException invalidRevision) { /* Malformed packet is never a signature. */ }
                    desk.feedback=TEXT+(saved?"diplomacy_saved":action.kind()==BuildingPayloads.Kind.DIPLOMACY_MEETING?"diplomacy_meeting_wait":"stale");
                } else desk.feedback=TEXT+"denied";
                desk.policy.changed();
            }
            case PROJECT -> {
                var council=runtime.state.anchors().official(desk.policy.selected(),BuildingScanner.Role.COUNCIL);
                boolean confirmed=false;
                if(desk.role==BuildingScanner.Role.COUNCIL && action.target().equals(desk.policy.selected()) && council.isPresent()
                        && council.get().position().equals(desk.policy.position()) && desk.policy.matchesAnchor(council.get().id(),council.get().revision())
                        && action.name().matches("[0-9]{1,19}")) {
                    try { confirmed=KingdomRecognitionService.complete(player,desk.policy.selected(),desk.policy.villageRevision(),Long.parseLong(action.name()),action.index()); }
                    catch(NumberFormatException invalid) { /* Untrusted packet number cannot prove a revision. */ }
                }
                desk.feedback=TEXT+(confirmed?"recognition_saved":"requirements"); desk.policy.changed();
            }
            case PLACE_ASSIGN, PLACE_REMOVE -> {
                var official=runtime.state.anchors().official(desk.policy.selected(),BuildingScanner.Role.MARKET);
                if(desk.role!=BuildingScanner.Role.MARKET || official.isEmpty() || !official.get().position().equals(desk.policy.position())
                        || !desk.policy.matchesAnchor(official.get().id(),official.get().revision()) || !action.target().equals(desk.policy.selected())
                        || !action.name().matches("[0-4]")) desk.feedback=TEXT+"denied";
                else {
                    int slot=Integer.parseInt(action.name()); var placeAnchor=official.get(); var places=runtime.state.marketPlaces();
                    KingdomMarketPlaces.Result result;
                    if(action.kind()==BuildingPayloads.Kind.PLACE_REMOVE) result=places.remove(player.getUUID(),desk.policy.selected(),
                            desk.policy.villageRevision(),placeAnchor.id(),placeAnchor.revision(),desk.layout.revision(),slot);
                    else if(action.index()>=desk.layout.candidates().size()) result=KingdomMarketPlaces.Result.STALE;
                    else {
                        var choice=desk.layout.candidates().get(action.index());
                        result=places.assign(player.getUUID(),desk.policy.selected(),desk.policy.villageRevision(),placeAnchor.id(),placeAnchor.revision(),
                                desk.layout.revision(),slot,choice.position(),choice.counter(),choice.container(),now);
                    }
                    desk.feedback=TEXT+switch(result) {
                        case APPLIED,UNCHANGED -> "places_saved";
                        case STALE -> "stale";
                        case CONFLICT -> "places_conflict";
                        case NO_EVIDENCE -> "requirements";
                        default -> "denied";
                    };
                    desk.policy.changed();
                }
            }
            case REBIND -> {
                var village=runtime.state.village(desk.policy.selected());
                boolean eligible=desk.role==BuildingScanner.Role.COUNCIL && village.isPresent()
                        && village.get().revision()==desk.policy.villageRevision()
                        && runtime.state.canManageVillage(player.getUUID(),village.get().id())
                        && runtime.state.anchors().official(village.get().id(),BuildingScanner.Role.COUNCIL).map(KingdomAnchorRegistry.AnchorView::earned).orElse(false);
                if (eligible && KingdomStoryService.chooseTarget((ServerLevel)player.level(),player,village.get().id(),true)) {
                    QuestState.get(runtime.server).setDirty(); desk.feedback=TEXT+"target_saved";
                } else desk.feedback=TEXT+"denied";
            }
            case RENAME -> {
                var result = desk.role != BuildingScanner.Role.COUNCIL ? LivingKingdomsState.Mutation.UNAUTHORIZED
                        : runtime.state.rename(player.getUUID(), desk.policy.selected(), desk.policy.villageRevision(), action.name());
                desk.feedback = result == LivingKingdomsState.Mutation.APPLIED || result == LivingKingdomsState.Mutation.UNCHANGED ? TEXT + "saved" : TEXT + "denied";
                runtime.refreshSelection(desk);
            }
            case RELEASE -> {
                var official = runtime.state.anchors().official(desk.policy.selected(), desk.role);
                if (!desk.policy.matchesAnchor(official.map(KingdomAnchorRegistry.AnchorView::id).orElse(BuildingPayloads.NONE),
                        official.map(KingdomAnchorRegistry.AnchorView::revision).orElse(-1L))) desk.feedback = TEXT + "stale";
                else if (official.filter(a -> a.dimension().equals(desk.policy.dimension()) && a.position().equals(desk.policy.position())).isPresent()) {
                    var result = runtime.state.anchors().detach(player.getUUID(), official.get().id(), official.get().revision());
                    desk.feedback = result == KingdomAnchorRegistry.Result.APPLIED ? TEXT + "released" : TEXT + "denied";
                } else desk.feedback = TEXT + "denied";
            }
            case MANDATE -> {
                var village = runtime.state.village(desk.policy.selected());
                boolean trusted = village.isPresent() && VillageBondService.knownBondLevel(data(player), village.get().id()).id() >= 1;
                boolean granted = village.isPresent() && runtime.state.recognizeMediator(village.get().id(), player.getUUID(), trusted);
                desk.feedback = granted || village.isPresent() && runtime.state.canManageVillage(player.getUUID(), village.get().id())
                        ? TEXT + "mandate_granted" : TEXT + "mandate_needed";
                runtime.refreshSelection(desk);
            }
            default -> { }
        }
        runtime.send(player, desk, false);
    }
    /** Successful native mutations only. Called by Level#setBlock without reading blocks or loading data/chunks. */
    public static void onBlockChanged(ServerLevel world, BlockPos pos) {
        Runtime runtime = RUNTIMES.get(world.getServer()); if (runtime == null) return;
        runtime.state.worksites().invalidateAt(world.dimension().identifier().toString(),pos);
        for (UUID id : runtime.watches.dirty(world.dimension().identifier().toString(), pos)) {
            runtime.queue.cancel(id); Entry entry = runtime.entries.get(id);
            if (entry != null) {
                entry.requested = true;
                runtime.state.anchors().anchor(entry.anchorId).filter(a -> a.position().equals(entry.spec.position()))
                        .ifPresent(a -> runtime.state.anchors().invalidateEvidence(a.id()));
            }
        }
    }
    public static BuildingWorkBudget workBudget(MinecraftServer server) { Runtime runtime = runtime(server); runtime.budget.begin(runtime.now()); return runtime.budget; }

    /** Uses the existing bounded scanner/cache. Dirty, unloaded and expired reports are never diagnoses. */
    public static boolean useVillageCheck(ServerLevel world,ServerPlayer player) {
        if(world==null || player==null || !world.getServer().isSameThread()) return false;
        Runtime runtime=runtime(world.getServer()); long now=runtime.now();
        var last=runtime.lastCheck.get(player.getUUID());
        if(last==null && runtime.lastCheck.size()>=128) return false;
        int cooldown=data(player).hasMilestoneFlag(ArtifactJourneyProgress.PREFIX+"seal_comfort")?60:100;
        if(last!=null && now>=last && now-last<cooldown) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_check.cooldown"),true); return false;
        }
        runtime.lastCheck.put(player.getUUID(),now);
        var request=new Diagnosis(world.dimension().identifier().toString(),player.blockPosition(),java.util.UUID.randomUUID(),now+600);
        runtime.diagnoses.put(player.getUUID(),request);
        return sendVillageCheck(runtime,world,player,request,true)>0;
    }
    private static final class Diagnosis {
        final String dimension; final BlockPos origin; final UUID session; final long until;
        List<de.quest.network.VillageCheckPayload.Marker> markers=List.of();
        Diagnosis(String dimension,BlockPos origin,UUID session,long until) { this.dimension=dimension;this.origin=origin.immutable();this.session=session;this.until=until; }
    }
    private static int sendVillageCheck(Runtime runtime,ServerLevel world,ServerPlayer player,Diagnosis request,boolean initial) {
        long now=runtime.now();
        String dimension=world.dimension().identifier().toString();
        var markers=new ArrayList<de.quest.network.VillageCheckPayload.Marker>();
        int confirmed=0;
        for(var anchor:runtime.state.anchors().near(dimension,player.blockPosition(),48,8)) {
            var spec=new BuildingWatchIndex.Spec(dimension,anchor.position(),anchor.facing(),anchor.role(),anchor.range(),anchor.revision());
            if(!world.hasChunkAt(anchor.position()) || !loaded(world,spec)) continue;
            // A removed or rotated physical anchor must not display the previous successful report.
            runtime.budget.begin(now); BlockState block=runtime.read(world,anchor.position());
            if(block==null || !(block.getBlock() instanceof KingdomAnchorBlock physical) || physical.role()!=anchor.role()
                    || block.getValue(KingdomAnchorBlock.FACING)!=anchor.facing()) continue;
            Entry entry=runtime.interest(spec,anchor.id(),now); if(entry==null) continue;
            var snapshot=runtime.watches.display(entry.id,now);
            var report=snapshot.map(BuildingWatchIndex.Snapshot::report).orElse(null);
            var status=report==null?BuildingScanner.Status.UNKNOWN:report.status();
            if(status==BuildingScanner.Status.VALID && !runtime.state.anchors().mayContinue(anchor.id(),now))
                status=runtime.state.anchors().report(anchor.id()).isEmpty()?BuildingScanner.Status.INVALID:BuildingScanner.Status.UNKNOWN;
            // A geometrically valid scan may still lose an exclusive furnishing to another building.
            boolean assignmentConflict=status==BuildingScanner.Status.INVALID && snapshot.filter(s -> s.report().status()==BuildingScanner.Status.VALID).isPresent();
            String name=runtime.state.village(anchor.villageId()).map(LivingKingdomsState.VillageView::name).orElse("");
            markers.add(new de.quest.network.VillageCheckPayload.Marker(anchor.position(),status,anchor.role(),name,
                    status==BuildingScanner.Status.UNKNOWN?List.of():snapshot.map(s -> rows(s.report())).orElse(List.of()),assignmentConflict));
            if(runtime.watches.fresh(entry.id,now).isEmpty() || status==BuildingScanner.Status.UNKNOWN) { entry.requested=true; continue; }
            confirmed++;
            if(status==BuildingScanner.Status.VALID && anchor.villageId().equals(ArtifactJourneyProgress.target(data(player))))
                ArtifactJourneyService.confirmed(world,player,ArtifactJourneyProgress.Deed.CHECK,anchor.position());
        }
        if(markers.isEmpty() && initial) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_check.empty"),true); return 0;
        }
        if(initial || !request.markers.equals(markers)) {
            request.markers=List.copyOf(markers);
            var payload=new de.quest.network.VillageCheckPayload(dimension,markers,request.session,initial);
            if(ServerPlayNetworking.canSend(player,de.quest.network.VillageCheckPayload.ID)) ServerPlayNetworking.send(player,payload);
        }
        return confirmed;
    }
    static BuildingScanner.Status villageCheckStatus(LivingKingdomsState state,UUID anchor,BuildingScanner.Report report,long now) {
        if(report==null || state.readOnly()) return BuildingScanner.Status.UNKNOWN;
        return report.status()==BuildingScanner.Status.VALID && !state.anchors().maySpawn(anchor,now)
                ?BuildingScanner.Status.INVALID:report.status();
    }
    static boolean foundingEvidenceFresh(NativeFoundingObserver.Evidence evidence,long observed,long now) {
        return evidence!=null && evidence.fullyLoaded() && observed>=0 && now>=observed && now-observed<=40;
    }
    static boolean foundingEvidenceReady(NativeFoundingObserver.Evidence evidence,long observed,long now) {
        return foundingEvidenceFresh(evidence,observed,now) && evidence.adults()>=3 && evidence.beds()>=3 && evidence.hasJob();
    }

    private static final class Runtime {
        final MinecraftServer server; final LivingKingdomsState state;
        final BuildingWorkBudget budget = new BuildingWorkBudget(); final BuildingWatchIndex watches = new BuildingWatchIndex();
        final BuildingScanQueue queue = new BuildingScanQueue();
        final Map<UUID, Desk> desks = new HashMap<>(); final Map<UUID, Long> lastOpen = new HashMap<>();
        final Map<UUID,Long> lastCheck=new HashMap<>();
        final Map<UUID,Diagnosis> diagnoses=new HashMap<>();
        final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
        int cursor, playerCursor, proofCursor;
        Runtime(MinecraftServer server) { this.server = server; state = LivingKingdomsState.get(server); }
        long now() { return server.overworld().getGameTime(); }
        BlockState read(ServerLevel world, BlockPos pos) {
            return world.hasChunkAt(pos) && budget.takeBlock() ? world.getBlockState(pos) : null;
        }
        Entry entry(Desk desk, long now) {
            var official = state.anchors().at(desk.policy.dimension(), desk.policy.position());
            long revision = official.filter(a -> a.role() == desk.role && a.range() == desk.range && a.facing() == desk.facing).map(KingdomAnchorRegistry.AnchorView::revision).orElse(0L);
            UUID logical = official.filter(a -> a.role() == desk.role && a.range() == desk.range && a.facing() == desk.facing).map(KingdomAnchorRegistry.AnchorView::id).orElse(BuildingPayloads.NONE);
            Entry entry = interest(new BuildingWatchIndex.Spec(desk.policy.dimension(), desk.policy.position(), desk.facing, desk.role, desk.range, revision), logical, now);
            if (entry != null) desk.scan = entry.id; return entry;
        }
        Entry interest(BuildingWatchIndex.Spec spec, UUID anchor, long now) {
            UUID id = UUID.nameUUIDFromBytes((spec.dimension() + "/" + spec.position() + "/" + spec.role() + "/" + spec.range() + "/" + spec.facing()).getBytes(StandardCharsets.UTF_8));
            if (!watches.watch(id, spec)) return null;
            Entry entry = entries.get(id);
            if (entry == null) { entry = new Entry(id, spec, anchor); entries.put(id, entry); }
            else if (!entry.spec.equals(spec)) { queue.cancel(id); entry.spec = spec; entry.requested = true; }
            entry.anchorId = anchor; entry.interested = now; return entry;
        }
        void tick() {
            long now = now(); budget.begin(now);
            if(now%10==0) for(var owner:List.copyOf(diagnoses.keySet())) {
                var request=diagnoses.get(owner); var player=server.getPlayerList().getPlayer(owner);
                if(player==null || now>request.until || now<request.until-600
                        || !request.dimension.equals(player.level().dimension().identifier().toString())
                        || request.origin.distSqr(player.blockPosition())>48L*48) { diagnoses.remove(owner); continue; }
                sendVillageCheck(this,player.level(),player,request,false);
            }
            if (now % 20 == 0) {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                for (int n = 0; n < Math.min(32, players.size()); n++) {
                    ServerPlayer player = players.get(Math.floorMod(playerCursor++, players.size()));
                    for (var anchor : state.anchors().near(player.level().dimension().identifier().toString(), player.blockPosition(), 64, 8))
                        interest(new BuildingWatchIndex.Spec(anchor.dimension(), anchor.position(), anchor.facing(), anchor.role(), anchor.range(), anchor.revision()), anchor.id(), now);
                }
                lastOpen.entrySet().removeIf(e -> now >= e.getValue() && now - e.getValue() > 1200);
                lastCheck.entrySet().removeIf(e -> now>=e.getValue() && now-e.getValue()>1200);
            }
            for (var owner : List.copyOf(desks.keySet())) {
                Desk desk = desks.get(owner); ServerPlayer player = server.getPlayerList().getPlayer(owner);
                if (player == null || desk.policy.validate(owner, player.level().dimension().identifier().toString(), player.blockPosition(), now, desk.policy.revision()) != BuildingSessionPolicy.Access.ALLOWED) {
                    desk.policy.close(); desks.remove(owner); continue;
                }
                entry(desk, now);
            }
            for (Entry entry : List.copyOf(entries.values())) if (now >= entry.interested && now - entry.interested > 200) {
                queue.cancel(entry.id); watches.forget(entry.id); entries.remove(entry.id);
                invalidateEntry(entry);
            }
            List<Desk> pending = desks.values().stream().filter(desk -> desk.pendingFound != null).toList();
            if (!pending.isEmpty() && budget.paths() >= 4 && budget.blocks() >= 16) {
                Desk desk = pending.get(Math.floorMod(proofCursor++, pending.size()));
                found(server.getPlayerList().getPlayer(desk.policy.owner()), desk, now);
            }
            var work=desks.values().stream().filter(desk -> desk.pendingWorksite).toList();
            if(!work.isEmpty() && budget.paths()>=4 && budget.blocks()>=16)
                worksite(work.get(Math.floorMod(proofCursor++,work.size())),now);
            // Read-only founding preview before the button is enabled. The final mutation rechecks everything.
            var previews=desks.values().stream().filter(d -> d.role==BuildingScanner.Role.COUNCIL).toList();
            if(now%20==0 && !previews.isEmpty() && budget.paths()>=4 && budget.blocks()>=16) {
                var desk=previews.get(Math.floorMod(proofCursor++,previews.size()));
                var player=server.getPlayerList().getPlayer(desk.policy.owner());
                if(player!=null) {
                    var village=desk.policy.selected();
                    var world=(ServerLevel)player.level();
                    var evidence=village.equals(BuildingPayloads.NONE)
                            ? NativeFoundingObserver.observe(world,desk.policy.position(),desk.range,budget.paths(),budget::takeBlock,
                                id -> state.founding().residentOwner(id).isEmpty())
                            : NativeFoundingObserver.observeVillage(world,state,village,budget.paths(),budget::takeBlock,
                                id -> state.founding().residentOwner(id).filter(owner -> !owner.equals(village)).isEmpty());
                    if(!village.equals(BuildingPayloads.NONE) && state.canManageVillage(player.getUUID(),village)
                            && state.anchors().official(village,BuildingScanner.Role.COUNCIL).filter(a -> state.anchors().maySpawn(a.id(),now)).isPresent())
                        state.founding().claim(player.getUUID(),village,state.village(village).orElseThrow().revision(),evidence.residents(),evidence.fullyLoaded(),true);
                    budget.takePaths(evidence.pathChecks());
                    boolean changed=!java.util.Objects.equals(desk.evidence,evidence);
                    desk.evidence=evidence; desk.evidenceTick=now;
                    if(changed) desk.policy.changed();
                    send(player,desk,false);
                }
            }
            // Two close desks first, then rotating background interests; at most eight concurrent full snapshots.
            int close = 0;
            for (Desk desk : desks.values()) if (close++ < 2) { Entry entry = entries.get(desk.scan); if (entry != null) control(entry, now); }
            List<Entry> candidates = new ArrayList<>(entries.values());
            for (int n = 0; n < Math.min(8, candidates.size()) && budget.blocks() > 32; n++)
                control(candidates.get(Math.floorMod(cursor++, candidates.size())), now);
            int used = queue.tick(now, Math.max(0, budget.blocks() - 16)); budget.takeBlocks(used);
            // Complete one accepted confirmation after a fresh scan, within the existing desk lifetime/budget.
            var confirmations=desks.values().stream().filter(d -> d.pendingConfirm!=null && watches.fresh(d.scan,now).isPresent()).toList();
            if(!confirmations.isEmpty() && budget.blocks()>0) {
                var desk=confirmations.get(Math.floorMod(proofCursor++,confirmations.size()));
                var player=server.getPlayerList().getPlayer(desk.policy.owner());
                if(player!=null) { confirm(player,desk,now); send(player,desk,false); }
            }
        }
        void control(Entry entry, long now) {
            Identifier dimension = Identifier.tryParse(entry.spec.dimension());
            ServerLevel world = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (world == null || !loaded(world, entry.spec)) {
                watches.unloaded(entry.id); queue.cancel(entry.id); invalidateEntry(entry); return;
            }
            watches.resumed(entry.id);
            BlockState physical = read(world, entry.spec.position());
            if (physical == null) return;
            if (!(physical.getBlock() instanceof KingdomAnchorBlock anchor) || anchor.role() != entry.spec.role()) {
                watches.unloaded(entry.id); queue.cancel(entry.id);
                state.anchors().anchor(entry.anchorId).filter(a -> a.position().equals(entry.spec.position())).ifPresent(a -> state.anchors().removedPhysical(a.id())); return;
            }
            if (physical.getValue(KingdomAnchorBlock.FACING) != entry.spec.facing()) {
                watches.unloaded(entry.id); queue.cancel(entry.id); invalidateEntry(entry); return;
            }
            var cached = watches.cached(entry.id, now, 1600);
            if (!entry.anchorId.equals(BuildingPayloads.NONE) && cached.isPresent())
                state.anchors().maintainCached(entry.anchorId, entry.spec.configurationRevision(), cached.get().tick(), now);
            if (queue.pending(entry.id) || queue.size() >= 8 || !entry.requested && watches.cached(entry.id, now, 40).isPresent()) return;
            Set<BlockPos> homes = new HashSet<>();
            boolean homesComplete=true;
            if (entry.spec.role() == BuildingScanner.Role.INN) {
                var p = entry.spec.position(); int r = entry.spec.range().horizontal, h = entry.spec.range().vertical;
                var residents=world.getEntitiesOfClass(Villager.class, new AABB(p.getX()-r, p.getY()-h, p.getZ()-r, p.getX()+r+1, p.getY()+h+1, p.getZ()+r+1), v -> v.isAlive() && !v.isRemoved()).stream().limit(65).toList();
                homesComplete=residents.size()<=64;
                for (Villager villager : residents.stream().limit(64).toList())
                    villager.getBrain().getMemory(MemoryModuleType.HOME).filter(home -> home.dimension().equals(world.dimension())).ifPresent(home -> homes.add(home.pos()));
            }
            final boolean completeHomes=homesComplete;
            var inn=state.anchors().anchor(entry.anchorId).map(KingdomAnchorRegistry.AnchorView::villageId).orElse(null);
            var probe = new NativeBuildingProbe(world, pos -> !homes.contains(pos) && state.founding().bedOwner(entry.spec.dimension(), pos).isEmpty(),
                    (foot,head,bed) -> entry.spec.role()==BuildingScanner.Role.INN?NativeInnBeds.buildingUse(world,inn,foot,head,bed,homes,completeHomes)
                            :new NativeBuildingProbe.BedUse(KingdomBedRegistry.Permission.AVAILABLE,false));
            long version = watches.revision(entry.id); BuildingWatchIndex.Spec spec = entry.spec;
            boolean requested = queue.request(entry.id, now, new BuildingScanner(spec.position(), spec.facing(), spec.role(), spec.range(), probe), (id, report) -> {
                long completed = now();
                if (!watches.publish(id, version, report, completed)) return;
                state.anchors().anchor(entry.anchorId).filter(a -> a.position().equals(spec.position()) && a.revision() == spec.configurationRevision()
                        && a.range() == spec.range() && a.facing() == spec.facing()).ifPresent(a -> state.anchors().observe(a.id(), a.revision(), report, completed));
                for (Desk desk : desks.values()) if (desk.scan.equals(id)) {
                    desk.policy.changed(); ServerPlayer player = server.getPlayerList().getPlayer(desk.policy.owner());
                    if (player != null) send(player, desk, false);
                }
            });
            if (requested) entry.requested = false;
        }
        void invalidateEntry(Entry entry) {
            state.anchors().anchor(entry.anchorId).filter(a -> a.dimension().equals(entry.spec.dimension())
                    && a.position().equals(entry.spec.position()) && a.revision() == entry.spec.configurationRevision()
                    && a.facing() == entry.spec.facing() && a.range() == entry.spec.range())
                    .ifPresent(a -> state.anchors().invalidateEvidence(a.id()));
        }
        void worksite(Desk desk,long now) {
            desk.pendingWorksite=false; var player=server.getPlayerList().getPlayer(desk.policy.owner());
            UUID village=desk.policy.selected(); var council=state.anchors().official(village,BuildingScanner.Role.COUNCIL);
            if(player==null || !state.canManageVillage(player.getUUID(),village) || council.isEmpty()
                    || !council.get().position().equals(desk.policy.position()) || !state.anchors().maySpawn(council.get().id(),now)) {
                desk.feedback=TEXT+"requirements"; return;
            }
            var world=(ServerLevel)player.level(); String dimension=world.dimension().identifier().toString();
            var evidence=NativeFoundingObserver.observe(world,desk.policy.position(),council.get().range(),budget.paths(),budget::takeBlock,id ->
                    state.founding().residentOwner(id).filter(owner -> !owner.equals(village)).isEmpty()
                    && world.getEntity(id) instanceof Villager resident
                    && resident.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()
                    && state.resolveAt(dimension,resident.blockPosition()).filter(v -> v.id().equals(village)).isPresent());
            budget.takePaths(evidence.pathChecks());
            boolean valid=state.worksites().observe(village,desk.policy.villageRevision(),council.get().id(),council.get().revision(),now,evidence);
            desk.feedback=TEXT+(valid?"worksite_verified":evidence.fullyLoaded()?"worksite_missing":"unknown"); desk.policy.changed(); send(player,desk,true);
        }
        void offer(Desk desk) {
            var options = BuildingVillageChoices.offer(state,desk.policy.dimension(),desk.policy.position(),desk.nativeContext,desk.role,desk.range);
            Map<UUID, Long> offered = new LinkedHashMap<>(); for (var village : options) offered.put(village.id(), village.revision());
            desk.policy.offer(offered); desk.choices = options.stream().map(v -> new BuildingPayloads.Choice(v.id(), v.name())).toList();
            if(options.size()==1) { desk.explicitSelection=false; desk.policy.select(options.getFirst().id()); }
            else if(!desk.explicitSelection || desk.policy.selected().equals(BuildingPayloads.NONE)) { desk.explicitSelection=false; desk.policy.clearSelection(); }
        }
        void refreshSelection(Desk desk) { offer(desk); if(!desk.policy.selected().equals(BuildingPayloads.NONE)) desk.policy.select(desk.policy.selected()); desk.policy.changed(); }
        void confirm(ServerPlayer player, Desk desk, long now) {
            if (!unlocked(player, desk.role, desk.policy.position())) { desk.pendingConfirm=null; desk.feedback=TEXT+"locked"; return; }
            if(binding(state,desk.policy.dimension(),desk.policy.position(),desk.role,desk.facing,desk.range,desk.policy.selected())==BuildingPayloads.Binding.REGISTERED) {
                desk.pendingConfirm=null; desk.confirmName=null; desk.feedback=TEXT+"membership_saved"; return;
            }
            var old = state.anchors().official(desk.policy.selected(), desk.role);
            UUID official=old.map(KingdomAnchorRegistry.AnchorView::id).orElse(BuildingPayloads.NONE);
            long anchorRevision=old.map(KingdomAnchorRegistry.AnchorView::revision).orElse(-1L);
            if(desk.pendingConfirm==null) desk.pendingConfirm=new PendingConfirmation(desk.policy.selected(),desk.policy.villageRevision(),official,anchorRevision,desk.range);
            var request=desk.pendingConfirm;
            if(request.village().equals(BuildingPayloads.NONE) || !request.village().equals(desk.policy.selected())
                    || request.villageRevision()!=desk.policy.villageRevision() || request.range()!=desk.range
                    || !request.official().equals(official) || request.anchorRevision()!=anchorRevision
                    || !desk.policy.matchesAnchor(official,anchorRevision)) {
                desk.pendingConfirm=null; desk.feedback=TEXT+"stale"; return;
            }
            var snapshot = watches.fresh(desk.scan, now);
            if (snapshot.isEmpty()) {
                Entry pending=entries.get(desk.scan); if(pending!=null) pending.requested=true;
                desk.feedback=TEXT+"checking"; return;
            }
            if (snapshot.get().report().status() != BuildingScanner.Status.VALID) {
                desk.pendingConfirm=null; desk.feedback=TEXT+(snapshot.get().report().status()==BuildingScanner.Status.UNKNOWN?"unknown":"requirements"); return;
            }
            BlockState physical=read((ServerLevel)player.level(),desk.policy.position());
            if(physical==null) { desk.feedback=TEXT+"checking"; return; }
            if(!(physical.getBlock() instanceof KingdomAnchorBlock anchor) || anchor.role()!=desk.role || physical.getValue(KingdomAnchorBlock.FACING)!=desk.facing) {
                desk.pendingConfirm=null; desk.feedback=TEXT+"unknown"; return;
            }
            desk.pendingConfirm=null;
            // Re-evaluate actual membership immediately before mutation, retaining the clicked context/revisions.
            var candidates=BuildingVillageChoices.offer(state,desk.policy.dimension(),desk.policy.position(),desk.nativeContext,desk.role,desk.range);
            if(candidates.size()>1 && !desk.explicitSelection || candidates.stream().noneMatch(v -> v.id().equals(request.village()) && v.revision()==request.villageRevision())) {
                desk.feedback=TEXT+"stale"; refreshSelection(desk); return;
            }
            var result = desk.confirmName==null?state.confirmBuilding(player.getUUID(), desk.policy.selected(), desk.policy.villageRevision(),
                    old.map(KingdomAnchorRegistry.AnchorView::id).orElseGet(UUID::randomUUID), old.map(KingdomAnchorRegistry.AnchorView::revision).orElse(-1L),
                    desk.policy.position(), desk.facing, desk.range, snapshot.get().report(), snapshot.get().tick(), now)
                    :state.confirmBuilding(player.getUUID(), desk.policy.selected(), desk.policy.villageRevision(),
                    old.map(KingdomAnchorRegistry.AnchorView::id).orElseGet(UUID::randomUUID),old.map(KingdomAnchorRegistry.AnchorView::revision).orElse(-1L),
                    desk.policy.position(),desk.facing,desk.range,snapshot.get().report(),snapshot.get().tick(),now,desk.confirmName);
            desk.confirmName=null;
            desk.feedback = result == KingdomAnchorRegistry.Result.APPLIED || result == KingdomAnchorRegistry.Result.UNCHANGED ? TEXT + "saved"
                    : result == KingdomAnchorRegistry.Result.STALE ? TEXT+"stale"
                    : result == KingdomAnchorRegistry.Result.STATION_CONFLICT || result == KingdomAnchorRegistry.Result.POSITION_CONFLICT || result == KingdomAnchorRegistry.Result.ALREADY_ASSIGNED
                    ? TEXT + "station_conflict" : result == KingdomAnchorRegistry.Result.INVALID ? TEXT + "connection_needed" : TEXT + "denied";
            if(result==KingdomAnchorRegistry.Result.STALE) refreshSelection(desk);
            if (result == KingdomAnchorRegistry.Result.APPLIED || result == KingdomAnchorRegistry.Result.UNCHANGED) {
                KingdomStoryService.chooseTarget((ServerLevel)player.level(),player,desk.policy.selected(),false);
                data(player).setMilestoneFlag("kingdoms.anchor." + desk.role.name(), true); QuestState.get(server).setDirty();
                refreshSelection(desk); entry(desk, now);
            }
        }
        void found(ServerPlayer player, Desk desk, long now) {
            offer(desk);
            if (player == null || !unlocked(player, desk.role, desk.policy.position()) || !desk.choices.isEmpty()) {
                desk.pendingFound = null; desk.feedback = TEXT + "locked"; if (player != null) send(player, desk, false); return;
            }
            var snapshot = watches.fresh(desk.scan, now);
            if (snapshot.isEmpty()) {
                Entry entry = entries.get(desk.scan); if (entry != null) entry.requested = true; return;
            }
            if (snapshot.get().report().status() != BuildingScanner.Status.VALID) {
                desk.pendingFound = null; desk.feedback = snapshot.get().report().status() == BuildingScanner.Status.UNKNOWN ? TEXT + "unknown" : TEXT + "requirements";
                send(player, desk, false); return;
            }
            BlockState physical = read((ServerLevel) player.level(), desk.policy.position());
            if (physical == null || !(physical.getBlock() instanceof KingdomAnchorBlock anchor) || anchor.role() != BuildingScanner.Role.COUNCIL
                    || physical.getValue(KingdomAnchorBlock.FACING) != desk.facing) { desk.pendingFound = null; desk.feedback = TEXT + "unknown"; send(player, desk, false); return; }
            VillageKey key = null;
            for (int n = 0; n < 64 && key == null; n++) {
                var proposed = new VillageKey(desk.policy.dimension(), desk.policy.position().getX() + n % 8, desk.policy.position().getZ() + n / 8);
                if (state.byLegacy(proposed).isEmpty()) key = proposed;
            }
            LivingKingdomsState.FoundingResult result = key == null ? new LivingKingdomsState.FoundingResult(LivingKingdomsState.FoundingCode.CONFLICT, null)
                    : state.foundCustom(UUID.randomUUID(), player.getUUID(), key, desk.policy.position(), desk.pendingFound, UUID.randomUUID(), desk.facing, desk.range,
                    snapshot.get().report(), snapshot.get().tick(), now, List.of(), true);
            desk.pendingFound = null;
            desk.feedback = switch (result.code()) {
                case APPLIED, UNCHANGED -> TEXT + "founded";
                case UNKNOWN -> TEXT + "unknown";
                case CONFLICT -> TEXT + "station_conflict";
                case READ_ONLY -> TEXT + "denied";
                default -> TEXT + "requirements";
            };
            if (result.code() == LivingKingdomsState.FoundingCode.APPLIED) {
                offer(desk); desk.policy.select(result.villageId());
                KingdomStoryService.chooseTarget((ServerLevel)player.level(),player,result.villageId(),false);
                data(player).setMilestoneFlag("kingdoms.anchor.COUNCIL", true); QuestState.get(server).setDirty(); entry(desk, now);
                var marker = ShadowsTradeRoadEncounterService.currentVillage((ServerLevel) player.level(), desk.policy.position());
                if (marker != null) VillageContactService.establish((ServerLevel) player.level(), player.getUUID(), marker);
            }
            desk.policy.changed(); send(player, desk, false);
        }
        void send(ServerPlayer player, Desk desk, boolean open) {
            offer(desk);
            long now = now(); var snapshot = watches.display(desk.scan, now); var village = state.village(desk.policy.selected());
            var official = state.anchors().official(desk.policy.selected(), desk.role);
            desk.policy.offerAnchor(official.map(KingdomAnchorRegistry.AnchorView::id).orElse(BuildingPayloads.NONE),
                    official.map(KingdomAnchorRegistry.AnchorView::revision).orElse(-1L));
            // Keep the last completed result visible during routine revalidation. Dirtiness/unload still revokes it.
            // This is an advisory button state: confirm/found independently require fresh authoritative evidence.
            BuildingScanner.Status status = snapshot.map(s -> s.report().status()).orElse(BuildingScanner.Status.UNKNOWN);
            var evidence = desk.evidence;
            String name = village.map(LivingKingdomsState.VillageView::name).orElse("");
            var layout=BuildingPayloads.MarketLayout.EMPTY;
            if(desk.role==BuildingScanner.Role.MARKET && official.filter(a -> a.position().equals(desk.policy.position())).isPresent()) {
                var anchor=official.orElseThrow(); var places=state.marketPlaces(); var usable=places.usable(anchor.id(),now);
                layout=new BuildingPayloads.MarketLayout(places.revision(anchor.id()),places.assigned(anchor.id()).stream()
                        .map(p -> stand(p,usable.contains(p))).toList(),places.candidates(anchor.id(),now).stream().map(p -> stand(p,true)).toList(),
                        state.marketTravel().at(anchor.villageId()).stream().filter(j -> state.markets().visit(anchor.villageId()).filter(v -> v.id().equals(j.visit())).isPresent())
                                .limit(5).map(j -> new BuildingPayloads.Travel(j.person(),state.people().person(j.person()).orElseThrow().name(),
                                        state.village(j.home()).orElseThrow().name(),name,j.phase().name().toLowerCase(java.util.Locale.ROOT),
                                        (int)j.remaining(),j.paused(),j.late())).toList());
            }
            if(!layout.equals(desk.layout)) { desk.layout=layout; desk.policy.changed(); }
            var diplomacy=desk.role==BuildingScanner.Role.COUNCIL?KingdomDiplomacyService.view(state,data(player),player.getUUID(),desk.policy.selected(),desk.diplomacyPage,
                    de.quest.caravan.KingdomRouteConnections.read(server)):BuildingPayloads.Diplomacy.EMPTY;
            if(desk.role==BuildingScanner.Role.COUNCIL) diplomacy=new BuildingPayloads.Diplomacy(diplomacy.page(),diplomacy.pages(),diplomacy.partners(),diplomacy.readOnly(),
                    KingdomCrisisService.view(player,desk.policy.selected()));
            if(!diplomacy.equals(desk.diplomacy)) { desk.diplomacy=diplomacy; desk.policy.changed(); }
            var payload = new BuildingPayloads.View(desk.policy.id(), desk.policy.revision(), open, desk.policy.position(), desk.role, desk.range, status,
                    desk.policy.selected(), desk.policy.villageRevision(), official.map(KingdomAnchorRegistry.AnchorView::id).orElse(BuildingPayloads.NONE),
                    official.map(KingdomAnchorRegistry.AnchorView::revision).orElse(-1L), state.canManageVillage(player.getUUID(), desk.policy.selected()),
                    desk.role == BuildingScanner.Role.COUNCIL && desk.policy.selected().equals(BuildingPayloads.NONE) && unlocked(player, desk.role, desk.policy.position())
                            && status==BuildingScanner.Status.VALID,
                    evidence == null ? 0 : evidence.adults(), evidence == null ? 0 : evidence.beds(), evidence != null && evidence.hasJob(), name,
                    snapshot.map(s -> rows(s.report())).orElse(List.of()), desk.choices, desk.feedback,desk.layout,
                    desk.role==BuildingScanner.Role.COUNCIL?KingdomRecognitionService.view(player,desk.policy.selected()):BuildingPayloads.Recognition.EMPTY,desk.diplomacy,
                    foundingEvidenceFresh(evidence,desk.evidenceTick,now),binding(state,desk.policy.dimension(),desk.policy.position(),desk.role,desk.facing,desk.range,desk.policy.selected()),
                    desk.role==BuildingScanner.Role.INN && official.filter(a -> a.position().equals(desk.policy.position())).isPresent()
                            ? state.tavernGuests().openingText(desk.policy.selected(),now) : Component.empty());
            if (ServerPlayNetworking.canSend(player, BuildingPayloads.View.ID)) ServerPlayNetworking.send(player, payload);
            else player.sendSystemMessage(Component.translatable(TEXT + "fallback", Component.translatable(TEXT + "role." + desk.role.name().toLowerCase(java.util.Locale.ROOT))), false);
        }
    }
    static BuildingPayloads.Binding binding(LivingKingdomsState state,String dimension,BlockPos pos,BuildingScanner.Role role,
            Direction facing,BuildingScanner.Range range,UUID village) {
        var official=state.anchors().official(village,role);
        if(official.isEmpty()) return BuildingPayloads.Binding.UNREGISTERED;
        var here=state.anchors().at(dimension,pos).filter(a -> a.id().equals(official.get().id()));
        if(here.isEmpty()) return BuildingPayloads.Binding.ELSEWHERE;
        return here.get().range()==range && here.get().facing()==facing?BuildingPayloads.Binding.REGISTERED:BuildingPayloads.Binding.CHANGED;
    }
    private static BuildingPayloads.Stand stand(KingdomMarketPlaces.Place place,boolean usable) {
        return new BuildingPayloads.Stand(place.slot(),place.position(),place.counter(),place.container(),usable);
    }
    static List<BuildingPayloads.Row> rows(BuildingScanner.Report report) {
        List<BuildingPayloads.Row> rows = new ArrayList<>();
        for (BuildingScanner.Check check : BuildingScanner.Check.values()) if (report.checks().containsKey(check)) {
            BuildingScanner.Feature feature = switch (check) {
                case LIGHT -> BuildingScanner.Feature.LIGHT; case TABLE -> BuildingScanner.Feature.TABLE; case SEATS -> BuildingScanner.Feature.SEAT;
                case BOOKS -> BuildingScanner.Feature.BOOKS; case BARRELS -> BuildingScanner.Feature.BARREL; case KITCHEN -> BuildingScanner.Feature.KITCHEN;
                case BED -> BuildingScanner.Feature.BED; case COUNTER -> BuildingScanner.Feature.COUNTER; case CONTAINERS -> BuildingScanner.Feature.CONTAINER;
                case WORKSTATION -> BuildingScanner.Feature.WORKSTATION; default -> null;
            };
            Set<BlockPos> targets = feature == null ? report.walkable() : report.stations().getOrDefault(feature, Set.of());
            boolean realStall=check==BuildingScanner.Check.COUNTER && !report.stations().getOrDefault(BuildingScanner.Feature.STALL,Set.of()).isEmpty();
            if(realStall) targets=report.stations().get(BuildingScanner.Feature.STALL);
            int required = switch (check) { case ENTRY -> 4; case SEATS -> report.role() == BuildingScanner.Role.COUNCIL ? 2 : 5; case BARRELS -> 2;
                case COUNTER -> realStall?1:2;
                case CONTAINERS -> report.role() == BuildingScanner.Role.STOREHOUSE ? 4 : 1; default -> 1; };
            int found = feature == null ? (report.checks().get(check) ? required : 0) : targets.size();
            rows.add(new BuildingPayloads.Row(check, report.checks().get(check), Math.min(4096, found), required,
                    targets.stream().sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).limit(64).toList()));
        }
        return List.copyOf(rows);
    }
    private static boolean loaded(ServerLevel world, BuildingWatchIndex.Spec spec) {
        BlockPos pos = spec.position(); int radius = spec.range().horizontal;
        for (int x = (pos.getX()-radius) >> 4; x <= (pos.getX()+radius) >> 4; x++)
            for (int z = (pos.getZ()-radius) >> 4; z <= (pos.getZ()+radius) >> 4; z++) if (!world.hasChunk(x,z)) return false;
        return true;
    }
    private static final class Entry {
        final UUID id; BuildingWatchIndex.Spec spec; UUID anchorId; long interested; boolean requested = true;
        Entry(UUID id, BuildingWatchIndex.Spec spec, UUID anchorId) { this.id = id; this.spec = spec; this.anchorId = anchorId; }
    }
    private record PendingConfirmation(UUID village,long villageRevision,UUID official,long anchorRevision,BuildingScanner.Range range) {}
    private static final class Desk {
        final BuildingSessionPolicy policy; final BuildingScanner.Role role; final Direction facing;
        UUID nativeContext;
        BuildingScanner.Range range = BuildingScanner.Range.BASIC; UUID scan = BuildingPayloads.NONE;
        List<BuildingPayloads.Choice> choices = List.of(); String feedback = "", pendingFound; long lastAction = -10;
        NativeFoundingObserver.Evidence evidence;
        long evidenceTick=-1;
        PendingConfirmation pendingConfirm;
        String confirmName;
        boolean explicitSelection;
        boolean pendingWorksite;
        BuildingPayloads.MarketLayout layout=BuildingPayloads.MarketLayout.EMPTY;
        BuildingPayloads.Diplomacy diplomacy=BuildingPayloads.Diplomacy.EMPTY; int diplomacyPage;
        Desk(BuildingSessionPolicy policy, BuildingScanner.Role role, Direction facing) { this.policy = policy; this.role = role; this.facing = facing; }
    }
}
