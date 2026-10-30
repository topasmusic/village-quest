package de.quest.network;

import de.quest.VillageQuest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Advertised in both directions. The full installed mod version is encoded without normalization. */
public record ExactVersionPayload() implements CustomPacketPayload {
    public static String version() {
        return FabricLoader.getInstance().getModContainer(VillageQuest.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("development");
    }
    public static Identifier capability(String version) {
        return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,
                "exact_version/" + HexFormat.of().formatHex(version.getBytes(StandardCharsets.UTF_8)));
    }
    public static final Type<ExactVersionPayload> ID = new Type<>(capability(version()));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExactVersionPayload> CODEC = StreamCodec.unit(new ExactVersionPayload());
    public static net.minecraft.network.chat.Component mismatch() {
        return net.minecraft.network.chat.Component.translatableWithFallback("message.village-quest.exact_version",
                "Village Quest: this installation uses %s. Client and server must use exactly the same Village Quest version, including the Unreleased number.",version());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
