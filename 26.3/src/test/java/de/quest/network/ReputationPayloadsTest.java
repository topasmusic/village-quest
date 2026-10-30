package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.*;
import de.quest.village.VillageLifeState.VillageKey;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

final class ReputationPayloadsTest {
    @Test void realLogisticsConditionsRoundTripAndInvalidActionsAreRejected() {
        var view=ReputationViewService.build(new SocialReputationData(),List.of(),0,true);
        var locked=new ReputationPayloads.ServiceView(0,"screen.village-quest.reputation.desk.logistics.routes",List.of(net.minecraft.network.chat.Component.literal("Connect two active villages")));
        var ready=new ReputationPayloads.ServiceView(2,"screen.village-quest.reputation.desk.logistics.ready",List.of(net.minecraft.network.chat.Component.literal("Confirm departure")));
        var payload=new ReputationPayloads.InteractionPayload(UUID.randomUUID(),false,false,view,List.of(),"",locked,ready);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
        try { ReputationPayloads.InteractionPayload.CODEC.encode(buf,payload); assertEquals(payload,ReputationPayloads.InteractionPayload.CODEC.decode(buf)); assertEquals(0,buf.readableBytes()); }
        finally { buf.release(); }
        assertThrows(IllegalArgumentException.class,()->new ReputationPayloads.ServiceView(5,"bad",List.of()));
        assertThrows(IllegalArgumentException.class,()->new ReputationPayloads.ServiceView(0,"bad",java.util.Collections.nCopies(9,net.minecraft.network.chat.Component.empty())));
    }
    static RegistryAccess registries;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
    @Test void serverRejectsOldOrNewVersionEvenWhenEveryOtherPacketIsSupported() {
        var player=org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        org.mockito.Mockito.when(player.getUUID()).thenReturn(UUID.randomUUID());
        player.connection=org.mockito.Mockito.mock(net.minecraft.server.network.ServerGamePacketListenerImpl.class);
        try(var networkMock=org.mockito.Mockito.mockStatic(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.class)) {
            networkMock.when(()->net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(org.mockito.ArgumentMatchers.eq(player),org.mockito.ArgumentMatchers.any(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type.class))).thenReturn(true);
            assertTrue(ProgressionProtocol.require(player));
            networkMock.when(()->net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,ExactVersionPayload.ID)).thenReturn(false);
            assertFalse(ProgressionProtocol.require(player));
            org.mockito.Mockito.verify(player.connection).disconnect(org.mockito.ArgumentMatchers.any(net.minecraft.network.chat.Component.class));
        } finally { ProgressionProtocol.disconnected(player.getUUID()); }
    }
    @Test void exactVersionsAndEveryNewCapabilityAreRequired() {
        assertNotEquals(ExactVersionPayload.capability("2.6.3-unreleased.9"),ExactVersionPayload.capability("2.6.3-unreleased.10"));
        assertNotEquals(ExactVersionPayload.capability("2.6.3"),ExactVersionPayload.capability("2.6.3-unreleased.10"));
        assertNotEquals(ExactVersionPayload.capability("A+B"),ExactVersionPayload.capability("a_b"));
        assertEquals(ExactVersionPayload.capability("2.6.3"),ExactVersionPayload.capability("2.6.3"));
        assertTrue(ProgressionProtocol.compatible(true,true,true,true,true,true));
        for(int i=0;i<6;i++) { boolean[] b={true,true,true,true,true,true};b[i]=false;
            assertFalse(ProgressionProtocol.compatible(b[0],b[1],b[2],b[3],b[4],b[5])); }
    }
    @Test void relationshipHintsRoundTripAndRejectUnknownConflictBeforePresentation() {
        var hints=new VillageRelationship.Hints(false,true,2,net.minecraft.network.chat.Component.literal("Next bond"),net.minecraft.network.chat.Component.literal("Repair"));
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
        VillageRelationship.write(buf,hints);assertEquals(hints,VillageRelationship.read(buf));assertEquals(0,buf.readableBytes());buf.release();
        assertThrows(IllegalArgumentException.class,()->new VillageRelationship.Hints(true,true,3,net.minecraft.network.chat.Component.empty(),net.minecraft.network.chat.Component.empty()));
    }
    @Test void maximumPersonalViewRoundTripsBelowSixteenKiB() {
        var data = new SocialReputationData();
        List<VillageKey> villages = new ArrayList<>();
        for (int i = 0; i < 8; i++) villages.add(new VillageKey("minecraft:overworld", i, -i));
        for (int i = 0; i < 32; i++) data.appendAdministrativeHistory(UUID.randomUUID(), -1, villages.get(i % 8), -4, i);
        data.openCase(UUID.randomUUID(), SocialReputationRules.Offence.CREW_KILL, villages.getFirst(), 32);
        var original = new ReputationPayloads.ViewPayload(ReputationViewService.build(data, villages, 0, true));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ReputationPayloads.ViewPayload.CODEC.encode(buf, original);
        assertTrue(buf.readableBytes() < 16_384);
        assertEquals(original, ReputationPayloads.ViewPayload.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }
    @Test void unsupportedSchemaIsRejectedBeforeReadingOrAllocatingCollections() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        buf.writeVarInt(99);
        assertThrows(IllegalArgumentException.class, () -> ReputationPayloads.ViewPayload.CODEC.decode(buf));
    }
    @Test void blockedTrustReasonSurvivesTheNetworkRoundTrip() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        var original = new ReputationPayloads.OpenFeedbackPayload("reputation.village-quest.open.unavailable");
        ReputationPayloads.OpenFeedbackPayload.CODEC.encode(buf, original);
        assertEquals(original, ReputationPayloads.OpenFeedbackPayload.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
        buf.release();
    }
    @Test void maliciousCollectionLengthIsRejectedInsteadOfClampedOrLeftUnread() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        assertThrows(IllegalArgumentException.class, () -> ReputationPayloads.checkedCount(9, 8));
        assertThrows(IllegalArgumentException.class, () -> ReputationPayloads.checkedCount(-1, 8));
        assertEquals(8, ReputationPayloads.checkedCount(8, 8));
    }
    @Test void villageTrustTravelsIndependentlyOfBondAndSupply() {
        var original = new Payloads.NetworkVillageData(2, net.minecraft.network.chat.Component.literal("Forge"),
                net.minecraft.network.chat.Component.literal("Established"), net.minecraft.network.chat.Component.literal("Thriving"),
                "thriving", net.minecraft.network.chat.Component.literal("Grain"), 91, 3, -70, true);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        Payloads.NetworkVillageData.CODEC.encode(buf, original);
        assertEquals(original, Payloads.NetworkVillageData.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }
    @Test void invalidCaseCauseIsRejectedBeforeClientEnumIndexing() {
        var data = new SocialReputationData(); data.openCase(UUID.randomUUID(), SocialReputationRules.Offence.CREW_KILL, null, 0);
        var view = ReputationViewService.build(data, List.of(), 0, true);
        var active = view.reparation();
        var broken = new ReputationViewService.CaseView(active.id(), active.major(), active.affectedCount(), active.ticksRemaining(), active.selected(), 99, active.aid());
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ReputationPayloads.ViewPayload.CODEC.encode(buf, new ReputationPayloads.ViewPayload(new ReputationViewService.View(
                view.schema(), view.revision(), view.enabled(), view.writable(), view.guildTrust(), view.probation(), view.buyLimit(),
                view.dispatchAllowed(), view.convoyAllowed(), view.rewardPercent(), view.villagePage(), view.villageTotal(), view.villages(), view.history(), broken)));
        assertThrows(IllegalArgumentException.class, () -> ReputationPayloads.ViewPayload.CODEC.decode(buf));
    }
}
