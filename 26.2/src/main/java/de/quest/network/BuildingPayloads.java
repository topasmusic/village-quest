package de.quest.network;

import de.quest.kingdom.BuildingScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Small private snapshots. Client actions carry no building/resident evidence or management permissions. */
public final class BuildingPayloads {
    public static final UUID NONE = new UUID(0, 0);
    private BuildingPayloads() {}
    public enum Kind { RECHECK, EXTEND, SELECT, CONFIRM, FOUND, RENAME, RELEASE, MANDATE, CLOSE, REBIND, PLACE_ASSIGN, PLACE_REMOVE, PROJECT, DIPLOMACY_PAGE, DIPLOMACY_OFFER, DIPLOMACY_ACCEPT, DIPLOMACY_MEETING, CRISIS_CHOICE, WORKSITE_CHECK }
    public enum Binding { UNREGISTERED, REGISTERED, CHANGED, ELSEWHERE }
    public record Deal(long revision,int status,int terms,boolean incoming,String reaction,int preferred,int personality,String need) {
        public Deal(long revision,int status,int terms,boolean incoming) { this(revision,status,terms,incoming,"none",0,0,"none"); }
        public Deal { if(revision< -1 || status< -1 || status>3 || terms<0 || terms>2 || (revision==-1)!=(status==-1)
                || !java.util.Set.of("none","ready","counter","delivery","unknown").contains(reaction)
                || preferred<0 || preferred>2 || personality<0 || personality>3 || need==null
                || !need.equals("none") && java.util.Arrays.stream(de.quest.village.VillageNeed.values()).noneMatch(n -> n.key().equals(need)))
            throw new IllegalArgumentException("Invalid agreement view"); }
    }
    public record Relation(String state,String trend,List<String> causes,String repair,String need,String repairVillage) {
        public Relation(String state,String trend,List<String> causes,String repair,String need) { this(state,trend,causes,repair,need,""); }
        public static final Relation UNKNOWN=new Relation("unknown","unknown",List.of(),"unknown","none");
        public Relation {
            text(repairVillage,96);
            if(!java.util.Set.of("unknown","acquainted","trading","cooperating","friendly","strained").contains(state)
                    || !java.util.Set.of("unknown","steady","improving","strained","rebuilding").contains(trend)
                    || !java.util.Set.of("unknown","none","negotiate","loyalty","attack_apology","attack_freight").contains(repair)
                    || need==null || !need.equals("none") && java.util.Arrays.stream(de.quest.village.VillageNeed.values()).noneMatch(n -> n.key().equals(need)))
                throw new IllegalArgumentException("Invalid relationship explanation");
            bounded(causes.size(),14); causes=List.copyOf(causes);
            if(causes.stream().distinct().count()!=causes.size() || causes.stream().anyMatch(c -> !java.util.Set.of(
                    "delivery","aid","loyalty","reconciliation","attack","trade","supply","friendship","prepared","paused",
                    "reliable","initiative_aid","initiative_bond","autonomous").contains(c))
                    || repair.equals("attack_freight")==need.equals("none")) throw new IllegalArgumentException("Invalid relationship cause/need");
        }
    }
    public record Partner(UUID village,String name,long revision,List<Deal> deals,String connection,boolean meeting,Relation relation) {
        public Partner(UUID village,String name,long revision,List<Deal> deals,String connection,boolean meeting) { this(village,name,revision,deals,connection,meeting,Relation.UNKNOWN); }
        public Partner(UUID village,String name,long revision,List<Deal> deals) { this(village,name,revision,deals,"none",false); }
        public Partner { Objects.requireNonNull(relation); Objects.requireNonNull(village); text(name,96); if(revision<0 || deals.size()!=3
                || !java.util.Set.of("none","active","player_paused","survey_in_progress","incident","destination_abandoned","hub_unavailable","recovering_settlement","anchor_moved").contains(connection)) throw new IllegalArgumentException("Invalid partner"); deals=List.copyOf(deals); }
    }
    public record Crisis(UUID id,long revision,String host,String other,String phase,String need,int remaining,int heard,int personality,boolean competing,String kind) {
        public Crisis(UUID id,long revision,String host,String other,String phase,String need,int remaining,int heard,int personality,boolean competing) {
            this(id,revision,host,other,phase,need,remaining,heard,personality,competing,"supply");
        }
        public static final Crisis EMPTY=new Crisis(NONE,0,"","","none","none",0,0,0,false);
        public Crisis {
            Objects.requireNonNull(id); text(host,96); text(other,96);
            if(!java.util.Set.of("supply","loyalty").contains(kind) || kind.equals("loyalty") && !competing
                    || revision<0 || remaining<0 || remaining>48000 || heard<0 || heard>3 || personality<0 || personality>3
                    || !java.util.Set.of("none","open","aid","withdrawn","reconciling","ready","resolved","lapsed").contains(phase)
                    || phase.equals("none")!=id.equals(NONE) || need==null || phase.equals("none")!=need.equals("none")
                    || !need.equals("none") && java.util.Arrays.stream(de.quest.village.VillageNeed.values()).noneMatch(n -> n.key().equals(need)))
                throw new IllegalArgumentException("Invalid private crisis view");
        }
    }
    public record Diplomacy(int page,int pages,List<Partner> partners,boolean readOnly,Crisis crisis) {
        public Diplomacy(int page,int pages,List<Partner> partners,boolean readOnly) { this(page,pages,partners,readOnly,Crisis.EMPTY); }
        public static final Diplomacy EMPTY=new Diplomacy(0,1,List.of(),true);
        public Diplomacy { Objects.requireNonNull(crisis); if(page<0 || pages<1 || pages>32 || page>=pages) throw new IllegalArgumentException("Invalid contact page"); bounded(partners.size(),32); partners=List.copyOf(partners); }
    }
    public record Recognition(long revision,int level,List<String> missing,boolean readOnly,List<String> missingConversations) {
        public Recognition(long revision,int level,List<String> missing,boolean readOnly) {
            this(revision,level,missing,readOnly,List.of("leader","innkeeper","storekeeper"));
        }
        public static final Recognition EMPTY=new Recognition(0,0,List.of(),true);
        public Recognition {
            if(revision<0) throw new IllegalArgumentException("Invalid recognition revision");
            missingConversations=List.copyOf(missingConversations);
            if(missingConversations.size()>3 || missingConversations.stream().distinct().count()!=missingConversations.size()
                    || missingConversations.stream().anyMatch(k -> !List.of("leader","innkeeper","storekeeper").contains(k)))
                throw new IllegalArgumentException("Invalid conversation roles");
            bounded(level,5); bounded(missing.size(),17); missing=List.copyOf(missing);
            for(var key:missing) if(!key.matches("[a-z_]{1,24}")) throw new IllegalArgumentException("Invalid recognition requirement");
        }
    }
    public record Stand(int slot,BlockPos position,BlockPos counter,BlockPos container,boolean usable) {
        public Stand {
            bounded(slot,4); Objects.requireNonNull(position); Objects.requireNonNull(counter); Objects.requireNonNull(container);
            position=position.immutable(); counter=counter.immutable(); container=container.immutable();
        }
    }
    public record Travel(UUID person,String name,String home,String host,String phase,int remaining,boolean paused,boolean late) {
        public Travel {
            Objects.requireNonNull(person); text(name,48); text(home,96); text(host,96);
            if(!java.util.Set.of("outbound","stay","evening","returning","home").contains(phase)) throw new IllegalArgumentException("Invalid journey phase");
            bounded(remaining,4400);
        }
    }
    public record MarketLayout(long revision,List<Stand> assigned,List<Stand> candidates,List<Travel> travel) {
        public MarketLayout(long revision,List<Stand> assigned,List<Stand> candidates) { this(revision,assigned,candidates,List.of()); }
        public static final MarketLayout EMPTY=new MarketLayout(0,List.of(),List.of());
        public MarketLayout {
            if(revision<0) throw new IllegalArgumentException("Invalid market place revision");
            bounded(assigned.size(),5); bounded(candidates.size(),32); assigned=List.copyOf(assigned); candidates=List.copyOf(candidates);
            bounded(travel.size(),5); travel=List.copyOf(travel);
            if(travel.stream().map(Travel::person).distinct().count()!=travel.size()) throw new IllegalArgumentException("Duplicate traveler");
            if(assigned.stream().map(Stand::slot).distinct().count()!=assigned.size()) throw new IllegalArgumentException("Duplicate market place");
        }
    }
    public record Row(BuildingScanner.Check check, boolean passed, int found, int required, List<BlockPos> targets) {
        public Row {
            Objects.requireNonNull(check); bounded(found, 4096); bounded(required, 4096); bounded(targets.size(), 64);
            targets = targets.stream().map(BlockPos::immutable).toList();
        }
    }
    public record Choice(UUID id, String name) {
        public Choice { Objects.requireNonNull(id); text(name, 96); }
    }
    public record View(UUID session, long revision, boolean open, BlockPos position, BuildingScanner.Role role,
                       BuildingScanner.Range range, BuildingScanner.Status status, UUID selectedVillage,
                       long villageRevision, UUID official, long anchorRevision, boolean manageable, boolean canFound,
                       int adults, int beds, boolean job, String name, List<Row> rows, List<Choice> villages,
                       String feedback,MarketLayout marketLayout,Recognition recognition,Diplomacy diplomacy,boolean foundingObserved,Binding binding) implements CustomPacketPayload {
        public View(UUID session,long revision,boolean open,BlockPos position,BuildingScanner.Role role,BuildingScanner.Range range,
                    BuildingScanner.Status status,UUID selectedVillage,long villageRevision,UUID official,long anchorRevision,
                    boolean manageable,boolean canFound,int adults,int beds,boolean job,String name,List<Row> rows,List<Choice> villages,
                    String feedback,MarketLayout marketLayout,Recognition recognition,Diplomacy diplomacy,boolean foundingObserved) {
            this(session,revision,open,position,role,range,status,selectedVillage,villageRevision,official,anchorRevision,manageable,canFound,
                    adults,beds,job,name,rows,villages,feedback,marketLayout,recognition,diplomacy,foundingObserved,
                    official.equals(NONE)?Binding.UNREGISTERED:Binding.REGISTERED);
        }
        public View(UUID session,long revision,boolean open,BlockPos position,BuildingScanner.Role role,BuildingScanner.Range range,
                    BuildingScanner.Status status,UUID selectedVillage,long villageRevision,UUID official,long anchorRevision,
                    boolean manageable,boolean canFound,int adults,int beds,boolean job,String name,List<Row> rows,List<Choice> villages,
                    String feedback,MarketLayout marketLayout,Recognition recognition,Diplomacy diplomacy) {
            this(session,revision,open,position,role,range,status,selectedVillage,villageRevision,official,anchorRevision,
                    manageable,canFound,adults,beds,job,name,rows,villages,feedback,marketLayout,recognition,diplomacy,true);
        }
        public View(UUID session,long revision,boolean open,BlockPos position,BuildingScanner.Role role,BuildingScanner.Range range,
                    BuildingScanner.Status status,UUID selectedVillage,long villageRevision,UUID official,long anchorRevision,
                    boolean manageable,boolean canFound,int adults,int beds,boolean job,String name,List<Row> rows,List<Choice> villages,
                    String feedback,MarketLayout marketLayout,Recognition recognition) {
            this(session,revision,open,position,role,range,status,selectedVillage,villageRevision,official,anchorRevision,
                    manageable,canFound,adults,beds,job,name,rows,villages,feedback,marketLayout,recognition,Diplomacy.EMPTY);
        }
        public View(UUID session,long revision,boolean open,BlockPos position,BuildingScanner.Role role,BuildingScanner.Range range,
                    BuildingScanner.Status status,UUID selectedVillage,long villageRevision,UUID official,long anchorRevision,
                    boolean manageable,boolean canFound,int adults,int beds,boolean job,String name,List<Row> rows,List<Choice> villages,
                    String feedback,MarketLayout marketLayout) {
            this(session,revision,open,position,role,range,status,selectedVillage,villageRevision,official,anchorRevision,
                    manageable,canFound,adults,beds,job,name,rows,villages,feedback,marketLayout,Recognition.EMPTY);
        }
        public View(UUID session,long revision,boolean open,BlockPos position,BuildingScanner.Role role,BuildingScanner.Range range,
                    BuildingScanner.Status status,UUID selectedVillage,long villageRevision,UUID official,long anchorRevision,
                    boolean manageable,boolean canFound,int adults,int beds,boolean job,String name,List<Row> rows,List<Choice> villages,String feedback) {
            this(session,revision,open,position,role,range,status,selectedVillage,villageRevision,official,anchorRevision,
                    manageable,canFound,adults,beds,job,name,rows,villages,feedback,MarketLayout.EMPTY);
        }
        public View {
            Objects.requireNonNull(binding);
            Objects.requireNonNull(diplomacy);
            Objects.requireNonNull(recognition);
            Objects.requireNonNull(marketLayout);
            Objects.requireNonNull(session); Objects.requireNonNull(position); Objects.requireNonNull(role);
            Objects.requireNonNull(range); Objects.requireNonNull(status); Objects.requireNonNull(selectedVillage); Objects.requireNonNull(official);
            if(binding!=Binding.UNREGISTERED && (official.equals(NONE) || selectedVillage.equals(NONE))) throw new IllegalArgumentException("Registered building without village/anchor");
            if (revision < 0 || villageRevision < -1 || anchorRevision < -1) throw new IllegalArgumentException("Invalid building revision");
            bounded(adults, 4096); bounded(beds, 4096); text(name, 96); text(feedback, 128);
            bounded(rows.size(), 13); bounded(villages.size(), 8); rows = List.copyOf(rows); villages = List.copyOf(villages);
            position = position.immutable();
        }
        public static final Type<View> ID = new Type<>(Identifier.fromNamespaceAndPath("village-quest", "building_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((buf, view) -> {
            buf.writeUUID(view.session); buf.writeVarLong(view.revision); buf.writeBoolean(view.open); buf.writeBlockPos(view.position);
            buf.writeEnum(view.role); buf.writeEnum(view.range); buf.writeEnum(view.status); buf.writeUUID(view.selectedVillage);
            buf.writeVarLong(view.villageRevision); buf.writeUUID(view.official); buf.writeVarLong(view.anchorRevision);
            buf.writeBoolean(view.manageable); buf.writeBoolean(view.canFound); buf.writeVarInt(view.adults); buf.writeVarInt(view.beds);
            buf.writeBoolean(view.job); buf.writeUtf(view.name, 96); buf.writeVarInt(view.rows.size());
            for (Row row : view.rows) {
                buf.writeEnum(row.check); buf.writeBoolean(row.passed); buf.writeVarInt(row.found); buf.writeVarInt(row.required);
                buf.writeVarInt(row.targets.size()); for (BlockPos pos : row.targets) buf.writeBlockPos(pos);
            }
            buf.writeVarInt(view.villages.size()); for (Choice choice : view.villages) { buf.writeUUID(choice.id); buf.writeUtf(choice.name, 96); }
            buf.writeUtf(view.feedback, 128);
            buf.writeVarLong(view.marketLayout.revision()); writeStands(buf,view.marketLayout.assigned()); writeStands(buf,view.marketLayout.candidates());
            buf.writeVarInt(view.marketLayout.travel().size()); for(var trip:view.marketLayout.travel()) {
                buf.writeUUID(trip.person()); buf.writeUtf(trip.name(),48); buf.writeUtf(trip.home(),96); buf.writeUtf(trip.host(),96);
                buf.writeUtf(trip.phase(),12); buf.writeVarInt(trip.remaining()); buf.writeBoolean(trip.paused()); buf.writeBoolean(trip.late());
            }
            buf.writeVarLong(view.recognition.revision()); buf.writeVarInt(view.recognition.level()); buf.writeBoolean(view.recognition.readOnly());
            buf.writeVarInt(view.recognition.missing().size()); for(var key:view.recognition.missing()) buf.writeUtf(key,24);
            buf.writeVarInt(view.recognition.missingConversations().size()); for(var roleKey:view.recognition.missingConversations()) buf.writeUtf(roleKey,12);
            buf.writeVarInt(view.diplomacy.page()); buf.writeVarInt(view.diplomacy.pages()); buf.writeBoolean(view.diplomacy.readOnly());
            buf.writeVarInt(view.diplomacy.partners().size());
            for(var partner:view.diplomacy.partners()) {
                buf.writeUUID(partner.village()); buf.writeUtf(partner.name(),96); buf.writeVarLong(partner.revision());
                for(var deal:partner.deals()) { buf.writeVarLong(deal.revision()); buf.writeVarInt(deal.status()); buf.writeVarInt(deal.terms()); buf.writeBoolean(deal.incoming());
                    buf.writeUtf(deal.reaction(),12); buf.writeVarInt(deal.preferred()); buf.writeVarInt(deal.personality()); buf.writeUtf(deal.need(),32); }
                buf.writeUtf(partner.connection(),24); buf.writeBoolean(partner.meeting());
                var relation=partner.relation(); buf.writeUtf(relation.state(),16); buf.writeUtf(relation.trend(),16);
                buf.writeVarInt(relation.causes().size()); for(var cause:relation.causes()) buf.writeUtf(cause,16);
                buf.writeUtf(relation.repair(),16); buf.writeUtf(relation.need(),32); buf.writeUtf(relation.repairVillage(),96);
            }
              var crisis=view.diplomacy.crisis(); buf.writeUUID(crisis.id()); buf.writeVarLong(crisis.revision());
              buf.writeUtf(crisis.host(),96); buf.writeUtf(crisis.other(),96); buf.writeUtf(crisis.phase(),16); buf.writeUtf(crisis.need(),32);
              buf.writeVarInt(crisis.remaining()); buf.writeVarInt(crisis.heard()); buf.writeVarInt(crisis.personality()); buf.writeBoolean(crisis.competing()); buf.writeUtf(crisis.kind(),16);
            buf.writeBoolean(view.foundingObserved());
            buf.writeEnum(view.binding());
        }, buf -> {
            UUID session = buf.readUUID(); long revision = buf.readVarLong(); boolean open = buf.readBoolean(); BlockPos position = buf.readBlockPos();
            var role = buf.readEnum(BuildingScanner.Role.class); var range = buf.readEnum(BuildingScanner.Range.class);
            var status = buf.readEnum(BuildingScanner.Status.class); UUID village = buf.readUUID(); long villageRevision = buf.readVarLong();
            UUID official = buf.readUUID(); long anchorRevision = buf.readVarLong(); boolean manageable = buf.readBoolean(), canFound = buf.readBoolean();
            int adults = bounded(buf.readVarInt(), 4096), beds = bounded(buf.readVarInt(), 4096); boolean job = buf.readBoolean(); String name = buf.readUtf(96);
            int length = bounded(buf.readVarInt(), 13); List<Row> rows = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                var check = buf.readEnum(BuildingScanner.Check.class); boolean passed = buf.readBoolean();
                int found = bounded(buf.readVarInt(), 4096), required = bounded(buf.readVarInt(), 4096), count = bounded(buf.readVarInt(), 64);
                List<BlockPos> positions = new ArrayList<>(count); for (int j = 0; j < count; j++) positions.add(buf.readBlockPos());
                rows.add(new Row(check, passed, found, required, positions));
            }
            int count = bounded(buf.readVarInt(), 8); List<Choice> choices = new ArrayList<>(count);
            for (int i = 0; i < count; i++) choices.add(new Choice(buf.readUUID(), buf.readUtf(96)));
            String feedback=buf.readUtf(128); long placeRevision=buf.readVarLong();
            var stands=readStands(buf,5); var candidates=readStands(buf,32); int trips=bounded(buf.readVarInt(),5); var travel=new ArrayList<Travel>();
            for(int i=0;i<trips;i++) travel.add(new Travel(buf.readUUID(),buf.readUtf(48),buf.readUtf(96),buf.readUtf(96),buf.readUtf(12),buf.readVarInt(),buf.readBoolean(),buf.readBoolean()));
            var layout=new MarketLayout(placeRevision,stands,candidates,travel);
            long recognitionRevision=buf.readVarLong(); int level=bounded(buf.readVarInt(),5); boolean readOnly=buf.readBoolean();
            int missingCount=bounded(buf.readVarInt(),17); List<String> missing=new ArrayList<>();
            for(int i=0;i<missingCount;i++) missing.add(buf.readUtf(24));
            int voicesCount=bounded(buf.readVarInt(),3); List<String> voices=new ArrayList<>();
            for(int i=0;i<voicesCount;i++) voices.add(buf.readUtf(12));
            int page=bounded(buf.readVarInt(),31),pages=bounded(buf.readVarInt(),32); boolean diplomacyReadOnly=buf.readBoolean();
            int partnersCount=bounded(buf.readVarInt(),32); List<Partner> partners=new ArrayList<>();
            for(int i=0;i<partnersCount;i++) {
                UUID partner=buf.readUUID(); String partnerName=buf.readUtf(96); long partnerRevision=buf.readVarLong(); List<Deal> deals=new ArrayList<>();
                for(int j=0;j<3;j++) deals.add(new Deal(buf.readVarLong(),buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readUtf(12),buf.readVarInt(),buf.readVarInt(),buf.readUtf(32)));
                String connection=buf.readUtf(24); boolean meeting=buf.readBoolean();
                String relationState=buf.readUtf(16),trend=buf.readUtf(16); int causeCount=bounded(buf.readVarInt(),14); List<String> causes=new ArrayList<>();
                for(int j=0;j<causeCount;j++) causes.add(buf.readUtf(16));
                partners.add(new Partner(partner,partnerName,partnerRevision,deals,connection,meeting,new Relation(relationState,trend,causes,buf.readUtf(16),buf.readUtf(32),buf.readUtf(96))));
            }
            return new View(session, revision, open, position, role, range, status, village, villageRevision, official,
                    anchorRevision, manageable, canFound, adults, beds, job, name, rows, choices, feedback,layout,new Recognition(recognitionRevision,level,missing,readOnly,voices),
                    new Diplomacy(page,pages,partners,diplomacyReadOnly,new Crisis(buf.readUUID(),buf.readVarLong(),buf.readUtf(96),buf.readUtf(96),
                            buf.readUtf(16),buf.readUtf(32),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readUtf(16))),buf.readBoolean(),buf.readEnum(Binding.class));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record Action(UUID session, long revision, Kind kind, UUID target, String name, int index) implements CustomPacketPayload {
        public Action {
            Objects.requireNonNull(session); Objects.requireNonNull(kind); Objects.requireNonNull(target);
            if (revision < 0) throw new IllegalArgumentException("Invalid building action revision"); text(name, 96); bounded(index, 64);
        }
        public static final Type<Action> ID = new Type<>(Identifier.fromNamespaceAndPath("village-quest", "building_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buf, action) -> {
            buf.writeUUID(action.session); buf.writeVarLong(action.revision); buf.writeEnum(action.kind);
            buf.writeUUID(action.target); buf.writeUtf(action.name, 96); buf.writeVarInt(action.index);
        }, buf -> new Action(buf.readUUID(), buf.readVarLong(), buf.readEnum(Kind.class), buf.readUUID(), buf.readUtf(96), bounded(buf.readVarInt(), 64)));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    private static int bounded(int count, int maximum) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid building packet length"); return count;
    }
    private static void writeStands(RegistryFriendlyByteBuf buf,List<Stand> stands) {
        buf.writeVarInt(stands.size()); for(var stand:stands) {
            buf.writeVarInt(stand.slot()); buf.writeBlockPos(stand.position()); buf.writeBlockPos(stand.counter()); buf.writeBlockPos(stand.container()); buf.writeBoolean(stand.usable());
        }
    }
    private static List<Stand> readStands(RegistryFriendlyByteBuf buf,int maximum) {
        int count=bounded(buf.readVarInt(),maximum); var result=new ArrayList<Stand>(count);
        for(int i=0;i<count;i++) result.add(new Stand(buf.readVarInt(),buf.readBlockPos(),buf.readBlockPos(),buf.readBlockPos(),buf.readBoolean()));
        return List.copyOf(result);
    }
    private static void text(String text, int maximum) {
        if (text == null || text.length() > maximum) throw new IllegalArgumentException("Invalid building packet text");
    }
}
