package de.quest.content.story;

import net.minecraft.core.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class VillagerDialogueService {
    private VillagerDialogueService() {}

    public static void sendDialogue(ServerPlayer player, Entity speaker, Component line) {
        if (player == null || speaker == null || line == null || line.getString().isEmpty()) {
            return;
        }
        player.sendSystemMessage(
                speaker.getDisplayName().copy().withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(line.copy().withStyle(ChatFormatting.GRAY)),
                false
        );
    }

    public static Component marketRounds(Villager villager) {
        return pick(villager, 4, "message.village-quest.dialogue.market_rounds.");
    }

    public static Component marketRoadGather(Villager villager) {
        return pick(villager, 4, "message.village-quest.dialogue.market_road_gather.");
    }

    public static Component marketRoadProfession(Villager villager) {
        if (villager == null) {
            return Component.empty();
        }
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.TOOLSMITH)) {
            return Component.translatable("message.village-quest.dialogue.market_road_profession.toolsmith");
        }
        if (profession.is(VillagerProfession.WEAPONSMITH)) {
            return Component.translatable("message.village-quest.dialogue.market_road_profession.weaponsmith");
        }
        if (profession.is(VillagerProfession.FARMER)) {
            return Component.translatable("message.village-quest.dialogue.market_road_profession.farmer");
        }
        if (profession.is(VillagerProfession.FISHERMAN)) {
            return Component.translatable("message.village-quest.dialogue.market_road_profession.fisherman");
        }
        if (profession.is(VillagerProfession.SHEPHERD)) {
            return Component.translatable("message.village-quest.dialogue.market_road_profession.shepherd");
        }
        if (profession.is(VillagerProfession.LIBRARIAN)) {
            return Component.translatable("message.village-quest.dialogue.market_road_profession.librarian");
        }
        return Component.translatable("message.village-quest.dialogue.market_road_gather.1");
    }

    public static Component shadowsHomeRumor(Villager villager) {
        return pick(villager, 6, "message.village-quest.dialogue.shadows_home.");
    }

    public static Component shadowsRemoteRumor(Villager villager) {
        return pick(villager, 8, "message.village-quest.dialogue.shadows_remote.");
    }

    private static Component pick(Villager villager, int variants, String keyPrefix) {
        if (villager == null || variants <= 0 || keyPrefix == null || keyPrefix.isBlank()) {
            return Component.empty();
        }
        int variant = Math.floorMod(villager.getUUID().hashCode(), variants) + 1;
        return Component.translatable(keyPrefix + variant);
    }
}
