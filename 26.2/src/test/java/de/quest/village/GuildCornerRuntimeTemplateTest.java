package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.DataFixTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the data fixing used by Minecraft's structure template manager. */
final class GuildCornerRuntimeTemplateTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @ValueSource(strings = {"generic", "desert"})
    void bundledPresetRemainsValidAfterMinecraftDataFixesIt(String style) throws Exception {
        String resource = "/data/village-quest/structure/guild_corner/" + style + ".nbt";
        CompoundTag authored;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            authored = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        }
        CompoundTag updated = DataFixTypes.STRUCTURE.updateToCurrentVersion(
                DataFixers.getDataFixer(), authored, NbtUtils.getDataVersion(authored));
        assertTrue(GuildCornerTemplateContract.isValid(updated),
                style + " must pass the runtime safety contract after Minecraft data fixing");
    }
}
