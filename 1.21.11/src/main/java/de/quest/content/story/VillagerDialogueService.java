package de.quest.content.story;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.VillagerProfession;

public final class VillagerDialogueService {
    private VillagerDialogueService() {}

    public static void sendDialogue(ServerPlayerEntity player, Entity speaker, Text line) {
        if (player == null || speaker == null || line == null || line.getString().isEmpty()) {
            return;
        }
        player.sendMessage(
                speaker.getDisplayName().copy().formatted(Formatting.GOLD)
                        .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
                        .append(line.copy().formatted(Formatting.GRAY)),
                false
        );
    }

    public static Text marketRounds(VillagerEntity villager) {
        return pick(villager, 4, "message.village-quest.dialogue.market_rounds.");
    }

    public static Text marketRoadGather(VillagerEntity villager) {
        return pick(villager, 4, "message.village-quest.dialogue.market_road_gather.");
    }

    public static Text marketRoadProfession(VillagerEntity villager) {
        if (villager == null) {
            return Text.empty();
        }
        RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.matchesKey(VillagerProfession.TOOLSMITH)) {
            return Text.translatable("message.village-quest.dialogue.market_road_profession.toolsmith");
        }
        if (profession.matchesKey(VillagerProfession.WEAPONSMITH)) {
            return Text.translatable("message.village-quest.dialogue.market_road_profession.weaponsmith");
        }
        if (profession.matchesKey(VillagerProfession.FARMER)) {
            return Text.translatable("message.village-quest.dialogue.market_road_profession.farmer");
        }
        if (profession.matchesKey(VillagerProfession.FISHERMAN)) {
            return Text.translatable("message.village-quest.dialogue.market_road_profession.fisherman");
        }
        if (profession.matchesKey(VillagerProfession.SHEPHERD)) {
            return Text.translatable("message.village-quest.dialogue.market_road_profession.shepherd");
        }
        if (profession.matchesKey(VillagerProfession.LIBRARIAN)) {
            return Text.translatable("message.village-quest.dialogue.market_road_profession.librarian");
        }
        return Text.translatable("message.village-quest.dialogue.market_road_gather.1");
    }

    public static Text shadowsHomeRumor(VillagerEntity villager) {
        return pick(villager, 6, "message.village-quest.dialogue.shadows_home.");
    }

    public static Text shadowsRemoteRumor(VillagerEntity villager) {
        return pick(villager, 8, "message.village-quest.dialogue.shadows_remote.");
    }

    private static Text pick(VillagerEntity villager, int variants, String keyPrefix) {
        if (villager == null || variants <= 0 || keyPrefix == null || keyPrefix.isBlank()) {
            return Text.empty();
        }
        int variant = Math.floorMod(villager.getUuid().hashCode(), variants) + 1;
        return Text.translatable(keyPrefix + variant);
    }
}
