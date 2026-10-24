package de.quest.kingdom;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.*;

/** Shared nontransitive pair agreements. No route, money, arrival or meeting is invented here. */
public final class KingdomDiplomacy {
    public enum Type { TRADE, SUPPLY, FRIENDSHIP }
    public enum Terms { BALANCED, NEEDS_FIRST, CAUTIOUS }
    public enum Status { PROPOSED, PREPARED, SIGNED, PAUSED }
    public record View(UUID id,UUID left,UUID right,Type type,long revision,Status status,Terms terms,
                       UUID from,UUID proposer,UUID acceptor,long leftRevision,long rightRevision) {}
    private final LivingKingdomsState state;
    private final Map<UUID,View> contracts=new HashMap<>();
    private final Map<UUID,CompoundTag> payloads=new HashMap<>();
    private Tag retained=new CompoundTag();
    private boolean opaque;
    KingdomDiplomacy(LivingKingdomsState state) { this.state=state; }
    public boolean readOnly() { return opaque || state.readOnly(); }
    public Optional<KingdomNegotiation.Answer> answer(View deal) {
        if(readOnly() || deal==null || !deal.equals(contracts.get(deal.id))) return Optional.empty();
        var tag=payloads.get(deal.id); if(tag==null || !tag.contains("reaction")) return Optional.empty();
        var need=tag.contains("taskNeed")?de.quest.village.VillageNeed.valueOf(tag.getStringOr("taskNeed","")):null;
        var reaction=KingdomNegotiation.Reaction.valueOf(tag.getStringOr("reaction",""));
        if(reaction==KingdomNegotiation.Reaction.DELIVERY && tag.getBooleanOr("taskDone",false)) reaction=KingdomNegotiation.Reaction.READY;
        return Optional.of(new KingdomNegotiation.Answer(reaction,Terms.valueOf(tag.getStringOr("preferred","")),need,tag.getIntOr("personality",0)));
    }
    boolean respond(View deal,KingdomNegotiation.Answer answer) {
        if(readOnly() || deal==null || answer==null || deal.status!=Status.PROPOSED
                || !deal.equals(contracts.get(deal.id)) || answer.reaction()==KingdomNegotiation.Reaction.DELIVERY && deal.type!=Type.SUPPLY
                || (answer.reaction()==KingdomNegotiation.Reaction.READY || answer.reaction()==KingdomNegotiation.Reaction.DELIVERY)
                    && deal.terms!=answer.preferred()) return false;
        var tag=payloads.getOrDefault(deal.id,new CompoundTag()).copy();
        tag.putString("reaction",answer.reaction().name()); tag.putString("preferred",answer.preferred().name());
        tag.putInt("personality",answer.personality());
        if(answer.need()!=null) tag.putString("taskNeed",answer.need().name()); else tag.remove("taskNeed");
        tag.putBoolean("taskDone",false); payloads.put(deal.id,tag); state.setDirty(); return true;
    }
    /** The native freight adapter has already consumed this delivery and deduplicated the pair receipt. */
    void delivered(UUID home,UUID destination,net.minecraft.world.item.Item item) {
        if(readOnly() || item==null) return;
        for(var deal:List.copyOf(contracts.values())) if(deal.status==Status.PROPOSED && deal.revision<Long.MAX_VALUE
                && (deal.left.equals(home) && deal.right.equals(destination) || deal.right.equals(home) && deal.left.equals(destination))
                && !deal.from.equals(destination)) {
            var answer=answer(deal);
            if(answer.filter(a -> a.reaction()==KingdomNegotiation.Reaction.DELIVERY && a.need()!=null && a.need().matches(item)).isPresent()) {
                var tag=payloads.get(deal.id).copy(); tag.putBoolean("taskDone",true); payloads.put(deal.id,tag); revise(deal,Status.PROPOSED);
            }
        }
    }
    public Optional<View> between(UUID a,UUID b,Type type) {
        return readOnly() || !validPair(a,b) || type==null?Optional.empty():Optional.ofNullable(contracts.get(id(a,b,type)));
    }
    public List<View> contracts(UUID village) {
        return readOnly() || village==null?List.of():contracts.values().stream().filter(v -> v.left.equals(village) || v.right.equals(village))
                .sorted(Comparator.comparing(View::id)).toList();
    }
    public boolean autonomous(View deal) {
        return !readOnly() && deal!=null && deal.equals(contracts.get(deal.id))
                && payloads.getOrDefault(deal.id,new CompoundTag()).getBooleanOr("autonomous",false);
    }
    /** Small local trade only. Political/supply agreements always use the ordinary two-mandate flow. */
    boolean autonomousTrade(UUID a,UUID b,Terms terms) {
        if(readOnly() || !validPair(a,b) || terms==null || state.autonomy().readOnly() || state.relations().readOnly()
                || state.relations().between(a,b).filter(v -> v.deliveries()>=3 && (!v.loyaltyBroken() || v.loyaltyRepaired())).isEmpty()
                || state.autonomy().between(a,b).filter(v -> v.habit()>=2).isEmpty()
                || between(a,b,Type.TRADE).isPresent() || contracts.size()>=32768
                || contracts(a).stream().anyMatch(d -> (d.left.equals(b) || d.right.equals(b)) && d.status==Status.PAUSED)) return false;
        UUID left=a.compareTo(b)<0?a:b,right=left.equals(a)?b:a;
        var deal=new View(id(a,b,Type.TRADE),left,right,Type.TRADE,0,Status.SIGNED,terms,left,null,null,
                state.village(left).orElseThrow().revision(),state.village(right).orElseThrow().revision());
        var tag=new CompoundTag(); tag.putBoolean("autonomous",true);
        tag.putInt("autonomyDeliveries",state.relations().between(a,b).orElseThrow().deliveries());
        payloads.put(deal.id,tag); contracts.put(deal.id,deal); state.setDirty(); return true;
    }
    void validateAutonomousEvidence() {
        if(readOnly()) return;
        for(var deal:contracts.values()) if(autonomous(deal)) {
            int proof=payloads.get(deal.id).getIntOr("autonomyDeliveries",0);
            if(state.relations().readOnly() || proof<3 || state.relations().between(deal.left,deal.right).filter(v -> v.deliveries()>=proof).isEmpty()) {
                opaque=true; contracts.clear(); payloads.clear(); return;
            }
        }
    }
    /** Existing freight board prioritizes actual pending promises, while retaining its own rank/daily gates. */
    public List<de.quest.village.VillageNeed> requestedNeeds(UUID actor) {
        if(readOnly()) return List.of();
        return contracts.values().stream().filter(d -> d.status==Status.PROPOSED && state.canManageVillage(actor,d.from))
                .sorted(Comparator.comparing(View::id)).map(this::answer).flatMap(Optional::stream)
                .filter(a -> a.reaction()==KingdomNegotiation.Reaction.DELIVERY).map(KingdomNegotiation.Answer::need).distinct().limit(3).toList();
    }
    boolean pause(UUID actor,UUID host,UUID other,UUID cause) {
        return pause(actor,host,other,cause,Type.SUPPLY);
    }
    boolean canPause(UUID actor,UUID host,UUID other,Type type) {
        return between(host,other,type).filter(d -> (type==Type.SUPPLY || type==Type.FRIENDSHIP) && d.status==Status.SIGNED
                && d.revision<Long.MAX_VALUE && state.canManageVillage(actor,host)).isPresent();
    }
    boolean pause(UUID actor,UUID host,UUID other,UUID cause,Type type) {
        if(cause==null || cause.equals(BuildingSessionPolicy.NONE) || !canPause(actor,host,other,type)) return false;
        var deal=between(host,other,type).orElseThrow();
        var tag=payloads.getOrDefault(deal.id,new CompoundTag()).copy(); tag.putString("pauseCase",cause.toString()); payloads.put(deal.id,tag);
        tag.remove("met");
        revise(deal,Status.PAUSED); return true;
    }
    boolean prepareRepairMeeting(UUID actor,UUID host,UUID other) {
        var deal=between(host,other,Type.FRIENDSHIP).orElse(null);
        if(deal==null || deal.status!=Status.SIGNED && deal.status!=Status.PAUSED || deal.revision==Long.MAX_VALUE || !state.canManageVillage(actor,host) || !mandates(deal)) return false;
        var tag=payloads.getOrDefault(deal.id,new CompoundTag()).copy(); tag.remove("met"); payloads.put(deal.id,tag);
        if(deal.status==Status.PAUSED) tag.putBoolean("repairMeeting",true);
        if(deal.status==Status.PAUSED) { revise(deal,Status.PAUSED); return true; }
        revise(deal,Status.PREPARED); return true;
    }
    boolean reopen(UUID actor,UUID host,UUID other,UUID cause) {
        return reopen(actor,host,other,cause,Type.SUPPLY);
    }
    boolean canReopen(UUID actor,UUID host,UUID other,UUID cause,Type type) {
        var deal=between(host,other,type).orElse(null);
        if(deal==null || cause==null || deal.status!=Status.PAUSED || deal.revision==Long.MAX_VALUE || !state.canManageVillage(actor,host)
                || !cause.toString().equals(payloads.getOrDefault(deal.id,new CompoundTag()).getStringOr("pauseCase",""))) return false;
        return !payloads.getOrDefault(deal.id,new CompoundTag()).contains("meetingHost");
    }
    boolean reopen(UUID actor,UUID host,UUID other,UUID cause,Type type) {
        if(!canReopen(actor,host,other,cause,type)) return false;
        var deal=between(host,other,type).orElseThrow();
        var tag=payloads.get(deal.id).copy(); tag.remove("pauseCase");
        tag.remove("met"); tag.remove("repairMeeting");
        for(String field:List.of("reaction","preferred","personality","taskNeed","taskDone")) tag.remove(field);
        var next=new View(deal.id,deal.left,deal.right,deal.type,deal.revision+1,Status.PROPOSED,deal.terms,host,actor,null,
                state.village(deal.left).orElseThrow().revision(),state.village(deal.right).orElseThrow().revision());
        contracts.put(deal.id,next); payloads.put(deal.id,tag);
        respond(next,new KingdomNegotiation.Answer(KingdomNegotiation.Reaction.UNKNOWN,next.terms,null,0)); state.setDirty(); return true;
    }
    public Optional<UUID> meetingHost(View contract) {
        if(readOnly() || contract==null || !contract.equals(contracts.get(contract.id))) return Optional.empty();
        var tag=payloads.get(contract.id); if(tag==null || !tag.contains("meetingHost")) return Optional.empty();
        try { return Optional.of(UUID.fromString(tag.getStringOr("meetingHost",""))); } catch(IllegalArgumentException bad) { return Optional.empty(); }
    }
    public boolean met(View contract) {
        return !readOnly() && contract!=null && contract.equals(contracts.get(contract.id))
                && payloads.getOrDefault(contract.id,new CompoundTag()).getBooleanOr("met",false);
    }
    List<KingdomPersonRegistry.View> reconcileMeetings() {
        var retired=new ArrayList<KingdomPersonRegistry.View>();
        if(readOnly()) return List.of();
        for(var deal:List.copyOf(contracts.values())) meetingHost(deal).ifPresent(host -> {
            UUID guest=host.equals(deal.left)?deal.right:deal.left;
            if(deal.revision<Long.MAX_VALUE && (state.anchors().knownDetached(host,BuildingScanner.Role.COUNCIL)
                    || state.anchors().knownDetached(guest,BuildingScanner.Role.COUNCIL))) {
                retired.addAll(state.people().visitors(host).stream().filter(p -> p.village().equals(guest)).toList());
                releaseVisitor(deal,host); clearMeeting(deal.id); revise(deal,repairMeeting(deal)?Status.PAUSED:Status.PREPARED);
            }
        });
        return List.copyOf(retired);
    }
    Optional<View> invite(UUID actor,UUID key,long revision,UUID host,long now) {
        var deal=readOnly()?null:contracts.get(key);
        if(deal==null || deal.type!=Type.FRIENDSHIP || deal.status!=Status.PREPARED && !repairMeeting(deal) || deal.revision!=revision || revision==Long.MAX_VALUE
                || !host.equals(deal.left) && !host.equals(deal.right) || !state.canManageVillage(actor,host) || !mandates(deal)
                || meetingHost(deal).isPresent() || state.people().visitors(host).size()>=4 || !councilReady(host,now)) return Optional.empty();
        UUID guest=host.equals(deal.left)?deal.right:deal.left;
        var local=state.people().ensure(host,KingdomPersonRegistry.Role.LEADER,0);
        var visitor=state.people().ensure(guest,KingdomPersonRegistry.Role.LEADER,0);
        if(local.isEmpty() || visitor.isEmpty() || local.get().presence()==KingdomPersonRegistry.Presence.VISITING
                || state.anchors().official(guest,BuildingScanner.Role.COUNCIL).isEmpty()
                || !state.people().depart(visitor.get().id(),visitor.get().revision(),host)) return Optional.empty();
        var tag=payloads.getOrDefault(key,new CompoundTag()).copy(); tag.putString("meetingHost",host.toString());
        tag.putString("meetingActor",actor.toString()); payloads.put(key,tag);
        return Optional.of(revise(deal,repairMeeting(deal)?Status.PAUSED:Status.PREPARED));
    }
    boolean cancelMeeting(UUID actor,UUID key,long revision,UUID host) {
        var deal=readOnly()?null:contracts.get(key);
        if(deal==null || deal.revision!=revision || revision==Long.MAX_VALUE || !state.canManageVillage(actor,host)
                || meetingHost(deal).filter(host::equals).isEmpty()) return false;
        releaseVisitor(deal,host); clearMeeting(key); revise(deal,repairMeeting(deal)?Status.PAUSED:Status.PREPARED); return true;
    }
    /** Called only after the native adapter observed both actual current bodies on the host floor. */
    boolean finishMeeting(UUID actor,UUID key,long revision,UUID host,UUID localBody,UUID visitorBody,long now) {
        var deal=readOnly()?null:contracts.get(key);
        if(deal==null || deal.status!=Status.PREPARED && !repairMeeting(deal) || deal.revision!=revision || revision==Long.MAX_VALUE
                || meetingHost(deal).filter(host::equals).isEmpty() || !state.canManageVillage(actor,host) || !mandates(deal) || !councilReady(host,now)) return false;
        UUID guest=host.equals(deal.left)?deal.right:deal.left;
        var local=state.people().ensure(host,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        var visitor=state.people().ensure(guest,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        if(local.presence()!=KingdomPersonRegistry.Presence.PRESENT || visitor.presence()!=KingdomPersonRegistry.Presence.VISITING
                || !host.equals(visitor.visit()) || !Objects.equals(local.body(),localBody) || !Objects.equals(visitor.body(),visitorBody)
                || !state.people().available(local.id(),local.generation(),localBody) || !state.people().available(visitor.id(),visitor.generation(),visitorBody)) return false;
        var tag=payloads.get(key).copy(); tag.putBoolean("met",true); payloads.put(key,tag);
        releaseVisitor(deal,host); clearMeeting(key); revise(deal,repairMeeting(deal)?Status.PAUSED:Status.SIGNED); return true;
    }
    boolean repairMeeting(View deal) {
        return deal!=null && deal.status==Status.PAUSED && deal.type==Type.FRIENDSHIP
                && payloads.getOrDefault(deal.id,new CompoundTag()).getBooleanOr("repairMeeting",false);
    }
    private boolean councilReady(UUID village,long now) {
        return state.anchors().official(village,BuildingScanner.Role.COUNCIL).filter(a -> state.anchors().maySpawn(a.id(),now)).isPresent();
    }
    private boolean mandates(View deal) {
        return state.canManageVillage(deal.proposer,deal.from)
                && state.canManageVillage(deal.acceptor,deal.from.equals(deal.left)?deal.right:deal.left);
    }
    private void releaseVisitor(View deal,UUID host) {
        UUID guest=host.equals(deal.left)?deal.right:deal.left;
        state.people().visitors(host).stream().filter(p -> p.village().equals(guest)).findFirst()
                .ifPresent(p -> state.people().endVisit(p.id(),p.revision(),false));
    }
    private void clearMeeting(UUID key) { var tag=payloads.get(key); tag.remove("meetingHost"); tag.remove("meetingActor"); }
    private View revise(View old,Status status) {
        var next=new View(old.id,old.left,old.right,old.type,old.revision+1,status,old.terms,old.from,old.proposer,old.acceptor,old.leftRevision,old.rightRevision);
        contracts.put(old.id,next); state.setDirty(); return next;
    }
    /** Caller must additionally restrict a native screen to actually known contacts. No client evidence is consumed. */
    Optional<View> offer(UUID actor,UUID from,UUID to,long fromRevision,long toRevision,Type type,long expectedRevision,Terms terms) {
        if(readOnly() || !validPair(from,to) || type==null || terms==null || !state.canManageVillage(actor,from)
                || state.relations().readOnly() || type==Type.FRIENDSHIP && (state.relations().distrusts(actor,to) || state.relations().distrusts(actor,from))
                || state.village(from).filter(v -> v.revision()==fromRevision).isEmpty()
                || state.village(to).filter(v -> v.revision()==toRevision).isEmpty()) return Optional.empty();
        var key=id(from,to,type); var old=contracts.get(key);
        if(old==null && (expectedRevision!=-1 || contracts.size()>=32768)
                || old!=null && (old.revision!=expectedRevision || old.revision==Long.MAX_VALUE || old.status!=Status.PROPOSED)) return Optional.empty();
        UUID left=from.compareTo(to)<0?from:to, right=left.equals(from)?to:from;
        var next=new View(key,left,right,type,old==null?0:old.revision+1,Status.PROPOSED,terms,from,actor,null,
                left.equals(from)?fromRevision:toRevision,right.equals(from)?fromRevision:toRevision);
        contracts.put(key,next);
        var tag=payloads.getOrDefault(key,new CompoundTag()).copy();
        for(String field:List.of("reaction","preferred","personality","taskNeed","taskDone")) tag.remove(field);
        payloads.put(key,tag); state.setDirty(); return Optional.of(next);
    }
    Optional<View> accept(UUID actor,UUID id,long expectedRevision) {
        var offer=readOnly()?null:contracts.get(id);
        if(offer==null || offer.status!=Status.PROPOSED || offer.revision!=expectedRevision || expectedRevision==Long.MAX_VALUE
                || state.relations().readOnly() || offer.type==Type.FRIENDSHIP && (state.relations().distrusts(offer.proposer,offer.left)
                || state.relations().distrusts(offer.proposer,offer.right) || state.relations().distrusts(actor,offer.left) || state.relations().distrusts(actor,offer.right))
                || !state.canManageVillage(offer.proposer,offer.from)
                || !state.canManageVillage(actor,offer.from.equals(offer.left)?offer.right:offer.left)
                || state.village(offer.left).filter(v -> v.revision()==offer.leftRevision).isEmpty()
                || state.village(offer.right).filter(v -> v.revision()==offer.rightRevision).isEmpty()) return Optional.empty();
        if(answer(offer).filter(a -> a.reaction()!=KingdomNegotiation.Reaction.READY).isPresent()) return Optional.empty();
        var next=new View(offer.id,offer.left,offer.right,offer.type,offer.revision+1,
                offer.type==Type.FRIENDSHIP?Status.PREPARED:Status.SIGNED,offer.terms,offer.from,offer.proposer,actor,offer.leftRevision,offer.rightRevision);
        contracts.put(id,next); state.setDirty(); return Optional.of(next);
    }
    private static boolean validPair(UUID a,UUID b) {
        return a!=null && b!=null && !a.equals(b) && !a.equals(BuildingSessionPolicy.NONE) && !b.equals(BuildingSessionPolicy.NONE);
    }
    private static UUID id(UUID a,UUID b,Type type) {
        UUID left=a.compareTo(b)<0?a:b, right=left.equals(a)?b:a;
        return UUID.nameUUIDFromBytes(("living-kingdoms:pair:"+left+":"+right+":"+type.name()).getBytes(StandardCharsets.UTF_8));
    }
    void load(Tag payload) {
        if(payload==null) return; retained=payload.copy();
        if(!(payload instanceof CompoundTag root) || !(root.get("schema") instanceof IntTag) || root.getIntOr("schema",0)!=1
                || !(root.get("contracts") instanceof ListTag rows) || rows.size()>32768) { opaque=true; return; }
        try {
            for(var value:rows) {
                if(!(value instanceof CompoundTag tag) || !(tag.get("revision") instanceof LongTag)
                        || !(tag.get("leftRevision") instanceof LongTag) || !(tag.get("rightRevision") instanceof LongTag)) throw new IllegalArgumentException();
                var key=UUID.fromString(tag.getStringOr("id","")); var left=UUID.fromString(tag.getStringOr("left",""));
                var right=UUID.fromString(tag.getStringOr("right","")); var from=UUID.fromString(tag.getStringOr("from",""));
                boolean autonomous=tag.getBooleanOr("autonomous",false);
                if(tag.contains("autonomous") && (!(tag.get("autonomous") instanceof ByteTag) || !autonomous)) throw new IllegalArgumentException();
                var proposer=autonomous?null:UUID.fromString(tag.getStringOr("proposer","")); var type=Type.valueOf(tag.getStringOr("type",""));
                var status=Status.valueOf(tag.getStringOr("status",""));
                var acceptor=status==Status.PROPOSED || autonomous?null:UUID.fromString(tag.getStringOr("acceptor",""));
                if(autonomous && (type!=Type.TRADE || status!=Status.SIGNED || tag.contains("proposer") || tag.contains("acceptor")
                        || !(tag.get("autonomyDeliveries") instanceof IntTag) || tag.getIntOr("autonomyDeliveries",0)<3
                        || tag.contains("reaction") || tag.contains("meetingHost") || tag.contains("met") || !from.equals(left))) throw new IllegalArgumentException();
                if(!autonomous && tag.contains("autonomyDeliveries")) throw new IllegalArgumentException();
                var record=new View(key,left,right,type,tag.getLongOr("revision",-1),status,Terms.valueOf(tag.getStringOr("terms","")),
                        from,proposer,acceptor,tag.getLongOr("leftRevision",-1),tag.getLongOr("rightRevision",-1));
                if(status==Status.PAUSED && (type==Type.TRADE || !tag.contains("pauseCase")) || tag.contains("pauseCase") && status!=Status.PAUSED)
                    throw new IllegalArgumentException();
                if(tag.contains("pauseCase") && UUID.fromString(tag.getStringOr("pauseCase","")).equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException();
                if(tag.contains("reaction")) {
                    var reaction=KingdomNegotiation.Reaction.valueOf(tag.getStringOr("reaction",""));
                    var preferred=Terms.valueOf(tag.getStringOr("preferred",""));
                    if(!(tag.get("personality") instanceof IntTag) || tag.getIntOr("personality",-1)<0 || tag.getIntOr("personality",-1)>3
                            || !(tag.get("taskDone") instanceof ByteTag)) throw new IllegalArgumentException();
                    if(tag.contains("taskNeed")) de.quest.village.VillageNeed.valueOf(tag.getStringOr("taskNeed",""));
                    if(reaction==KingdomNegotiation.Reaction.DELIVERY && (type!=Type.SUPPLY || !tag.contains("taskNeed"))) throw new IllegalArgumentException();
                    if((reaction==KingdomNegotiation.Reaction.READY || reaction==KingdomNegotiation.Reaction.DELIVERY) && record.terms!=preferred
                            || tag.getBooleanOr("taskDone",false) && reaction!=KingdomNegotiation.Reaction.DELIVERY
                            || status!=Status.PROPOSED && reaction!=KingdomNegotiation.Reaction.READY
                                && (reaction!=KingdomNegotiation.Reaction.DELIVERY || !tag.getBooleanOr("taskDone",false))) throw new IllegalArgumentException();
                } else if(tag.contains("taskNeed") || tag.contains("preferred") || tag.contains("taskDone") || tag.contains("personality")) throw new IllegalArgumentException();
                if(tag.contains("met") && !(tag.get("met") instanceof ByteTag)
                        || tag.getBooleanOr("met",false) && (type!=Type.FRIENDSHIP || status!=Status.SIGNED && !(status==Status.PAUSED && tag.getBooleanOr("repairMeeting",false)))
                        || tag.contains("meetingHost")!=tag.contains("meetingActor")) throw new IllegalArgumentException();
                if(tag.contains("repairMeeting") && (!(tag.get("repairMeeting") instanceof ByteTag) || !tag.getBooleanOr("repairMeeting",false)
                        || type!=Type.FRIENDSHIP || status!=Status.PAUSED)) throw new IllegalArgumentException();
                if(tag.contains("meetingHost")) {
                    var host=UUID.fromString(tag.getStringOr("meetingHost","")); var actor=UUID.fromString(tag.getStringOr("meetingActor",""));
                    if(type!=Type.FRIENDSHIP || status!=Status.PREPARED && !(status==Status.PAUSED && tag.getBooleanOr("repairMeeting",false)) || !host.equals(left) && !host.equals(right)
                            || actor.equals(BuildingSessionPolicy.NONE)) throw new IllegalArgumentException();
                }
                if(!validPair(left,right) || left.compareTo(right)>=0 || !key.equals(id(left,right,type)) || record.revision<0
                        || record.leftRevision<0 || record.rightRevision<0 || !from.equals(left) && !from.equals(right)
                        || proposer!=null && proposer.equals(BuildingSessionPolicy.NONE) || acceptor!=null && acceptor.equals(BuildingSessionPolicy.NONE)
                        || status==Status.SIGNED && type==Type.FRIENDSHIP && !tag.getBooleanOr("met",false) || status==Status.PREPARED && type!=Type.FRIENDSHIP
                        || state.village(left).isEmpty() || state.village(right).isEmpty() || contracts.putIfAbsent(key,record)!=null) throw new IllegalArgumentException();
                payloads.put(key,tag.copy());
            }
        } catch(RuntimeException malformed) { contracts.clear(); payloads.clear(); opaque=true; }
    }
    Tag toNbt() {
        if(opaque) return retained.copy(); var root=retained instanceof CompoundTag tag?tag.copy():new CompoundTag(); root.putInt("schema",1);
        var rows=new ListTag(); contracts.values().stream().sorted(Comparator.comparing(View::id)).forEach(v -> {
            var tag=payloads.getOrDefault(v.id,new CompoundTag()).copy(); tag.putString("id",v.id.toString()); tag.putString("left",v.left.toString());
            tag.putString("right",v.right.toString()); tag.putString("type",v.type.name()); tag.putLong("revision",v.revision);
            tag.putString("status",v.status.name()); tag.putString("terms",v.terms.name()); tag.putString("from",v.from.toString());
            if(v.proposer!=null) tag.putString("proposer",v.proposer.toString()); else tag.remove("proposer");
            if(v.acceptor!=null) tag.putString("acceptor",v.acceptor.toString()); else tag.remove("acceptor");
            tag.putLong("leftRevision",v.leftRevision); tag.putLong("rightRevision",v.rightRevision); rows.add(tag);
        }); root.put("contracts",rows); return root;
    }
}
