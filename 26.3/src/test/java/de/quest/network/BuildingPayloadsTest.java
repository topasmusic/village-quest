package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.kingdom.BuildingScanner;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class BuildingPayloadsTest {
    @Test void savedHereChangedAndElsewhereAreDistinctAuthoritativeSnapshots() {
        for(var binding:BuildingPayloads.Binding.values()) {
            var view=new BuildingPayloads.View(UUID.randomUUID(),1,true,BlockPos.ZERO,BuildingScanner.Role.INN,BuildingScanner.Range.BASIC,
                    BuildingScanner.Status.INVALID,UUID.randomUUID(),2,UUID.randomUUID(),3,true,false,0,0,false,"Home",List.of(),List.of(),"",
                    BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,BuildingPayloads.Diplomacy.EMPTY,false,binding);
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try { BuildingPayloads.View.CODEC.encode(buf,view);assertEquals(view,BuildingPayloads.View.CODEC.decode(buf));assertEquals(0,buf.readableBytes()); }
            finally {buf.release();}
        }
    }
    @Test void unknownFoundingEvidenceRoundTripsWithoutBecomingZeroPopulationEvidence() {
        var view=new BuildingPayloads.View(UUID.randomUUID(),0,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.BASIC,BuildingScanner.Status.UNKNOWN,BuildingPayloads.NONE,0,BuildingPayloads.NONE,0,false,false,
                0,0,false,"",List.of(),List.of(),"",BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,BuildingPayloads.Diplomacy.EMPTY,false);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { BuildingPayloads.View.CODEC.encode(buf,view); var copy=BuildingPayloads.View.CODEC.decode(buf); assertEquals(view,copy); assertFalse(copy.foundingObserved()); assertEquals(0,buf.readableBytes()); }
        finally { buf.release(); }
    }
    @Test void relationshipPacketCannotCarryInventedOrUnboundedCausesOrUnrelatedNeed() {
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Relation("friendly","steady",List.of("telepathy"),"none","none"));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Relation("friendly","steady",java.util.Collections.nCopies(11,"aid"),"none","none"));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Relation("strained","strained",List.of("attack"),"attack_freight","none"));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Relation("strained","strained",List.of("attack"),"attack_apology","granary_pantry"));
    }
    @Test void autonomousHistoryAndMediationCausesRoundTripThroughPrivateCouncilPacket() {
        var relation=new BuildingPayloads.Relation("trading","improving",List.of("delivery","reliable","autonomous","initiative_bond"),"none","none");
        var partner=new BuildingPayloads.Partner(UUID.randomUUID(),"Río de los Sauces",0,
                List.of(new BuildingPayloads.Deal(0,2,0,false),new BuildingPayloads.Deal(-1,-1,0,false),new BuildingPayloads.Deal(-1,-1,0,false)),"active",false,relation);
        var view=new BuildingPayloads.View(UUID.randomUUID(),4,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.BASIC,BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,true,false,
                0,0,false,"Mühlengrund",List.of(),List.of(),"",BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,
                new BuildingPayloads.Diplomacy(0,1,List.of(partner),true));
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { BuildingPayloads.View.CODEC.encode(buf,view); assertEquals(view,BuildingPayloads.View.CODEC.decode(buf)); }
        finally { buf.release(); }
    }
    @Test void checklistRoundTripsStableIdentitiesPositionsAndPrivateSessionRevision() {
        var view = new BuildingPayloads.View(UUID.randomUUID(), 4, true, new BlockPos(10, -20, 11),
                BuildingScanner.Role.INN, BuildingScanner.Range.EXTENDED, BuildingScanner.Status.VALID,
                UUID.randomUUID(), 7, UUID.randomUUID(), 8, true, false, 3, 3, true, "Talheim",
                List.of(new BuildingPayloads.Row(BuildingScanner.Check.BARRELS, true, 2, 2, List.of(new BlockPos(11, -20, 11)))),
                List.of(new BuildingPayloads.Choice(UUID.randomUUID(), "Nearbrook")), "");
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { BuildingPayloads.View.CODEC.encode(buf, view); assertEquals(view, BuildingPayloads.View.CODEC.decode(buf)); }
        finally { buf.release(); }
    }
    @Test void actionIsBoundToOneSessionRevisionAndBoundedNameAndChoice() {
        var action = new BuildingPayloads.Action(UUID.randomUUID(), 2, BuildingPayloads.Kind.RENAME, UUID.randomUUID(), "Oakbridge", 0);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { BuildingPayloads.Action.CODEC.encode(buf, action); assertEquals(action, BuildingPayloads.Action.CODEC.decode(buf)); }
        finally { buf.release(); }
        assertThrows(IllegalArgumentException.class, () -> new BuildingPayloads.Action(UUID.randomUUID(), -1,
                BuildingPayloads.Kind.FOUND, BuildingPayloads.NONE, "", 0));
    }
    @Test void oversizedRowsAndHighlightRequestsAreRejectedBeforeClientAllocation() {
        assertThrows(IllegalArgumentException.class, () -> new BuildingPayloads.Row(BuildingScanner.Check.BED, true, 1, 1,
                java.util.Collections.nCopies(65, BlockPos.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new BuildingPayloads.Choice(UUID.randomUUID(), "x".repeat(97)));
    }
    @Test void marketChildPlacesRoundTripAndRejectOversizedOrDuplicateSlots() {
        var stand=new BuildingPayloads.Stand(2,new BlockPos(0,64,1),new BlockPos(0,64,2),new BlockPos(0,65,2),true);
        var trip=new BuildingPayloads.Travel(UUID.randomUUID(),"Mira","Forge home","Market town","outbound",600,true,false);
        var layout=new BuildingPayloads.MarketLayout(7,List.of(stand),List.of(stand),List.of(trip));
        var base=new BuildingPayloads.View(UUID.randomUUID(),4,true,BlockPos.ZERO,BuildingScanner.Role.MARKET,
                BuildingScanner.Range.BASIC,BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,true,false,
                0,0,false,"Market town",List.of(),List.of(),"",layout);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { BuildingPayloads.View.CODEC.encode(buf,base); assertEquals(base,BuildingPayloads.View.CODEC.decode(buf)); }
        finally { buf.release(); }
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.MarketLayout(0,List.of(stand,stand),List.of()));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.MarketLayout(0,List.of(),java.util.Collections.nCopies(33,stand)));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Stand(5,BlockPos.ZERO,BlockPos.ZERO,BlockPos.ZERO,false));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Travel(UUID.randomUUID(),"Mira","A","B","teleport",0,false,false));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.MarketLayout(0,List.of(),List.of(),java.util.Collections.nCopies(6,trip)));
    }
    @Test void recognitionSnapshotRoundTripsAndCannotAllocateUnboundedRequirements() {
        var progress=new BuildingPayloads.Recognition(2,2,List.of("route_agreement","meeting"),false,List.of("innkeeper"));
        var view=new BuildingPayloads.View(UUID.randomUUID(),4,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.BASIC,BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,true,false,
                0,0,false,"Home",List.of(),List.of(),"",BuildingPayloads.MarketLayout.EMPTY,progress);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { BuildingPayloads.View.CODEC.encode(buf,view); assertEquals(view,BuildingPayloads.View.CODEC.decode(buf)); }
        finally { buf.release(); }
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Recognition(0,6,List.of(),false));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Recognition(0,1,java.util.Collections.nCopies(18,"meeting"),false));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Recognition(0,1,List.of("untrusted.key"),false));
    }
    @Test void diplomacySnapshotRoundTripsAndBoundsPartnersAndTerms() {
        var deal=new BuildingPayloads.Deal(4,0,1,true,"delivery",1,3,"granary_pantry");
        var relation=new BuildingPayloads.Relation("strained","rebuilding",List.of("delivery","loyalty","reconciliation","attack"),"attack_freight","granary_pantry","Home");
        var partner=new BuildingPayloads.Partner(UUID.randomUUID(),"Other",2,List.of(deal,deal,deal),"anchor_moved",true,relation);
        var crisis=new BuildingPayloads.Crisis(UUID.randomUUID(),9,"Home","Partner","reconciling","granary_pantry",24000,3,2,true,"loyalty");
        var diplomacy=new BuildingPayloads.Diplomacy(1,2,List.of(partner),false,crisis);
        var view=new BuildingPayloads.View(UUID.randomUUID(),4,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.BASIC,BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,true,false,
                0,0,false,"Home",List.of(),List.of(),"",BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,diplomacy);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { BuildingPayloads.View.CODEC.encode(buf,view); assertEquals(view,BuildingPayloads.View.CODEC.decode(buf)); }
        finally { buf.release(); }
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Diplomacy(0,1,java.util.Collections.nCopies(33,partner),false));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Deal(0,4,0,true));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Deal(0,0,0,true,"invented",0,0,"none"));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Deal(0,0,0,true,"ready",0,4,"none"));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Deal(0,0,0,true,"delivery",0,0,"diamond_tribute"));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Partner(UUID.randomUUID(),"Other",0,List.of()));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Crisis(UUID.randomUUID(),0,"Home","Other","open","none",48001,0,0,false));
        assertThrows(IllegalArgumentException.class,() -> new BuildingPayloads.Crisis(UUID.randomUUID(),0,"Home","Other","open","forged_need",100,0,0,false));
    }
}
