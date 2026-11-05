package de.quest.network;

import de.quest.VillageQuest;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class RewardPayloads {
    public record Choice(String id, List<Component> lines, long currency, List<ItemStack> items) {
        public Choice { lines = List.copyOf(lines); items = items.stream().map(ItemStack::copy).toList(); }
        public Choice(String id, List<Component> lines) { this(id, lines, 0, List.of()); }
    }
    public record View(UUID session, String key, List<Choice> choices, String preferred) implements CustomPacketPayload {
        public static final Type<View> ID = new Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "reward_choices"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUUID(value.session()); buf.writeUtf(value.key(), 128); buf.writeVarInt(value.choices().size());
            for (var choice : value.choices()) {
                buf.writeUtf(choice.id(), 32); buf.writeVarInt(choice.lines().size());
                for (var line : choice.lines()) ComponentSerialization.STREAM_CODEC.encode(buf, line);
                buf.writeVarLong(choice.currency()); buf.writeVarInt(choice.items().size());
                for (var item : choice.items()) ItemStack.STREAM_CODEC.encode(buf, item);
            }
            buf.writeUtf(value.preferred(), 32);
        }, buf -> {
            UUID session = buf.readUUID(); String key = buf.readUtf(128); int count = bounded(buf.readVarInt(), 3);
            List<Choice> choices = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf(32); int length = bounded(buf.readVarInt(), 16); List<Component> lines = new ArrayList<>();
                for (int j = 0; j < length; j++) lines.add(ComponentSerialization.STREAM_CODEC.decode(buf));
                long currency = buf.readVarLong(); if (currency < 0) throw new IllegalArgumentException("Negative reward currency");
                int itemCount = bounded(buf.readVarInt(), 256); List<ItemStack> items = new ArrayList<>();
                for (int j = 0; j < itemCount; j++) items.add(ItemStack.STREAM_CODEC.decode(buf));
                choices.add(new Choice(id, List.copyOf(lines), currency, List.copyOf(items)));
            }
            return new View(session, key, List.copyOf(choices), buf.readUtf(32));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record Action(UUID session, String option) implements CustomPacketPayload {
        public static final Type<Action> ID = new Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "reward_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.session()); buf.writeUtf(value.option(), 32); },
                buf -> new Action(buf.readUUID(), buf.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    private static int bounded(int count, int max) { if (count < 0 || count > max) throw new IllegalArgumentException("Reward packet length"); return count; }
    private RewardPayloads() { }
}
