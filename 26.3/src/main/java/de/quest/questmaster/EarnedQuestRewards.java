package de.quest.questmaster;

import de.quest.network.Payloads;
import de.quest.reward.VanillaRewardService;
import de.quest.quest.daily.*;
import de.quest.quest.weekly.*;
import de.quest.quest.story.*;
import de.quest.pilgrim.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;

/** Durable completed-quest entries: the choice remains visible after Later, reset or a new quest. */
final class EarnedQuestRewards {
    static final String PREFIX = "bequest:";
    static boolean owns(ServerLevel world, ServerPlayer player, String entryId) {
        if (entryId == null || !entryId.startsWith(PREFIX)) return false;
        String instance = entryId.substring(PREFIX.length());
        return VanillaRewardService.state(world,player.getUUID()).earned().stream()
                .anyMatch(raw -> ((CompoundTag)raw).getStringOr("instance", "").equals(instance));
    }
    static List<Payloads.QuestMasterEntryData> entries(ServerLevel world, ServerPlayer player) {
        var result = new ArrayList<Payloads.QuestMasterEntryData>(); var state = VanillaRewardService.state(world,player.getUUID());
        var counts = new LinkedHashMap<String,Integer>();
        for (var raw : state.earned()) { var offer=(CompoundTag)raw; String slot=offer.getStringOr("slot", "");
            if (!slot.equals("admin_reward_test")) counts.merge(category(slot),1,Integer::sum); }
        var shown = new HashSet<String>();
        for (var raw : state.earned()) {
            var offer = (CompoundTag)raw; String slot = offer.getStringOr("slot", ""), key = offer.getStringOr("key", "");
            if (slot.equals("admin_reward_test")) continue;
            String category = category(slot);
            if (!shown.add(category)) continue;
            int count = counts.get(category);
            var label = Component.translatable("reward.village-quest.bequests");
            result.add(new Payloads.QuestMasterEntryData(PREFIX + offer.getStringOr("instance", ""),category,
                    count > 1 ? label : title(offer),label,Component.translatable("reward.village-quest.ready_count", count),false,Component.empty(),
                    List.of(),List.of(),List.of(Component.translatable("reward.village-quest.collect_later")),
                    Payloads.QuestMasterActionPayload.ACTION_CLAIM,Component.translatable("reward.village-quest.collect"),state.writable(),
                    Payloads.QuestMasterActionPayload.ACTION_NONE,Component.empty(),false,false));
        }
        return List.copyOf(result);
    }
    private static String category(String slot) { return switch(slot) { case "daily", "bonus" -> "daily"; case "weekly" -> "weekly"; case "story" -> "story"; default -> "special"; }; }
    private static Component title(CompoundTag offer) {
        String key = offer.getStringOr("key", "");
        if (key.equals("friends:lutte")) return de.quest.quest.special.FriendsQuestService.tr("lutte.3.title");
        if (key.equals("concord")) return Component.translatable("reward.village-quest.source_concord");
        try {
            if (key.equals("intro") || key.startsWith("daily:")) {
                String type = key.equals("intro") ? offer.getStringOr("identity", "").split(":")[0] : key.substring(6);
                var definition = DailyQuestGenerator.definition(DailyQuestService.DailyQuestType.valueOf(type));
                if (definition != null) return definition.title();
            } else if (key.startsWith("weekly:")) {
                var definition = WeeklyQuestGenerator.definition(WeeklyQuestService.WeeklyQuestType.valueOf(key.substring(7)));
                if (definition != null) return definition.title();
            } else if (key.startsWith("story:")) return StoryQuestService.definition(StoryArcType.valueOf(key.substring(6).toUpperCase(Locale.ROOT))).title();
            else if (key.startsWith("pilgrim:")) return PilgrimContractService.title(PilgrimContractType.valueOf(key.substring(8).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknownSavedQuest) { /* Preserve collection for historical keys. */ }
        return Component.translatable("reward.village-quest.bequests");
    }
    private EarnedQuestRewards() { }
}
