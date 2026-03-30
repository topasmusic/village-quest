package de.quest.registry;

import de.quest.VillageQuest;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    public static SoundEvent SHULKER_IDLE6;

    private static Identifier id(String path) {
        return Identifier.of(VillageQuest.MOD_ID, path);
    }

    public static void register() {
        Identifier shulkerIdle6Id = id("shulker_idle6");
        SHULKER_IDLE6 = SoundEvent.of(shulkerIdle6Id);
        Registry.register(Registries.SOUND_EVENT, shulkerIdle6Id, SHULKER_IDLE6);
        VillageQuest.LOGGER.info("Registered sounds");
    }
}

