package de.quest.network;

import de.quest.VillageQuest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Per-player presentation request from /vq questtracker left or right. */
public record QuestTrackerPositionPayload(boolean right) implements CustomPacketPayload {
    public static final Type<QuestTrackerPositionPayload> ID=new Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,"quest_tracker_position"));
    public static final StreamCodec<RegistryFriendlyByteBuf,QuestTrackerPositionPayload> CODEC=StreamCodec.of(
            (buf,value)->buf.writeBoolean(value.right()),buf->new QuestTrackerPositionPayload(buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() {return ID;}
}
