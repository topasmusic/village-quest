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
    static RegistryAccess registries;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
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
