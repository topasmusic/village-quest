package de.quest.registry;

import de.quest.VillageQuest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static SoundEvent SHULKER_IDLE6;

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, path);
    }

    public static void register() {
        Identifier shulkerIdle6Id = id("shulker_idle6");
        SHULKER_IDLE6 = SoundEvent.createVariableRangeEvent(shulkerIdle6Id);
        Registry.register(BuiltInRegistries.SOUND_EVENT, shulkerIdle6Id, SHULKER_IDLE6);
        VillageQuest.LOGGER.info("Registered sounds");
    }
}

