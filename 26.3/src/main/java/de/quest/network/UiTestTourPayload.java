package de.quest.network;

import de.quest.VillageQuest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authorized start signal; all sample data remains client-local. */
public record UiTestTourPayload() implements CustomPacketPayload {
    public static final Type<UiTestTourPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "ui_test_tour"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UiTestTourPayload> CODEC = StreamCodec.of(
            (buf, value) -> {}, buf -> new UiTestTourPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
