package de.quest.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Preserve every future/invalid attachment shape; the coordinator rejects it instead of treating it as absent. */
public final class MarketPlayerProofCodec {
    private MarketPlayerProofCodec() {}
    public static final Codec<Tag> CODEC=Codec.PASSTHROUGH.xmap(
            value -> value.convert(NbtOps.INSTANCE).getValue().copy(),
            value -> new Dynamic<>(NbtOps.INSTANCE,value.copy()));
}
