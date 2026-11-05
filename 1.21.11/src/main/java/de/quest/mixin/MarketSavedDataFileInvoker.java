package de.quest.mixin;

import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DimensionDataStorage.class)
public interface MarketSavedDataFileInvoker {
    @Invoker("getDataFile") Path villageQuest$marketDataFile(String id);
}
