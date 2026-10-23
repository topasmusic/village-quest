package de.quest.kingdom;

import de.quest.entity.KingdomNpcEntity;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Native callbacks always validate the canonical generation before exposing a person. */
public final class KingdomNpcService {
    private KingdomNpcService() {}
    public static void register() {
        KingdomNpcController.register();
        ServerEntityEvents.ENTITY_LOAD.register(KingdomNpcService::loaded);
        ServerEntityEvents.ENTITY_UNLOAD.register(KingdomNpcService::unloaded);
    }
    private static void loaded(Entity entity,ServerLevel world) {
        if (!(entity instanceof KingdomNpcEntity npc) || npc.isPreview()) return;
        var registry=LivingKingdomsState.get(world.getServer()).people();
        npc.setNoAi(true);
        if(!reactivate(world,npc)) {
            // Known superseded generations from virtual travel are retired, not allowed to linger at home.
            npc.identity().ticket(npc.getUUID()).ifPresent(ticket -> registry.person(ticket.person()).filter(p ->
                    p.generation()!=ticket.generation() || !java.util.Objects.equals(p.body(),ticket.body())).ifPresent(p -> npc.discard()));
        }
    }
    static boolean reactivate(ServerLevel world,KingdomNpcEntity npc) {
        var state=LivingKingdomsState.get(world.getServer()); var registry=state.people();
        var ticket=npc.identity().ticket(npc.getUUID());
        if(ticket.isPresent() && state.tavernGuests().readOnly() && registry.person(ticket.get().person()).filter(p -> p.guest()>=0).isPresent()) return false;
        if(ticket.isEmpty() || registry.person(ticket.get().person()).flatMap(person -> state.village(person.village()))
                .filter(town -> town.legacyKey().dimension().equals(world.dimension().identifier().toString())).isEmpty()
                || !registry.markLoaded(ticket.get().person(),ticket.get().generation(),ticket.get().body())) return false;
        var person=registry.person(ticket.get().person()).orElseThrow();
        if(person.guest()>=0 && state.tavernGuests().active(person.village())!=person.guest()) {
            registry.dismiss(person.id(),person.generation(),person.body()); npc.discard(); return false;
        }
        npc.setNoAi(false); npc.refreshAppearance(registry.person(ticket.get().person()).orElseThrow()); return true;
    }
    private static void unloaded(Entity entity,ServerLevel world) {
        if (!(entity instanceof KingdomNpcEntity npc) || npc.isPreview()) return;
        npc.identity().ticket(npc.getUUID()).ifPresent(ticket -> LivingKingdomsState.get(world.getServer()).people()
                .unloaded(ticket.person(),ticket.generation(),ticket.body(),npc.blockPosition()));
    }
    public static void died(ServerLevel world,KingdomNpcEntity npc) {
        available(world,npc).filter(p -> p.guest()>=0).ifPresent(p -> LivingKingdomsState.get(world.getServer()).tavernGuests().end(p.village(),world.getOverworldClockTime()));
        npc.identity().ticket(npc.getUUID()).ifPresent(ticket -> LivingKingdomsState.get(world.getServer()).people()
                .died(ticket.person(),ticket.generation(),ticket.body()));
    }
    public static Optional<KingdomPersonRegistry.View> available(ServerLevel world,KingdomNpcEntity npc) {
        var state=LivingKingdomsState.get(world.getServer()); var registry=state.people();
        return npc.identity().ticket(npc.getUUID()).filter(ticket -> registry.available(ticket.person(),ticket.generation(),ticket.body()))
                .flatMap(ticket -> registry.person(ticket.person())).filter(person -> person.guest()<0 || state.tavernGuests().active(person.village())==person.guest()).filter(person -> state.village(person.village())
                        .filter(v -> v.legacyKey().dimension().equals(world.dimension().identifier().toString())).isPresent());
    }
    public static UUID reputationIdentity(UUID person,long generation) {
        return UUID.nameUUIDFromBytes(("kingdom-person/"+person+"/"+generation).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static boolean tradeAllowed(ServerLevel world,ServerPlayer actor,UUID person,UUID village) {
        if(!de.quest.reputation.SocialReputationService.enabled()) return true;
        var town=LivingKingdomsState.get(world.getServer()).village(village);
        if(town.isEmpty()) return false;
        var quests=de.quest.data.QuestState.get(world.getServer()); var social=quests.getPlayerData(actor.getUUID()).socialReputation();
        var identity=LivingKingdomsState.get(world.getServer()).people().person(person).map(p -> reputationIdentity(p.id(),p.generation())).orElse(person);
        return !social.traderWarningActive(identity,quests.socialServerTick()) && de.quest.reputation.SocialReputationRules.decision(
                de.quest.reputation.SocialReputationRules.ServiceKind.CARAVAN_TRADE,social,town.get().legacyKey()).allowed();
    }
    public static boolean refuses(ServerLevel world,ServerPlayer actor,KingdomNpcEntity npc) {
        var person=available(world,npc); if(person.isEmpty() || !de.quest.reputation.SocialReputationService.enabled()) return false;
        var quests=de.quest.data.QuestState.get(world.getServer()); var p=person.get();
        if(quests.getPlayerData(actor.getUUID()).socialReputation().traderWarningActive(reputationIdentity(p.id(),p.generation()),quests.socialServerTick())) {
            NpcSpeechService.privateRefusal(actor,npc,p.id()); return true;
        }
        return false;
    }
    public static void interact(ServerLevel world,ServerPlayer actor,KingdomNpcEntity npc) {
        if (!npc.isAlive() || npc.isSleeping() || actor.distanceToSqr(npc)>64) return;
        var person=available(world,npc); if (person.isEmpty()) return;
        var view=person.get();
        if(view.guest()>=0) { KingdomTavernGuestService.interact(world,actor,npc,view); return; }
        var state=LivingKingdomsState.get(world.getServer());
        npc.getLookControl().setLookAt(actor.getX(),actor.getEyeY(),actor.getZ());
        if(refuses(world,actor,npc)) return;
        if(view.role()==KingdomPersonRegistry.Role.MARKET_TRADER) {
            UUID host=view.visit()==null?view.village():view.visit();
            if(!tradeAllowed(world,actor,view.id(),host)) {
                var line=Component.translatable("message.village-quest.market.case_unresolved",view.name());
                NpcSpeechService.privateLine(actor,npc,line,line); return;
            }
            if(KingdomMarketUiService.open(world,actor,npc)) marketGreeting(world,actor,npc,view);
            return;
        }
        var home=state.village(view.village()).orElseThrow();
        var quests=de.quest.data.QuestState.get(world.getServer()); var data=quests.getPlayerData(actor.getUUID());
        var life=de.quest.village.VillageLifeState.get(world.getServer()).status(home.legacyKey());
        var line=Component.translatable(KingdomStaffDialogue.key(view,life,KingdomStaffDialogue.seen(data,view.id())),view.name(),home.name());
        var artifactLine=ArtifactJourneyService.dialogue(world,actor,view);
        if(artifactLine!=null) line=artifactLine.copy();
        else if(view.visit()==null && !npc.isSleeping() && actor.hasLineOfSight(npc)) {
            var information=switch(view.role()) {
                case INNKEEPER -> KingdomStaffInformation.inn(state,view.village(),world.getServer().overworld().getGameTime(),p -> {
                    var body=world.getEntity(p.body());
                    if(!(body instanceof KingdomNpcEntity trader) || !trader.isAlive() || trader.isSleeping()
                            || !world.hasChunkAt(trader.blockPosition()) || !world.isPositionEntityTicking(trader.blockPosition())
                            || available(world,trader).filter(current -> current.id().equals(p.id())).isEmpty()) return false;
                    return state.marketDays().place(view.village(),p.id(),world.getServer().overworld().getGameTime())
                            .filter(place -> trader.blockPosition().distSqr(place.position())<=64).isPresent();
                });
                case STOREKEEPER -> KingdomStaffInformation.store(state,actor.getUUID(),view.village(),
                        de.quest.caravan.KingdomRouteConnections.read(state,de.quest.village.VillageLifeState.get(world.getServer()),actor.getUUID(),data));
                default -> Optional.<Component>empty();
            };
            if(information.isPresent()) line=line.copy().append(" ").append(information.get());
        }
        boolean spoken=NpcSpeechService.privateLine(actor,npc,line,line);
        if(spoken && artifactLine==null && !npc.isSleeping() && actor.hasLineOfSight(npc)
                && (view.role()==KingdomPersonRegistry.Role.INNKEEPER || view.role()==KingdomPersonRegistry.Role.STOREKEEPER))
            actor.sendSystemMessage(Component.translatable("message.village-quest.kingdom.staff_info.details")
                    .withStyle(s -> s.withColor(0x78B8CC).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/vq routes"))),false);
        if(spoken && artifactLine==null && actor.hasLineOfSight(npc) && KingdomStaffDialogue.remember(data,view.id())) quests.setDirty();
        var anchor=state.anchors().official(view.village(),view.role().anchor);
        if (!spoken || view.presence()==KingdomPersonRegistry.Presence.VISITING || anchor.isEmpty()
                || !state.anchors().mayContinue(anchor.get().id(),world.getServer().overworld().getGameTime())
                || !actor.hasLineOfSight(npc)) return;
        de.quest.quest.special.FriendsQuestService.residentContact(world,actor,view.village(),npc.blockPosition());
        KingdomCrisisService.talk(actor,npc,view);
        KingdomAutonomyService.talk(actor,npc,view);
        if(view.role()==KingdomPersonRegistry.Role.LEADER) KingdomAttackService.talk(actor,npc,view.village());
        KingdomStoryProgress.Deed deed=switch(view.role()) {
            case LEADER -> KingdomStoryProgress.Deed.MEET_LEADER;
            case INNKEEPER -> KingdomStoryProgress.Deed.MEET_INNKEEPER;
            case STOREKEEPER -> KingdomStoryProgress.Deed.MEET_STOREKEEPER;
            case MARKET_TRADER -> null;
        };
        if (deed!=null) KingdomStoryService.recordConfirmed(world,actor,view.village(),deed,"dialogue:"+view.id());
    }
    private static void marketGreeting(ServerLevel world,ServerPlayer actor,KingdomNpcEntity npc,KingdomPersonRegistry.View person) {
        var state=LivingKingdomsState.get(world.getServer()); var quests=de.quest.data.QuestState.get(world.getServer()); var data=quests.getPlayerData(actor.getUUID());
        var home=state.village(person.village()).orElseThrow(); UUID host=person.visit()==null?person.village():person.visit();
        var remembered=new java.util.ArrayList<>(java.util.Arrays.asList(data.getTradeRouteString("kingdoms.market.familiar").split(",")));
        boolean again=remembered.contains(person.id().toString()); remembered.removeIf(id -> id.isBlank() || id.equals(person.id().toString()));
        remembered.addFirst(person.id().toString()); data.setTradeRouteString("kingdoms.market.familiar",String.join(",",remembered.stream().limit(8).toList()));
        var profile=state.marketHomes().profile(home.id());
        if(profile.isPresent()) de.quest.shrine.VillageContactService.establish(data,home,profile.get().type());
        quests.setDirty();
        String variant=state.marketTravel().evening(host,person.id())?"evening":state.relations().distrusts(actor.getUUID(),home.id())?"careful"
                :!host.equals(home.id()) && state.relations().between(home.id(),host).filter(r -> r.deliveries()>0).isPresent()?"freight"
                :de.quest.reputation.SocialReputationService.enabled() && data.socialReputation().localTrust(home.legacyKey())>=40?"trusted"
                :(again?"again.":"hello.")+person.personality();
        var line=Component.translatable("message.village-quest.market_travel."+variant,person.name(),home.name());
        NpcSpeechService.privateLine(actor,npc,line,line);
    }
}
