package de.quest.mixin;

import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SavedDataStorage.class)
public interface MarketSavedDataFileInvoker {
    @Invoker("getDataFile") Path villageQuest$marketDataFile(Identifier id);
}
